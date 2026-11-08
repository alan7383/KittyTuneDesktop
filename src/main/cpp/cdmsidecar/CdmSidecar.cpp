// KittyTune CDM sidecar: the Widevine licence/decrypt bridge, reachable from another machine.
//
// Why this exists. On Windows the licence server refuses every CDM that is not hosted by a browser
// it recognises (docs/SOUNDCLOUD_DRM_WINDOWS.md, section 3), and the PlayReady path that does play
// never hands the app a PCM sample, so the equalizer, the filters, the pitch shifter and the
// visualizer all fall silent on a protected track. The same bridge, driven from a Linux process,
// gets a 200 and decrypts samples in-process. This daemon is that process: it links
// `widevine/WidevineBridge.cpp` unchanged and exposes the same six calls over stdin/stdout, so the
// JVM side keeps its whole decode pipeline (FFmpeg -> DSP -> WASAPI) and simply moves the CDM
// somewhere it is allowed to run.
//
// It is transport-agnostic on purpose: `wsl.exe -- ktcdm ...`, `ssh host ktcdm ...` and a plain
// local `ktcdm ...` are the same process to the caller.
//
// Protocol, both directions, little-endian:
//   request  : u8 opcode, u32 payload length, payload
//   response : u8 status (0 ok, 1 error), u32 payload length, payload (data, or a UTF-8 message)
// The content key never crosses this pipe: DECRYPT takes ciphertext and returns plaintext.
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#else
#include <unistd.h>
#endif

// The bridge's C ABI. Declared here rather than in a header so this file can be compiled next to
// the bridge without adding a build step of its own; the signatures are pinned by WidevineBridge.cpp.
extern "C" {
typedef void* KtwvCdm;
KtwvCdm ktwv_create(const char* cdm_path, char* err, int err_len);
void ktwv_destroy(KtwvCdm handle);
void ktwv_reset_session(KtwvCdm handle);
int ktwv_set_server_certificate(KtwvCdm handle, const uint8_t* certificate, int certificate_len,
                                char* err, int err_len);
int ktwv_license_request(KtwvCdm handle, const uint8_t* pssh, int pssh_len, uint8_t** out,
                         int* out_len, char* err, int err_len);
int ktwv_provide_license(KtwvCdm handle, const uint8_t* license, int license_len, char* err,
                         int err_len);
int ktwv_update(KtwvCdm handle, const uint8_t* message, int message_len, uint8_t** out,
                int* out_len, char* err, int err_len);
int ktwv_decrypt(KtwvCdm handle, const uint8_t* data, int data_len, const uint8_t* iv, int iv_len,
                 const uint32_t* subsamples, int subsample_count, const uint8_t* key_id,
                 int key_id_len, uint8_t** out, int* out_len, char* err, int err_len);
void ktwv_free(void* pointer);
}

namespace {

constexpr uint8_t kQuit = 0;
constexpr uint8_t kLicenseRequest = 1;
constexpr uint8_t kProvideLicense = 2;
constexpr uint8_t kUpdate = 3;
constexpr uint8_t kDecrypt = 4;
constexpr uint8_t kResetSession = 5;
constexpr uint8_t kSetServerCertificate = 6;

constexpr size_t kMaxFrame = 32u * 1024u * 1024u;  // one CENC sample is a few KiB; 32 MiB is a wall.
constexpr size_t kErrorLength = 512;

// Frame I/O over raw file descriptors: std::cout is line buffered and would split a 4-byte length
// across two writes when a sample lands just so.
bool read_exact(int fd, void* target, size_t size) {
  auto* out = static_cast<uint8_t*>(target);
  while (size > 0) {
#ifdef _WIN32
    int got = _read(fd, out, static_cast<unsigned>(size));
#else
    ssize_t got = ::read(fd, out, size);
#endif
    if (got <= 0) return false;
    out += got;
    size -= static_cast<size_t>(got);
  }
  return true;
}

bool write_exact(int fd, const void* source, size_t size) {
  auto* in = static_cast<const uint8_t*>(source);
  while (size > 0) {
#ifdef _WIN32
    int sent = _write(fd, in, static_cast<unsigned>(size));
#else
    ssize_t sent = ::write(fd, in, size);
#endif
    if (sent <= 0) return false;
    in += sent;
    size -= static_cast<size_t>(sent);
  }
  return true;
}

void put_u32(std::vector<uint8_t>& out, uint32_t value) {
  out.push_back(static_cast<uint8_t>(value & 0xFF));
  out.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
  out.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
  out.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

// A cursor over one request payload. Every read is bounds-checked: a truncated frame from a broken
// pipe must end the daemon, never index past the buffer.
struct Cursor {
  const std::vector<uint8_t>& buffer;
  size_t offset = 0;
  bool ok = true;

  explicit Cursor(const std::vector<uint8_t>& source) : buffer(source) {}

  const uint8_t* bytes(size_t count) {
    if (!ok || offset + count > buffer.size()) {
      ok = false;
      return nullptr;
    }
    const uint8_t* at = buffer.data() + offset;
    offset += count;
    return at;
  }

  uint32_t word() {
    const uint8_t* at = bytes(4);
    if (!at) return 0;
    return static_cast<uint32_t>(at[0]) | (static_cast<uint32_t>(at[1]) << 8) |
           (static_cast<uint32_t>(at[2]) << 16) | (static_cast<uint32_t>(at[3]) << 24);
  }
};

bool send_frame(uint8_t status, const void* payload, size_t size) {
  std::vector<uint8_t> header;
  header.reserve(5);
  header.push_back(status);
  put_u32(header, static_cast<uint32_t>(size));
  if (!write_exact(1, header.data(), header.size())) return false;
  return size == 0 || write_exact(1, payload, size);
}

bool send_ok(const uint8_t* payload, size_t size) { return send_frame(0, payload, size); }

bool send_error(const std::string& message) {
  return send_frame(1, message.data(), message.size());
}

// One CDM, one open session. The bridge already serialises the CDM internally, and the JVM side
// drives this pipe from a single decode thread, so no extra locking is needed here.
struct Daemon {
  KtwvCdm cdm = nullptr;
  char error[kErrorLength] = {};

  bool start(const std::string& cdm_path) {
    cdm = ktwv_create(cdm_path.c_str(), error, static_cast<int>(sizeof(error)));
    return cdm != nullptr;
  }

  ~Daemon() {
    if (cdm) ktwv_destroy(cdm);
  }
};

// DECRYPT payload: data, iv, subsample pairs, key id — each length-prefixed.
bool decrypt(Daemon& daemon, const std::vector<uint8_t>& payload) {
  Cursor cursor(payload);
  uint32_t data_length = cursor.word();
  const uint8_t* data = cursor.bytes(data_length);
  uint32_t iv_length = cursor.word();
  const uint8_t* iv = cursor.bytes(iv_length);
  uint32_t pair_count = cursor.word();
  if (pair_count > (payload.size() / 4) + 1) return send_error("subsample count out of range");
  std::vector<uint32_t> subsamples(pair_count * 2);
  for (uint32_t i = 0; i < pair_count * 2; i++) subsamples[i] = cursor.word();
  uint32_t key_id_length = cursor.word();
  const uint8_t* key_id = cursor.bytes(key_id_length);
  if (!cursor.ok || data_length == 0) return send_error("malformed decrypt frame");

  uint8_t* plain = nullptr;
  int plain_length = 0;
  char message[kErrorLength] = {};
  int ok = ktwv_decrypt(daemon.cdm, data, static_cast<int>(data_length), iv,
                        static_cast<int>(iv_length), subsamples.empty() ? nullptr : subsamples.data(),
                        static_cast<int>(pair_count), key_id, static_cast<int>(key_id_length),
                        &plain, &plain_length, message, static_cast<int>(sizeof(message)));
  if (!ok) {
    std::string text(message);
    if (plain) ktwv_free(plain);
    return send_error(text.empty() ? "decrypt failed" : text);
  }
  const bool sent = send_ok(plain, static_cast<size_t>(plain_length));
  ktwv_free(plain);
  return sent;
}

bool handle(Daemon& daemon, uint8_t opcode, const std::vector<uint8_t>& payload) {
  char message[kErrorLength] = {};
  switch (opcode) {
    case kResetSession:
      ktwv_reset_session(daemon.cdm);
      return send_ok(nullptr, 0);
    case kSetServerCertificate: {
      int ok = ktwv_set_server_certificate(daemon.cdm, payload.data(),
                                           static_cast<int>(payload.size()), message,
                                           static_cast<int>(sizeof(message)));
      if (!ok) return send_error(message);
      return send_ok(nullptr, 0);
    }
    case kLicenseRequest: {
      uint8_t* request = nullptr;
      int request_length = 0;
      int ok = ktwv_license_request(daemon.cdm, payload.data(), static_cast<int>(payload.size()),
                                    &request, &request_length, message,
                                    static_cast<int>(sizeof(message)));
      if (!ok) {
        if (request) ktwv_free(request);
        return send_error(message);
      }
      const bool sent = send_ok(request, static_cast<size_t>(request_length));
      ktwv_free(request);
      return sent;
    }
    case kProvideLicense: {
      int ok = ktwv_provide_license(daemon.cdm, payload.data(), static_cast<int>(payload.size()),
                                    message, static_cast<int>(sizeof(message)));
      if (!ok) return send_error(message);
      return send_ok(nullptr, 0);
    }
    case kUpdate: {
      uint8_t* answer = nullptr;
      int answer_length = 0;
      int ok = ktwv_update(daemon.cdm, payload.data(), static_cast<int>(payload.size()), &answer,
                           &answer_length, message, static_cast<int>(sizeof(message)));
      if (!ok) {
        if (answer) ktwv_free(answer);
        return send_error(message);
      }
      const bool sent = send_ok(answer, static_cast<size_t>(answer_length));
      ktwv_free(answer);
      return sent;
    }
    case kDecrypt:
      return decrypt(daemon, payload);
    default:
      return send_error("unknown opcode");
  }
}

}  // namespace

int main(int argc, char** argv) {
#ifdef _WIN32
  _setmode(_fileno(stdin), _O_BINARY);
  _setmode(_fileno(stdout), _O_BINARY);
#endif
  if (argc < 2) {
    std::fprintf(stderr, "usage: ktcdm <libwidevinecdm.so> [--probe]\n");
    return 64;
  }
  const bool probe = argc > 2 && std::strcmp(argv[2], "--probe") == 0;

  // The CDM keeps a provisioned certificate here. Defaulting it beside the binary keeps an install
  // self-contained; the bridge reads the same variable.
  if (!std::getenv("KITTYTUNE_WIDEVINE_STORAGE")) {
    const char* home = std::getenv("HOME");
    if (home && *home) {
      std::string storage = std::string(home) + "/.local/share/kittytune/cdm";
#ifdef _WIN32
      _putenv_s("KITTYTUNE_WIDEVINE_STORAGE", storage.c_str());
#else
      setenv("KITTYTUNE_WIDEVINE_STORAGE", storage.c_str(), 0);
#endif
    }
  }

  Daemon daemon;
  if (!daemon.start(argv[1])) {
    std::fprintf(stderr, "ktcdm: %s\n", daemon.error);
    return 70;
  }
  if (probe) {
    // Creation succeeded, so the library exports the CDM API and answered Initialize.
    std::fputs("ok\n", stderr);
    return 0;
  }

  for (;;) {
    uint8_t header[5];
    if (!read_exact(0, header, sizeof(header))) return 0;  // caller closed the pipe: normal exit
    const uint8_t opcode = header[0];
    const uint32_t length = static_cast<uint32_t>(header[1]) |
                            (static_cast<uint32_t>(header[2]) << 8) |
                            (static_cast<uint32_t>(header[3]) << 16) |
                            (static_cast<uint32_t>(header[4]) << 24);
    if (length > kMaxFrame) {
      send_error("frame too large");
      return 70;
    }
    std::vector<uint8_t> payload(length);
    if (length > 0 && !read_exact(0, payload.data(), length)) return 0;
    if (opcode == kQuit) {
      send_ok(nullptr, 0);
      return 0;
    }
    if (!handle(daemon, opcode, payload)) return 0;  // the far end went away mid-answer
  }
}
