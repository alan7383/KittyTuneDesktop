// Public Windows PlayReady Media Foundation declarations for older MinGW headers.
//
// MinGW's headers predate the EME/CDM interfaces, so this file carries only what the Windows
// PlayReady path uses. The IIDs and vtable layouts are copied from the Windows 10.0.28000 SDK
// (mfcontentdecryptionmodule.h / mfmediaengine.h / mfapi.h).
#pragma once

#define INITGUID
#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfobjects.h>
#include <mfmediaengine.h>
#include <mfreadwrite.h>
#include <mferror.h>
#include <mmreg.h>
#include <propsys.h>
#include <propvarutil.h>

#include <cstdint>
#include <string>
#include <vector>

// ---- Media Foundation EME interfaces ---------------------------------------------------------

struct IMFTrustedInput;
struct IMFPMPHostApp;

enum MF_MEDIAKEYSESSION_TYPE {
  MF_MEDIAKEYSESSION_TYPE_TEMPORARY = 0,
  MF_MEDIAKEYSESSION_TYPE_PERSISTENT_LICENSE = 1,
  MF_MEDIAKEYSESSION_TYPE_PERSISTENT_RELEASE_MESSAGE = 2,
  MF_MEDIAKEYSESSION_TYPE_PERSISTENT_USAGE_RECORD = 3
};
enum MF_MEDIAKEYSESSION_MESSAGETYPE {
  MF_MEDIAKEYSESSION_MESSAGETYPE_LICENSE_REQUEST = 0,
  MF_MEDIAKEYSESSION_MESSAGETYPE_LICENSE_RENEWAL = 1,
  MF_MEDIAKEYSESSION_MESSAGETYPE_LICENSE_RELEASE = 2,
  MF_MEDIAKEYSESSION_MESSAGETYPE_INDIVIDUALIZATION_REQUEST = 3
};
enum MF_MEDIAKEY_STATUS {
  MF_MEDIAKEY_STATUS_USABLE = 0,
  MF_MEDIAKEY_STATUS_EXPIRED = 1,
  MF_MEDIAKEY_STATUS_OUTPUT_DOWNSCALED = 2,
  MF_MEDIAKEY_STATUS_OUTPUT_NOT_ALLOWED = 3,
  MF_MEDIAKEY_STATUS_STATUS_PENDING = 4,
  MF_MEDIAKEY_STATUS_INTERNAL_ERROR = 5,
  MF_MEDIAKEY_STATUS_RELEASED = 6,
  MF_MEDIAKEY_STATUS_OUTPUT_RESTRICTED = 7
};
struct MFMediaKeyStatus {
  BYTE* keyId;
  UINT keyIdSize;
  MF_MEDIAKEY_STATUS eMediaKeyStatus;
};
enum MF_MEDIAKEYS_REQUIREMENT {
  MF_MEDIAKEYS_REQUIREMENT_REQUIRED = 1,
  MF_MEDIAKEYS_REQUIREMENT_OPTIONAL = 2,
  MF_MEDIAKEYS_REQUIREMENT_NOT_ALLOWED = 3
};

struct IMFMediaEngineClassFactory4 : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE CreateContentDecryptionModuleFactory(
      LPCWSTR keySystem, REFIID riid, LPVOID* ppv) = 0;
};
static const IID IID_IMFMediaEngineClassFactory4 =
    {0xfbe256c1, 0x43cf, 0x4a9b, {0x8c, 0xb8, 0xce, 0x86, 0x32, 0xa3, 0x41, 0x86}};

struct IMFContentDecryptionModuleFactory : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE IsTypeSupported(LPCWSTR keySystem, LPCWSTR contentType) = 0;
  virtual HRESULT STDMETHODCALLTYPE CreateContentDecryptionModuleAccess(
      LPCWSTR keySystem, IPropertyStore** configurations, DWORD numConfigurations, void** access) = 0;
};
static const IID IID_IMFContentDecryptionModuleFactory =
    {0x7d5abf16, 0x4cbb, 0x4e08, {0xb9, 0x77, 0x9b, 0xa5, 0x90, 0x49, 0x94, 0x3e}};

struct IMFContentDecryptionModuleAccess : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE CreateContentDecryptionModule(IPropertyStore* props,
                                                                  void** cdm) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetConfiguration(IPropertyStore** config) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetKeySystem(LPWSTR* keySystem) = 0;
};
static const IID IID_IMFContentDecryptionModuleAccess =
    {0xa853d1f4, 0xe2a0, 0x4303, {0x9e, 0xdc, 0xf1, 0xa6, 0x8e, 0xe4, 0x31, 0x36}};

struct IMFContentDecryptionModuleSessionCallbacks : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE KeyMessage(MF_MEDIAKEYSESSION_MESSAGETYPE type,
                                                const BYTE* message, DWORD messageSize,
                                                LPCWSTR destinationURL) = 0;
  virtual HRESULT STDMETHODCALLTYPE KeyStatusChanged() = 0;
};
static const IID IID_IMFContentDecryptionModuleSessionCallbacks =
    {0x3f96ee40, 0xad81, 0x4096, {0x84, 0x70, 0x59, 0xa4, 0xb7, 0x70, 0xf8, 0x9a}};

struct IMFContentDecryptionModule : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE SetContentEnabler(IUnknown* enabler, IUnknown* result) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetSuspendNotify(IUnknown** notify) = 0;
  virtual HRESULT STDMETHODCALLTYPE SetPMPHostApp(IMFPMPHostApp* app) = 0;
  virtual HRESULT STDMETHODCALLTYPE CreateSession(
      MF_MEDIAKEYSESSION_TYPE type, IMFContentDecryptionModuleSessionCallbacks* cb,
      void** session) = 0;
  virtual HRESULT STDMETHODCALLTYPE SetServerCertificate(const BYTE* cert, DWORD certSize) = 0;
  virtual HRESULT STDMETHODCALLTYPE CreateTrustedInput(const BYTE* data, DWORD size,
                                                       IMFTrustedInput** input) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetProtectionSystemIds(GUID** ids, DWORD* count) = 0;
};
static const IID IID_IMFContentDecryptionModule =
    {0x87be986c, 0x10be, 0x4943, {0xbf, 0x48, 0x4b, 0x54, 0xce, 0x19, 0x83, 0xa2}};

struct IMFContentDecryptionModuleSession : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE GetSessionId(LPWSTR* id) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetExpiration(double* expiration) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetKeyStatuses(MFMediaKeyStatus** statuses, UINT* count) = 0;
  virtual HRESULT STDMETHODCALLTYPE Load(LPCWSTR id, BOOL* loaded) = 0;
  virtual HRESULT STDMETHODCALLTYPE GenerateRequest(LPCWSTR initDataType, const BYTE* data,
                                                    DWORD size) = 0;
  virtual HRESULT STDMETHODCALLTYPE Update(const BYTE* response, DWORD size) = 0;
  virtual HRESULT STDMETHODCALLTYPE Close() = 0;
  virtual HRESULT STDMETHODCALLTYPE Remove() = 0;
};
static const IID IID_IMFContentDecryptionModuleSession =
    {0x4e233efd, 0x1dd2, 0x49e8, {0xb5, 0x77, 0xd6, 0x3e, 0xee, 0x4c, 0x0d, 0x33}};

// ---- interfaces MinGW does not declare --------------------------------------------------------

struct IMFPMPHost : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE LockProcess() = 0;
  virtual HRESULT STDMETHODCALLTYPE UnlockProcess() = 0;
  virtual HRESULT STDMETHODCALLTYPE CreateObjectByCLSID(REFCLSID clsid, IStream* stream,
                                                         REFIID riid, void** ppv) = 0;
};
struct IMFPMPHostApp : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE LockProcess() = 0;
  virtual HRESULT STDMETHODCALLTYPE UnlockProcess() = 0;
  virtual HRESULT STDMETHODCALLTYPE ActivateClassById(LPCWSTR id, IStream* stream, REFIID riid,
                                                       void** ppv) = 0;
};
static const IID IID_IMFPMPHost = {0xf70ca1a9, 0xfdc7, 0x4782,
                                   {0xb9, 0x94, 0xad, 0xff, 0xb1, 0xc9, 0x86, 0x06}};
static const IID IID_IMFPMPHostApp = {0x84d2054a, 0x3aa1, 0x4728,
                                      {0xa3, 0xb0, 0x44, 0x0a, 0x41, 0x8c, 0xf4, 0x9c}};

struct IMFTrustedInput : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE GetInputTrustAuthority(DWORD streamId, REFIID riid,
                                                            IUnknown** object) = 0;
};
static const IID IID_IMFTrustedInput = {0x542612c4, 0xa1b8, 0x4632,
                                        {0xb5, 0x21, 0xde, 0x11, 0xea, 0x64, 0xa0, 0xb0}};
static const IID IID_IMFInputTrustAuthority = {0xd19f8e98, 0xb126, 0x4446,
                                               {0x89, 0x0c, 0x5d, 0xcb, 0x7a, 0xd7, 0x14, 0x53}};
struct IMFOutputPolicy;
struct IMFInputTrustAuthority : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE GetDecrypter(REFIID riid, void** ppv) = 0;
  virtual HRESULT STDMETHODCALLTYPE RequestAccess(MFPOLICYMANAGER_ACTION action,
                                                   IMFActivate** enabler) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetPolicy(MFPOLICYMANAGER_ACTION action,
                                                IMFOutputPolicy** policy) = 0;
  virtual HRESULT STDMETHODCALLTYPE BindAccess(MFINPUTTRUSTAUTHORITY_ACCESS_PARAMS* params) = 0;
  virtual HRESULT STDMETHODCALLTYPE UpdateAccess(MFINPUTTRUSTAUTHORITY_ACCESS_PARAMS* params) = 0;
  virtual HRESULT STDMETHODCALLTYPE Reset() = 0;
};

// ---- EME property keys and constants missing from MinGW ---------------------------------------

static const PROPERTYKEY kEmeInitDataTypes = {
    {0x497d231b, 0x4eb9, 0x4df0, {0xb4, 0x74, 0xb9, 0xaf, 0xeb, 0x0a, 0xdf, 0x38}}, 2};
static const PROPERTYKEY kEmeDistinctiveId = {
    {0x7dc9c4a5, 0x12be, 0x497e, {0x8b, 0xff, 0x9b, 0x60, 0xb2, 0xdc, 0x58, 0x45}}, 2};
static const PROPERTYKEY kEmePersistedState = {
    {0x5d4df6ae, 0x9af1, 0x4e3d, {0x95, 0x5b, 0x0e, 0x4b, 0xd2, 0x2f, 0xed, 0xf0}}, 3};
static const PROPERTYKEY kEmeAudioCapabilities = {
    {0x980fbb84, 0x297d, 0x4ea7, {0x89, 0x5f, 0xbc, 0xf2, 0x8a, 0x46, 0x28, 0x81}}, 4};
static const PROPERTYKEY kEmeVideoCapabilities = {
    {0xb172f83d, 0x30dd, 0x4c10, {0x80, 0x06, 0xed, 0x53, 0xda, 0x4d, 0x3b, 0xdb}}, 5};
static const PROPERTYKEY kEmeRobustness = {
    {0x9d3d2b9e, 0x7023, 0x4944, {0xa8, 0xf5, 0xec, 0xca, 0x52, 0xa4, 0x69, 0x90}}, 2};
static const PROPERTYKEY kEmeContentType = {
    {0x289fb1fc, 0xd9c4, 0x4cc7, {0xb2, 0xbe, 0x97, 0x2b, 0x0e, 0x9b, 0x28, 0x3a}}, 3};
static const PROPERTYKEY kOriginId = {
    {0x1218a3e2, 0xcfb0, 0x4c98, {0x90, 0xe5, 0x5f, 0x58, 0x18, 0xd4, 0xb6, 0x7e}}, 2};
static const PROPERTYKEY kStorePath = {
    {0x77d993b9, 0xba61, 0x4bb7, {0x92, 0xc6, 0x18, 0xc8, 0x6a, 0x18, 0x9c, 0x06}}, 2};

static const GUID CLSID_EMEStoreActivate = {
    0x2df7b51e, 0x797b, 0x4d06, {0xbe, 0x71, 0xd1, 0x4a, 0x52, 0xcf, 0x84, 0x21}};
static const GUID GUID_ClassName = {
    0x77631a31, 0xe5e7, 0x4785, {0xbf, 0x17, 0x20, 0xf5, 0x7b, 0x22, 0x48, 0x02}};
static const GUID GUID_ObjectStream = {
    0x3e73735c, 0xe6c0, 0x481d, {0x82, 0x60, 0xee, 0x5d, 0xb1, 0x34, 0x3b, 0x5f}};
static const GUID MF_CONTENTDECRYPTIONMODULE_SERVICE = {
    0x15320c45, 0xff80, 0x484a, {0x9d, 0xcb, 0x0d, 0xf8, 0x94, 0xe6, 0x9a, 0x01}};

// CENC sample attributes.
static const GUID MFSampleExtension_Encryption_ProtectionScheme = {
    0xd054d096, 0x28bb, 0x45da, {0x87, 0xec, 0x74, 0xf3, 0x51, 0x87, 0x14, 0x06}};
static const GUID MFSampleExtension_Encryption_SubSample_Mapping = {
    0x8444f27a, 0x69a1, 0x48da, {0xbd, 0x08, 0x11, 0xce, 0xf3, 0x68, 0x30, 0xd2}};
static const GUID MFSampleExtension_Content_KeyID = {
    0xc6c7f5b0, 0xacca, 0x415b, {0x87, 0xd9, 0x10, 0x44, 0x14, 0x69, 0xef, 0xc6}};
static const GUID GUID_NULL_VALUE = {0, 0, 0, {0, 0, 0, 0, 0, 0, 0, 0}};


enum MFSampleEncryptionProtectionScheme {
  MF_SAMPLE_ENCRYPTION_PROTECTION_SCHEME_NONE = 0,
  MF_SAMPLE_ENCRYPTION_PROTECTION_SCHEME_AES_CTR = 1,
  MF_SAMPLE_ENCRYPTION_PROTECTION_SCHEME_AES_CBC = 2,
};

// ---- content protection (Media Engine license callback) --------------------------------------

struct IMFContentEnabler : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE GetEnableType(GUID* type) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetEnableURL(LPWSTR* url, DWORD* url_length,
                                                  int* trust_status) = 0;
  virtual HRESULT STDMETHODCALLTYPE GetEnableData(BYTE** data, DWORD* size) = 0;
  virtual HRESULT STDMETHODCALLTYPE IsAutomaticSupported(BOOL* automatic) = 0;
  virtual HRESULT STDMETHODCALLTYPE AutomaticEnable() = 0;
  virtual HRESULT STDMETHODCALLTYPE MonitorEnable() = 0;
  virtual HRESULT STDMETHODCALLTYPE Cancel() = 0;
};
static const IID IID_IMFContentEnabler = {0xd3c4ef59, 0x49ce, 0x4381,
                                          {0x90, 0x71, 0xd5, 0xbc, 0xd0, 0x44, 0xc7, 0x70}};
struct IMFContentProtectionManager : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE BeginEnableContent(IMFActivate* enabler, IMFTopology* topology,
                                                        IMFAsyncCallback* callback,
                                                        IUnknown* state) = 0;
  virtual HRESULT STDMETHODCALLTYPE EndEnableContent(IMFAsyncResult* result) = 0;
};
static const IID IID_IMFContentProtectionManager = {
    0xacf92459, 0x6a61, 0x42bd, {0xb5, 0x7c, 0xb4, 0x3e, 0x51, 0x20, 0x3c, 0xb0}};
// ---- small helpers ---------------------------------------------------------------------------

inline HRESULT SetStoreUint(IPropertyStore* store, REFPROPERTYKEY key, UINT32 value) {
  PROPVARIANT pv;
  PropVariantInit(&pv);
  HRESULT hr = InitPropVariantFromUInt32(value, &pv);
  if (SUCCEEDED(hr)) hr = store->SetValue(key, pv);
  PropVariantClear(&pv);
  return hr;
}

inline HRESULT SetStoreString(IPropertyStore* store, REFPROPERTYKEY key, LPCWSTR value) {
  PROPVARIANT pv;
  PropVariantInit(&pv);
  HRESULT hr = InitPropVariantFromString(value, &pv);
  if (SUCCEEDED(hr)) hr = store->SetValue(key, pv);
  PropVariantClear(&pv);
  return hr;
}

inline GUID GuidFromNetworkBytes(const uint8_t* b) {
  GUID g;
  g.Data1 = (uint32_t(b[0]) << 24) | (uint32_t(b[1]) << 16) | (uint32_t(b[2]) << 8) | b[3];
  g.Data2 = uint16_t((b[4] << 8) | b[5]);
  g.Data3 = uint16_t((b[6] << 8) | b[7]);
  memcpy(g.Data4, b + 8, 8);
  return g;
}




