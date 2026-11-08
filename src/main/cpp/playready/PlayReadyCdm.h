// Native licence exchange and protected-process activation. No sample decryption API is called.
#include "MediaFoundationCompat.h"

#include <winhttp.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#pragma comment(lib, "winhttp.lib")

namespace {

// ---------------------------------------------------------------------------------------------
// Logging
// ---------------------------------------------------------------------------------------------

void LogHr(const char* what, HRESULT hr) {
  std::printf("%s: 0x%08lx\n", what, static_cast<unsigned long>(hr));
  std::fflush(stdout);
}

// ---------------------------------------------------------------------------------------------
// PlayReady CDM
// ---------------------------------------------------------------------------------------------

std::mutex g_challenge_mutex;
std::vector<uint8_t> g_challenge;
std::atomic<int> g_key_status_changes{0};

class SessionCallbacks : public IMFContentDecryptionModuleSessionCallbacks {
 public:
  HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
    if (riid == IID_IUnknown || riid == IID_IMFContentDecryptionModuleSessionCallbacks) {
      *ppv = this;
      AddRef();
      return S_OK;
    }
    printf("QI not supported %08lx-%04x-%04x\n",riid.Data1,riid.Data2,riid.Data3);
    *ppv = nullptr;
    return E_NOINTERFACE;
  }
  ULONG STDMETHODCALLTYPE AddRef() override { return 1; }
  ULONG STDMETHODCALLTYPE Release() override { return 1; }
  HRESULT STDMETHODCALLTYPE KeyMessage(MF_MEDIAKEYSESSION_MESSAGETYPE type, const BYTE* message,
                                        DWORD size, LPCWSTR url) override {
    std::printf("KeyMessage type=%d size=%lu url=%ls\n", static_cast<int>(type),
                static_cast<unsigned long>(size), url ? url : L"");
    std::fflush(stdout);
    std::lock_guard<std::mutex> lock(g_challenge_mutex);
    g_challenge.assign(message, message + size);
    return S_OK;
  }
  HRESULT STDMETHODCALLTYPE KeyStatusChanged() override {
    g_key_status_changes++;
    return S_OK;
  }
};

// The PlayReady CDM lives in a protected process and asks the host to activate objects there.
class PmpHostApp : public IMFPMPHostApp {
 public:
  explicit PmpHostApp(IMFPMPHost* host) : host_(host) {}
  HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
    if (riid == IID_IUnknown || riid == IID_IMFPMPHostApp) {
      *ppv = this;
      AddRef();
      return S_OK;
    }
    printf("QI not supported %08lx-%04x-%04x\n",riid.Data1,riid.Data2,riid.Data3);
    *ppv = nullptr;
    return E_NOINTERFACE;
  }
  ULONG STDMETHODCALLTYPE AddRef() override { return 1; }
  ULONG STDMETHODCALLTYPE Release() override { return 1; }
  HRESULT STDMETHODCALLTYPE LockProcess() override { return host_ ? host_->LockProcess() : E_FAIL; }
  HRESULT STDMETHODCALLTYPE UnlockProcess() override {
    return host_ ? host_->UnlockProcess() : E_FAIL;
  }
  HRESULT STDMETHODCALLTYPE ActivateClassById(LPCWSTR id, IStream* stream, REFIID riid,
                                               void** ppv) override {
    if (!host_) return E_FAIL;
    IMFAttributes* attrs = nullptr;
    HRESULT hr = MFCreateAttributes(&attrs, 3);
    if (FAILED(hr)) return hr;
    hr = attrs->SetString(GUID_ClassName, id);
    if (SUCCEEDED(hr) && stream) {
      STATSTG stat = {};
      hr = stream->Stat(&stat, STATFLAG_NOOPEN | STATFLAG_NONAME);
      if (SUCCEEDED(hr)) {
        std::vector<BYTE> blob(static_cast<size_t>(stat.cbSize.QuadPart));
        ULONG read = 0;
        if (!blob.empty()) hr = stream->Read(blob.data(), static_cast<ULONG>(blob.size()), &read);
        if (SUCCEEDED(hr)) hr = attrs->SetBlob(GUID_ObjectStream, blob.data(), read);
      }
    }
    IStream* serialized = nullptr;
    if (SUCCEEDED(hr)) hr = CreateStreamOnHGlobal(nullptr, TRUE, &serialized);
    if (SUCCEEDED(hr)) hr = MFSerializeAttributesToStream(attrs, 0, serialized);
    if (SUCCEEDED(hr)) {
      LARGE_INTEGER zero = {};
      hr = serialized->Seek(zero, STREAM_SEEK_SET, nullptr);
    }
    IMFActivate* activate = nullptr;
    if (SUCCEEDED(hr)) {
      hr = host_->CreateObjectByCLSID(CLSID_EMEStoreActivate, serialized, IID_IMFActivate,
                                      reinterpret_cast<void**>(&activate));
    }
    if (SUCCEEDED(hr)) hr = activate->ActivateObject(riid, ppv);
    if (activate) activate->Release();
    if (serialized) serialized->Release();
    if (attrs) attrs->Release();
    return hr;
  }

 private:
  IMFPMPHost* host_;
};

struct PlayReadyCdm {
  IMFContentDecryptionModule* cdm = nullptr;
  IMFContentDecryptionModuleSession* session = nullptr;
  IMFTrustedInput* trusted_input = nullptr;
  PmpHostApp* pmp_host_app = nullptr;
  IMFGetService* service = nullptr;
  IMFPMPHost* pmp_host = nullptr;
  GUID key_id{};
  bool keys_usable = false;
  ~PlayReadyCdm() {
    if(session){session->Close();session->Release();}
    if(trusted_input)trusted_input->Release();
    if(cdm){cdm->SetPMPHostApp(nullptr);cdm->Release();}
    if(service)service->Release();
    if(pmp_host)pmp_host->Release();
    delete pmp_host_app;
  }
};

HRESULT CreatePlayReadyCdm(PlayReadyCdm* out, const std::wstring& storePath) {
  IMFMediaEngineClassFactory4* factory4 = nullptr;
  HRESULT hr = CoCreateInstance(CLSID_MFMediaEngineClassFactory, nullptr, CLSCTX_INPROC_SERVER,
                                IID_IMFMediaEngineClassFactory4,
                                reinterpret_cast<void**>(&factory4));
  if (FAILED(hr)) return hr;

  IMFContentDecryptionModuleFactory* factory = nullptr;
  hr = factory4->CreateContentDecryptionModuleFactory(
      L"com.microsoft.playready", IID_IMFContentDecryptionModuleFactory,
      reinterpret_cast<void**>(&factory));
  factory4->Release();
  if (FAILED(hr)) return hr;

  IPropertyStore* config = nullptr;
  hr = PSCreateMemoryPropertyStore(IID_IPropertyStore, reinterpret_cast<void**>(&config));
  if (FAILED(hr)) {
    factory->Release();
    return hr;
  }

  BSTR* init_types = static_cast<BSTR*>(CoTaskMemAlloc(sizeof(BSTR)));
  init_types[0] = SysAllocString(L"cenc");
  PROPVARIANT pv;
  PropVariantInit(&pv);
  pv.vt = VT_VECTOR | VT_BSTR;
  pv.cabstr.cElems = 1;
  pv.cabstr.pElems = init_types;
  config->SetValue(kEmeInitDataTypes, pv);
  PropVariantClear(&pv);

  // Ask for audio at PlayReady software robustness (SL2000). The variant deliberately owns the
  // property store and is not cleared: clearing it made the CDM's PropVariant copy dereference a
  // freed store on this machine.
  IPropertyStore* audio_cap = nullptr;
  PSCreateMemoryPropertyStore(IID_IPropertyStore, reinterpret_cast<void**>(&audio_cap));
  SetStoreString(audio_cap, kEmeContentType, L"audio/mp4;codecs=\"mp4a.40.2\"");
  SetStoreString(audio_cap, kEmeRobustness, L"2000");
  PropVariantInit(&pv);
  pv.vt = VT_VARIANT | VT_VECTOR;
  pv.capropvar.cElems = 1;
  pv.capropvar.pElems = static_cast<PROPVARIANT*>(CoTaskMemAlloc(sizeof(PROPVARIANT)));
  PropVariantInit(pv.capropvar.pElems);
  pv.capropvar.pElems[0].vt = VT_UNKNOWN;
  pv.capropvar.pElems[0].punkVal = audio_cap;
  config->SetValue(kEmeAudioCapabilities, pv);

  SetStoreUint(config, kEmePersistedState, MF_MEDIAKEYS_REQUIREMENT_OPTIONAL);
  SetStoreUint(config, kEmeDistinctiveId, MF_MEDIAKEYS_REQUIREMENT_OPTIONAL);

  IMFContentDecryptionModuleAccess* access = nullptr;
  IPropertyStore* configs[1] = {config};
  hr = factory->CreateContentDecryptionModuleAccess(L"com.microsoft.playready", configs, 1,
                                                    reinterpret_cast<void**>(&access));
  factory->Release();
  config->Release();
  if (FAILED(hr)) return hr;

  IPropertyStore* props = nullptr;
  PSCreateMemoryPropertyStore(IID_IPropertyStore, reinterpret_cast<void**>(&props));
  SetStoreString(props, kOriginId, L"kittytune-playready");
  SetStoreString(props, kStorePath, storePath.c_str());
  hr = access->CreateContentDecryptionModule(props, reinterpret_cast<void**>(&out->cdm));
  props->Release();
  access->Release();
  if (FAILED(hr)) return hr;

  hr = out->cdm->QueryInterface(IID_IMFGetService, reinterpret_cast<void**>(&out->service));
  if (FAILED(hr)) return hr;
  hr = out->service->GetService(MF_CONTENTDECRYPTIONMODULE_SERVICE, IID_IMFPMPHost,
                                reinterpret_cast<void**>(&out->pmp_host));
  if (FAILED(hr)) return hr;
  out->pmp_host_app = new PmpHostApp(out->pmp_host);
  hr = out->cdm->SetPMPHostApp(out->pmp_host_app);
  if (FAILED(hr)) return hr;

  static SessionCallbacks callbacks;
  hr = out->cdm->CreateSession(MF_MEDIAKEYSESSION_TYPE_TEMPORARY, &callbacks,
                               reinterpret_cast<void**>(&out->session));
  return hr;
}

// Extracts the SOAP challenge from a PlayReadyKeyMessage (UTF-16LE XML with base64 challenge).
std::vector<uint8_t> InnerChallenge(const std::vector<uint8_t>& message) {
  std::string utf8;
  if (message.size() >= 2) {
    int size = WideCharToMultiByte(CP_UTF8, 0,
                                   reinterpret_cast<const wchar_t*>(message.data()),
                                   static_cast<int>(message.size() / 2), nullptr, 0, nullptr,
                                   nullptr);
    utf8.resize(size);
    WideCharToMultiByte(CP_UTF8, 0, reinterpret_cast<const wchar_t*>(message.data()),
                        static_cast<int>(message.size() / 2), utf8.data(), size, nullptr, nullptr);
  }
  const std::string open = "<Challenge encoding=\"base64encoded\">";
  size_t a = utf8.find(open);
  size_t b = utf8.find("</Challenge>", a);
  if (a == std::string::npos || b == std::string::npos) return {};
  std::string b64 = utf8.substr(a + open.size(), b - (a + open.size()));
  static const char* table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  uint32_t val = 0; int valb = -8;
  std::vector<uint8_t> out;
  for (unsigned char c : b64) {
    if (c == '=') break;
    const char* p = strchr(table, c);
    if (!p) continue;
    val = (val << 6) + static_cast<int>(p - table);
    valb += 6;
    if (valb >= 0) {
      out.push_back(static_cast<uint8_t>((val >> valb) & 0xFF));
      valb -= 8;
    }
  }
  return out;
}

// POSTs the SOAP challenge to the PlayReady license endpoint. The URL already contains the token.
bool HttpPost(const std::wstring& url, const std::vector<uint8_t>& body,
              std::vector<uint8_t>* response, std::string* error) {
  URL_COMPONENTS parts = {};
  parts.dwStructSize = sizeof(parts);
  parts.dwHostNameLength = static_cast<DWORD>(-1);
  parts.dwUrlPathLength = static_cast<DWORD>(-1);
  parts.dwExtraInfoLength = static_cast<DWORD>(-1);
  if (!WinHttpCrackUrl(url.c_str(), 0, 0, &parts)) {
    *error = "WinHttpCrackUrl failed";
    return false;
  }
  std::wstring host(parts.lpszHostName, parts.dwHostNameLength);
  std::wstring path(parts.lpszUrlPath, parts.dwUrlPathLength);
  if (parts.dwExtraInfoLength > 0) path.append(parts.lpszExtraInfo, parts.dwExtraInfoLength);

  HINTERNET session = WinHttpOpen(L"KittyTune/1.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                  WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
  if (!session) {
    *error = "WinHttpOpen failed";
    return false;
  }
  WinHttpSetTimeouts(session, 15000, 15000, 30000, 30000);
  HINTERNET connect = WinHttpConnect(session, host.c_str(), INTERNET_DEFAULT_HTTPS_PORT, 0);
  if (!connect) {
    WinHttpCloseHandle(session);
    *error = "WinHttpConnect failed";
    return false;
  }
  HINTERNET request = WinHttpOpenRequest(connect, L"POST", path.c_str(), nullptr,
                                         WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES,
                                         WINHTTP_FLAG_SECURE);
  if (!request) {
    WinHttpCloseHandle(connect);
    WinHttpCloseHandle(session);
    *error = "WinHttpOpenRequest failed";
    return false;
  }
  const wchar_t* headers = L"Content-Type: text/xml; charset=utf-8\r\n";
  BOOL ok = WinHttpSendRequest(request, headers, static_cast<DWORD>(-1), const_cast<void*>(static_cast<const void*>(body.data())),
                               static_cast<DWORD>(body.size()), static_cast<DWORD>(body.size()), 0);
  if (ok) ok = WinHttpReceiveResponse(request, nullptr);
  DWORD status = 0;
  DWORD status_size = sizeof(status);
  if (ok) {
    ok = WinHttpQueryHeaders(request, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                             WINHTTP_HEADER_NAME_BY_INDEX, &status, &status_size,
                             WINHTTP_NO_HEADER_INDEX);
  }
  if (ok) {
    std::printf("license POST -> HTTP %lu\n", static_cast<unsigned long>(status));
    std::fflush(stdout);
    DWORD available = 0;
    do {
      available = 0;
      if (!WinHttpQueryDataAvailable(request, &available)) break;
      if (available == 0) break;
      std::vector<uint8_t> chunk(available);
      DWORD read = 0;
      if (!WinHttpReadData(request, chunk.data(), available, &read)) break;
      response->insert(response->end(), chunk.begin(), chunk.begin() + read);
    } while (available > 0);
  }
  if (!ok && error->empty()) *error = "WinHTTP request failed";
  WinHttpCloseHandle(request);
  WinHttpCloseHandle(connect);
  WinHttpCloseHandle(session);
  return ok && status == 200;
}

HRESULT AcquireLicense(PlayReadyCdm* cdm, const std::vector<uint8_t>& pssh,
                       const std::wstring& license_token, std::vector<uint8_t>* license) {
  { std::lock_guard<std::mutex> lock(g_challenge_mutex);g_challenge.clear(); }
  HRESULT hr = cdm->session->GenerateRequest(L"cenc", pssh.data(),
                                             static_cast<DWORD>(pssh.size()));
  if (FAILED(hr)) return hr;
  std::vector<uint8_t> challenge;
  for (int i = 0; i < 100; i++) {
    { std::lock_guard<std::mutex> lock(g_challenge_mutex); challenge=g_challenge; }
    if(!challenge.empty())break;
    Sleep(50);
  }
  if (challenge.empty()) return E_FAIL;
  std::vector<uint8_t> inner = InnerChallenge(challenge);
  std::printf("challenge: %zu (inner %zu)\n", challenge.size(), inner.size());
  if (inner.empty()) return E_FAIL;

  std::wstring url =
      L"https://license.media-streaming.soundcloud.cloud/playback/playready?license_token=" +
      license_token;
  std::string error;
  if (!HttpPost(url, inner, license, &error)) {
    std::printf("license POST failed: %s\n", error.c_str());
    return E_FAIL;
  }
  std::printf("license: %zu bytes\n", license->size());
  return cdm->session->Update(license->data(), static_cast<DWORD>(license->size()));
}

bool KeysUsable(PlayReadyCdm* cdm, GUID* key_id) {
  for (int attempt = 0; attempt < 40; attempt++) {
    MFMediaKeyStatus* statuses = nullptr;
    UINT count = 0;
    HRESULT hr = cdm->session->GetKeyStatuses(&statuses, &count);
    if (FAILED(hr)) return false;
    bool usable = count > 0;
    for (UINT i = 0; i < count; i++) {
      std::printf("key %u: size=%u status=%d\n", i, statuses[i].keyIdSize,
                  static_cast<int>(statuses[i].eMediaKeyStatus));
      if (statuses[i].eMediaKeyStatus != MF_MEDIAKEY_STATUS_USABLE) usable = false;
    }
    if (statuses) CoTaskMemFree(statuses);
    if (usable) return true;
    Sleep(100);
  }
  return false;
}


} // namespace
