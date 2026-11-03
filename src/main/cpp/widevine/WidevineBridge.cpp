// Bridges a Widevine CDM (the .so a browser already downloaded) to KittyTune's JVM side.
//
// Playback of SoundCloud's `ctr-encrypted-hls` needs a CDM: the component that signs the license
// request, holds the content keys and decrypts the samples. The JVM has none, so this bridge drives
// the one present on the machine through the public CDM API, and nothing more.
//
// Two things matter for correctness:
//  * The CDM calls back into the host *while holding its own lock*, so anything that re-enters the
//    CDM (TimerExpired) must be dispatched on another thread or it deadlocks.
//  * The per-sample IV size comes from the init segment's `tenc` box, not from a constant:
//    SoundCloud uses 16 bytes where FFmpeg-produced content uses 8, and guessing wrong yields
//    "decryption succeeded" with noise.
//
// The content key never crosses this boundary: `ktwv_decrypt` hands ciphertext in and gets plaintext
// back, exactly like a browser's EME stack.
#include "content_decryption_module.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <memory>
#include <mutex>
#include <random>
#include <set>
#include <string>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <direct.h>
#else
#include <sys/stat.h>
#endif

using namespace cdm;

namespace {

// ---------------------------------------------------------------------------------------------
// Host side
// ---------------------------------------------------------------------------------------------

class BridgeBuffer : public Buffer {
 public:
  explicit BridgeBuffer(uint32_t capacity) : data_(capacity) {}
  void Destroy() override { delete this; }
  uint32_t Capacity() const override { return static_cast<uint32_t>(data_.size()); }
  uint8_t* Data() override { return data_.data(); }
  void SetSize(uint32_t size) override { data_.resize(size); }
  uint32_t Size() const override { return static_cast<uint32_t>(data_.size()); }

 private:
  std::vector<uint8_t> data_;
};

struct Bridge;
class BridgeHost;

struct Bridge {
  ContentDecryptionModule_10* cdm = nullptr;
  std::string session_id;

  // The host is per instance, not global: a second CDM instance (a download running alongside
  // playback) gets its own, so its callbacks cannot land in the other instance's state.
  BridgeHost* host = nullptr;
  std::mutex host_mutex;

  std::mutex mutex;
  std::condition_variable cv;

  bool initialized = false;
  bool initialized_ok = false;
  bool license_requested = false;
  bool keys_changed = false;
  bool keys_usable = false;
  bool rejected = false;
  // SetServerCertificate is a promise like any other, but it happens before a session exists.
  bool promise_settled = false;
  bool promise_ok = false;
  std::string last_error;
  std::vector<uint8_t> license_request;
  std::vector<uint8_t> last_key_id;

  ~Bridge();

  void notify() { cv.notify_all(); }
};

// ---------------------------------------------------------------------------------------------
// Storage
// ---------------------------------------------------------------------------------------------
//
// A CDM keeps the state it is allowed to keep ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the certificate it was provisioned with, the
// licenses it may cache ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â through FileIO and the storage id. With neither, the only identity it has
// is the default certificate baked into its binary, which is the unprovisioned client a license
// server is free to refuse. The directory mirrors what a browser profile provides.

std::string storage_directory() {
  const char* configured = getenv("KITTYTUNE_WIDEVINE_STORAGE");
  if (configured && *configured) return configured;
  const char* base = getenv("TMPDIR");
  if (!base || !*base) base = getenv("TEMP");
  if (!base || !*base) base = "/tmp";
#ifdef _WIN32
  return std::string(base) + "\\kittytune_cdm";
#else
  return std::string(base) + "/kittytune_cdm";
#endif
}

void ensure_storage_directory() {
#ifdef _WIN32
  _mkdir(storage_directory().c_str());
#else
  mkdir(storage_directory().c_str(), 0700);
#endif
}

// The id the CDM keys its stored state on, stable across runs so a certificate it saved is found
// again. Generated once, then kept beside the files it belongs to.
std::vector<uint8_t> storage_id() {
  ensure_storage_directory();
  std::string path = storage_directory();
#ifdef _WIN32
  path += "\\storage-id.bin";
#else
  path += "/storage-id.bin";
#endif
  std::vector<uint8_t> id(32);
  if (FILE* file = fopen(path.c_str(), "rb")) {
    const size_t read = fread(id.data(), 1, id.size(), file);
    fclose(file);
    if (read == id.size()) return id;
  }
  std::random_device random;
  for (auto& byte : id) byte = static_cast<uint8_t>(random() & 0xFF);
  if (FILE* file = fopen(path.c_str(), "wb")) {
    fwrite(id.data(), 1, id.size(), file);
    fclose(file);
  }
  return id;
}

// The CDM is explicit that FileIO answers arrive asynchronously: answering from inside Open() or
// Read() makes it drop the state it asked us to keep. One worker thread is enough Ã¢â‚¬â€ this is a
// certificate and a handful of counters, not media.
class IoWorker {
 public:
  static IoWorker& instance() {
    static IoWorker worker;
    return worker;
  }

  void post(std::function<void()> task) {
    {
      std::lock_guard<std::mutex> lock(mutex_);
      tasks_.push_back(std::move(task));
    }
    cv_.notify_one();
  }

 private:
  // Detached: the worker lives as long as the process, and a joinable static would abort at exit.
  IoWorker() { std::thread([this] { run(); }).detach(); }

  void run() {
    for (;;) {
      std::function<void()> task;
      {
        std::unique_lock<std::mutex> lock(mutex_);
        cv_.wait(lock, [this] { return !tasks_.empty(); });
        task = std::move(tasks_.front());
        tasks_.erase(tasks_.begin());
      }
      task();
    }
  }

  std::mutex mutex_;
  std::condition_variable cv_;
  std::vector<std::function<void()>> tasks_;
  std::thread thread_;
};

// One storage file, shared with the worker so a callback can still be answered after Close().
struct StorageFile {
  FileIOClient* client = nullptr;
  std::string directory;
  std::string name;
  FILE* file = nullptr;
  bool closed = false;
  std::mutex mutex;
};

class StorageFileIO : public FileIO {
 public:
  explicit StorageFileIO(FileIOClient* client) {
    state_->client = client;
    state_->directory = storage_directory();
  }

  void Open(const char* file_name, uint32_t file_name_size) override {
    auto state = state_;
    state->name.assign(file_name ? file_name : "", file_name ? file_name_size : 0);
    IoWorker::instance().post([state] {
      ensure_storage_directory();
#ifdef _WIN32
      std::string path = state->directory + "\\" + state->name;
#else
      std::string path = state->directory + "/" + state->name;
#endif
      std::lock_guard<std::mutex> lock(state->mutex);
      if (state->closed) return;
      state->file = fopen(path.c_str(), "r+b");
      if (!state->file) state->file = fopen(path.c_str(), "wb+");
      if (state->client) {
        state->client->OnOpenComplete(state->file ? FileIOClient::Status::kSuccess
                                                  : FileIOClient::Status::kError);
      }
    });
  }

  void Read() override {
    auto state = state_;
    IoWorker::instance().post([state] {
      std::lock_guard<std::mutex> lock(state->mutex);
      if (state->closed || !state->file || !state->client) return;
      fseek(state->file, 0, SEEK_END);
      const long size = ftell(state->file);
      fseek(state->file, 0, SEEK_SET);
      std::vector<uint8_t> data(size > 0 ? static_cast<size_t>(size) : 0);
      const bool ok =
          size <= 0 || fread(data.data(), 1, data.size(), state->file) == data.size();
      if (!ok) {
        state->client->OnReadComplete(FileIOClient::Status::kError, nullptr, 0);
        return;
      }
      state->client->OnReadComplete(FileIOClient::Status::kSuccess,
                                    data.empty() ? nullptr : data.data(),
                                    static_cast<uint32_t>(data.size()));
    });
  }

  void Write(const uint8_t* data, uint32_t data_size) override {
    auto state = state_;
    std::vector<uint8_t> copy(data, data + data_size);
    IoWorker::instance().post([state, copy] {
      std::lock_guard<std::mutex> lock(state->mutex);
      if (state->closed || !state->file || !state->client) return;
      fseek(state->file, 0, SEEK_SET);
      const bool ok = copy.empty() || fwrite(copy.data(), 1, copy.size(), state->file) == copy.size();
      if (ok) fflush(state->file);
      state->client->OnWriteComplete(ok ? FileIOClient::Status::kSuccess
                                        : FileIOClient::Status::kError);
    });
  }

  void Close() override {
    auto state = state_;
    IoWorker::instance().post([state] {
      std::lock_guard<std::mutex> lock(state->mutex);
      state->closed = true;
      if (state->file) {
        fclose(state->file);
        state->file = nullptr;
      }
    });
    delete this;
  }

 private:
  std::shared_ptr<StorageFile> state_ = std::make_shared<StorageFile>();
};

class BridgeHost : public Host_10 {
 public:
  explicit BridgeHost(Bridge* bridge) : bridge_(bridge) {}

  Buffer* Allocate(uint32_t capacity) override { return new BridgeBuffer(capacity); }

  // The CDM calls this while holding its lock; re-entering it on this thread would deadlock.
  void SetTimer(int64_t delay_ms, void* context) override {
    if (!context) return;
    ContentDecryptionModule_10* cdm = bridge_->cdm;
    if (!cdm) return;
    std::thread([cdm, delay_ms, context]() {
      std::this_thread::sleep_for(std::chrono::milliseconds(delay_ms > 0 ? delay_ms : 0));
      cdm->TimerExpired(context);
    }).detach();
  }

  Time GetCurrentWallTime() override {
    using namespace std::chrono;
    return duration<double>(system_clock::now().time_since_epoch()).count();
  }

  void OnInitialized(bool success) override {
    std::lock_guard<std::mutex> lock(bridge_->mutex);
    bridge_->initialized = true;
    bridge_->initialized_ok = success;
    bridge_->notify();
  }

  void OnResolveKeyStatusPromise(uint32_t, KeyStatus) override {}

  void OnResolveNewSessionPromise(uint32_t, const char* session_id,
                                  uint32_t session_id_size) override {
    std::lock_guard<std::mutex> lock(bridge_->mutex);
    bridge_->session_id.assign(session_id ? session_id : "", session_id ? session_id_size : 0);
    bridge_->notify();
  }

  void OnResolvePromise(uint32_t) override {
    std::lock_guard<std::mutex> lock(bridge_->mutex);
    bridge_->promise_settled = true;
    bridge_->promise_ok = true;
    bridge_->notify();
  }

  void OnRejectPromise(uint32_t, Exception exception, uint32_t system_code,
                       const char* error_message, uint32_t error_message_size) override {
    std::lock_guard<std::mutex> lock(bridge_->mutex);
    bridge_->promise_settled = true;
    bridge_->promise_ok = false;
    bridge_->rejected = true;
    bridge_->last_error.assign(error_message ? error_message : "", error_message_size);
    bridge_->notify();
  }

  void OnSessionMessage(const char* session_id, uint32_t session_id_size, MessageType type,
                        const char* message, uint32_t message_size) override {
    if (getenv("KITTYTUNE_WIDEVINE_DEBUG")) {
      fprintf(stderr, "[ktwv] session message type=%u size=%u\n", static_cast<unsigned>(type),
              static_cast<unsigned>(message_size));
      fflush(stderr);
    }
    std::lock_guard<std::mutex> lock(bridge_->mutex);
    if (type == MessageType::kLicenseRequest) {
      bridge_->license_request.assign(reinterpret_cast<const uint8_t*>(message),
                                      reinterpret_cast<const uint8_t*>(message) + message_size);
      bridge_->license_requested = true;
    }
    bridge_->notify();
  }

  void OnSessionKeysChange(const char* session_id, uint32_t session_id_size,
                           bool has_additional_usable_key, const KeyInformation* keys_info,
                           uint32_t keys_info_count) override {
    std::lock_guard<std::mutex> lock(bridge_->mutex);
    bridge_->keys_changed = true;
    bridge_->keys_usable = has_additional_usable_key;
    if (keys_info_count > 0 && keys_info[0].key_id && keys_info[0].key_id_size > 0) {
      bridge_->last_key_id.assign(keys_info[0].key_id,
                                  keys_info[0].key_id + keys_info[0].key_id_size);
    }
    bridge_->notify();
  }

  void OnExpirationChange(const char*, uint32_t, Time) override {}
  void OnSessionClosed(const char*, uint32_t) override {}
  void SendPlatformChallenge(const char*, uint32_t, const char*, uint32_t) override {}
  void EnableOutputProtection(uint32_t) override {}
  void QueryOutputProtectionStatus() override {}
  void OnDeferredInitializationDone(StreamType, Status) override {}
  FileIO* CreateFileIO(FileIOClient* client) override {
    return new StorageFileIO(client);
  }

  // The CDM asks for it before it can persist a certificate; answering with nothing leaves it on
  // the built-in default identity.
  void RequestStorageId(uint32_t version) override {
    if (!bridge_->cdm) return;
    const std::vector<uint8_t> id = storage_id();
    bridge_->cdm->OnStorageId(version, id.data(), static_cast<uint32_t>(id.size()));
  }

 private:
  Bridge* bridge_;
};

// The CDM hands back the |user_data| it was created with, so each instance finds its own bridge.
Bridge::~Bridge() {
  if (cdm) cdm->Destroy();
  delete host;
  host = nullptr;
}

void* GetCdmHost(int host_interface_version, void* user_data) {
  if (host_interface_version != Host_10::kVersion || !user_data) return nullptr;
  auto* bridge = static_cast<Bridge*>(user_data);
  std::lock_guard<std::mutex> lock(bridge->host_mutex);
  if (!bridge->host) bridge->host = new BridgeHost(bridge);
  return bridge->host;
}

// ---------------------------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------------------------

// Browsers ship the same CDM API on every platform, so only how it is loaded differs.
void* open_library(const char* path) {
#ifdef _WIN32
  return reinterpret_cast<void*>(LoadLibraryA(path));
#else
  return dlopen(path, RTLD_NOW | RTLD_GLOBAL);
#endif
}

void* load_symbol(void* library, const char* name) {
#ifdef _WIN32
  return reinterpret_cast<void*>(GetProcAddress(reinterpret_cast<HMODULE>(library), name));
#else
  return dlsym(library, name);
#endif
}

const char* last_load_error() {
#ifdef _WIN32
  return "LoadLibrary failed";
#else
  return dlerror();
#endif
}

void set_error(char* err, int err_len, const char* message) {
  if (!err || err_len <= 0) return;
  snprintf(err, static_cast<size_t>(err_len), "%s", message ? message : "unknown error");
}

}  // namespace

extern "C" {

// Opaque handle.
typedef void* KtwvCdm;

/**
 * Loads the CDM at |cdm_path| and initialises it.
 * Returns NULL on failure and fills |err|.
 */
KtwvCdm ktwv_create(const char* cdm_path, char* err, int err_len) {
  if (!cdm_path) {
    set_error(err, err_len, "no CDM path");
    return nullptr;
  }
  void* lib = open_library(cdm_path);
  if (!lib) {
    set_error(err, err_len, last_load_error());
    return nullptr;
  }

  auto init_module = reinterpret_cast<void (*)()>(load_symbol(lib, "InitializeCdmModule_4"));
  auto create = reinterpret_cast<void* (*)(int, const char*, uint32_t, GetCdmHostFunc, void*)>(
      load_symbol(lib, "CreateCdmInstance"));
  if (!init_module || !create) {
    set_error(err, err_len, "this library does not export the CDM API");
    return nullptr;
  }
  // Initialising a module is a one-time, process-wide operation: calling it again for the second
  // instance of the same library made that instance's Initialize never report back.
  {
    static std::mutex init_mutex;
    static std::set<void*> initialized;
    std::lock_guard<std::mutex> lock(init_mutex);
    if (initialized.insert(lib).second) init_module();
  }

  Bridge* bridge = new Bridge();
  const char* key_system = "com.widevine.alpha";
  void* instance = create(ContentDecryptionModule_10::kVersion, key_system,
                          static_cast<uint32_t>(strlen(key_system)), GetCdmHost, bridge);
  if (!instance) {
    delete bridge;
    set_error(err, err_len, "CreateCdmInstance refused interface 10");
    return nullptr;
  }
  bridge->cdm = reinterpret_cast<ContentDecryptionModule_10*>(instance);

  // A distinctive identifier is what a per-device certificate is: without allowing it the CDM can
  // only present the default identity its binary ships with.
  bridge->cdm->Initialize(false, false, false);
  {
    std::unique_lock<std::mutex> lock(bridge->mutex);
    if (!bridge->cv.wait_for(lock, std::chrono::seconds(20),
                             [bridge] { return bridge->initialized; })) {
      bridge->cdm->Destroy();
      delete bridge;
      set_error(err, err_len, "the CDM never reported that it was initialised");
      return nullptr;
    }
    if (!bridge->initialized_ok) {
      bridge->cdm->Destroy();
      delete bridge;
      set_error(err, err_len, "the CDM reported an initialisation failure");
      return nullptr;
    }
  }

  return bridge;
}

void ktwv_destroy(KtwvCdm handle) {
  Bridge* bridge = static_cast<Bridge*>(handle);
  if (!bridge) return;
  // ~Bridge destroys the CDM instance and its host.
  delete bridge;
}

/**
 * Clears the state of the previous session so a second track can reuse the CDM.
 */
void ktwv_reset_session(KtwvCdm handle) {
  Bridge* bridge = static_cast<Bridge*>(handle);
  if (!bridge) return;
  std::string session_id;
  {
    // The lock is only for our own state. CloseSession runs timers and host callbacks, and holding
    // the mutex across it deadlocks the app the second time a session is opened on the same CDM:
    // the call never returns (reported as a download stuck at 0%).
    std::lock_guard<std::mutex> lock(bridge->mutex);
    session_id.swap(bridge->session_id);
    bridge->license_requested = false;
    bridge->keys_changed = false;
    bridge->keys_usable = false;
    bridge->rejected = false;
    bridge->last_error.clear();
    bridge->license_request.clear();
    bridge->last_key_id.clear();
  }
  if (!session_id.empty() && bridge->cdm) {
    bridge->cdm->CloseSession(3, session_id.c_str(),
                              static_cast<uint32_t>(session_id.size()));
  }
}

/**
 * Hands the CDM the license service's certificate before any session is opened.
 *
 * SoundCloud's licence endpoint only answers a request that carries an `encrypted_client_id`
 * bound to its own service certificate, the one its DRM provider signs. A CDM that never received
 * that certificate sends its built-in client id instead, and the licence is refused ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â which is
 * indistinguishable from "this machine has no CDM" if you only look at the status code.
 *
 * Returns 1 when the CDM accepted the certificate.
 */
int ktwv_set_server_certificate(KtwvCdm handle, const uint8_t* certificate, int certificate_len,
                                char* err, int err_len) {
  Bridge* bridge = static_cast<Bridge*>(handle);
  if (!bridge || !certificate || certificate_len <= 0) {
    set_error(err, err_len, "bad arguments");
    return 0;
  }
  {
    std::lock_guard<std::mutex> lock(bridge->mutex);
    bridge->promise_settled = false;
    bridge->promise_ok = false;
  }
  bridge->cdm->SetServerCertificate(1000, certificate, static_cast<uint32_t>(certificate_len));

  std::unique_lock<std::mutex> lock(bridge->mutex);
  if (!bridge->cv.wait_for(lock, std::chrono::seconds(10),
                           [bridge] { return bridge->promise_settled; })) {
    set_error(err, err_len, "the CDM did not answer the server certificate");
    return 0;
  }
  if (!bridge->promise_ok) {
    set_error(err, err_len, "the CDM rejected the server certificate");
    return 0;
  }
  return 1;
}

/**
 * Opens a session on |pssh| and returns the license request the CDM produced.
 * The caller owns |*out| and must release it with ktwv_free.
 * Returns 1 on success, 0 on failure (with |err| filled).
 */
int ktwv_license_request(KtwvCdm handle, const uint8_t* pssh, int pssh_len, uint8_t** out,
                         int* out_len, char* err, int err_len) {
  Bridge* bridge = static_cast<Bridge*>(handle);
  if (!bridge || !pssh || pssh_len <= 0) {
    set_error(err, err_len, "bad arguments");
    return 0;
  }

  bridge->cdm->CreateSessionAndGenerateRequest(1, SessionType::kTemporary, InitDataType::kCenc,
                                               pssh, static_cast<uint32_t>(pssh_len));

  std::unique_lock<std::mutex> lock(bridge->mutex);
  if (!bridge->cv.wait_for(lock, std::chrono::seconds(30),
                           [bridge] { return bridge->license_requested || bridge->rejected; })) {
    set_error(err, err_len, "the CDM produced no license request");
    return 0;
  }
  if (bridge->rejected) {
    set_error(err, err_len, bridge->last_error.c_str());
    return 0;
  }

  const std::vector<uint8_t>& request = bridge->license_request;
  *out = static_cast<uint8_t*>(malloc(request.size()));
  memcpy(*out, request.data(), request.size());
  *out_len = static_cast<int>(request.size());
  return 1;
}

/**
 * Feeds |license| into the open session and waits until the CDM reports usable keys.
 * Returns 1 when keys are usable, 0 otherwise.
 */
int ktwv_provide_license(KtwvCdm handle, const uint8_t* license, int license_len, char* err,
                         int err_len) {
  Bridge* bridge = static_cast<Bridge*>(handle);
  if (!bridge || !license || license_len <= 0) {
    set_error(err, err_len, "bad arguments");
    return 0;
  }

  bridge->cdm->UpdateSession(2, bridge->session_id.c_str(),
                             static_cast<uint32_t>(bridge->session_id.size()), license,
                             static_cast<uint32_t>(license_len));

  std::unique_lock<std::mutex> lock(bridge->mutex);
  if (!bridge->cv.wait_for(lock, std::chrono::seconds(30),
                           [bridge] { return bridge->keys_changed || bridge->rejected; })) {
    set_error(err, err_len, "the CDM accepted the license but reported no keys");
    return 0;
  }
  if (bridge->rejected) {
    set_error(err, err_len, bridge->last_error.c_str());
    return 0;
  }
  if (!bridge->keys_usable) {
    set_error(err, err_len, "the license granted keys that are not usable");
    return 0;
  }
  return 1;
}

/**
 * Decrypts one CENC sample. |subsamples| is a flat array of (clear_bytes, cipher_bytes) pairs, or
 * NULL when the whole sample is encrypted.
 * The caller owns |*out| and must release it with ktwv_free.
 */
int ktwv_decrypt(KtwvCdm handle, const uint8_t* data, int data_len, const uint8_t* iv, int iv_len,
                 const uint32_t* subsamples, int subsample_count, const uint8_t* key_id,
                 int key_id_len, uint8_t** out, int* out_len, char* err, int err_len) {
  Bridge* bridge = static_cast<Bridge*>(handle);
  if (!bridge || !data || data_len <= 0) {
    set_error(err, err_len, "bad arguments");
    return 0;
  }

  std::vector<SubsampleEntry> entries;
  entries.reserve(static_cast<size_t>(subsample_count));
  for (int i = 0; i < subsample_count; i++) {
    SubsampleEntry entry{};
    entry.clear_bytes = subsamples[i * 2];
    entry.cipher_bytes = subsamples[i * 2 + 1];
    entries.push_back(entry);
  }

  InputBuffer_2 input{};
  input.data = data;
  input.data_size = static_cast<uint32_t>(data_len);
  input.encryption_scheme = EncryptionScheme::kCenc;
  input.key_id = key_id;
  input.key_id_size = key_id ? static_cast<uint32_t>(key_id_len) : 0;
  input.iv = iv;
  input.iv_size = static_cast<uint32_t>(iv_len);
  input.subsamples = entries.empty() ? nullptr : entries.data();
  input.num_subsamples = static_cast<uint32_t>(entries.size());
  input.pattern.crypt_byte_block = 0;
  input.pattern.skip_byte_block = 0;
  input.timestamp = 0;

  DecryptedBlock* block = nullptr;
  // The CDM fills a DecryptedBlock that the host allocated; a tiny local implementation keeps the
  // ownership rules obvious.
  class LocalBlock : public DecryptedBlock {
   public:
    void SetDecryptedBuffer(Buffer* buffer) override { buffer_ = buffer; }
    Buffer* DecryptedBuffer() override { return buffer_; }
    void SetTimestamp(int64_t timestamp) override { timestamp_ = timestamp; }
    int64_t Timestamp() const override { return timestamp_; }

   private:
    Buffer* buffer_ = nullptr;
    int64_t timestamp_ = 0;
  };
  LocalBlock local;
  block = &local;

  Status status = bridge->cdm->Decrypt(input, block);
  if (status != Status::kSuccess) {
    char message[64];
    snprintf(message, sizeof(message), "Decrypt returned status %u", status);
    set_error(err, err_len, message);
    return 0;
  }
  Buffer* plain = block->DecryptedBuffer();
  if (!plain) {
    set_error(err, err_len, "Decrypt produced no buffer");
    return 0;
  }
  const uint32_t size = plain->Size();
  *out = static_cast<uint8_t*>(malloc(size > 0 ? size : 1));
  if (size > 0) memcpy(*out, plain->Data(), size);
  *out_len = static_cast<int>(size);
  plain->Destroy();
  return 1;
}

void ktwv_free(void* pointer) { free(pointer); }

}  // extern "C"















