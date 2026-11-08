// Unpackaged Windows PlayReady renderer. Encrypted samples stay in Media Foundation/PMP.
#include "PlayReadyCdm.h"
#include <filesystem>
#include <chrono>
#include <cmath>
#include <appmodel.h>
__CRT_UUID_DECL(IMFContentProtectionManager,0xacf92459,0x6a61,0x42bd,0xb5,0x7c,0xb4,0x3e,0x51,0x20,0x3c,0xb0)
struct IMFMediaEngineProtectedContent : IUnknown {
  virtual HRESULT STDMETHODCALLTYPE ShareResources(IUnknown*)=0;
  virtual HRESULT STDMETHODCALLTYPE GetRequiredProtections(DWORD*)=0;
  virtual HRESULT STDMETHODCALLTYPE SetOPMWindow(HWND)=0;
  virtual HRESULT STDMETHODCALLTYPE TransferVideoFrame(IUnknown*,const void*,const RECT*,const void*,DWORD*)=0;
  virtual HRESULT STDMETHODCALLTYPE SetContentProtectionManager(IMFContentProtectionManager*)=0;
  virtual HRESULT STDMETHODCALLTYPE SetApplicationCertificate(const BYTE*,DWORD)=0;
};
static const IID IID_IMFMediaEngineProtectedContent={0x9f8021e8,0x9c8c,0x487e,{0xbb,0x5c,0x79,0xaa,0x47,0x79,0x93,0x8c}};
#include <winrt/windows.media.protection.h>
#include <winrt/windows.foundation.collections.h>
#include <winrt/windows.data.json.h>
#include <fstream>
using namespace winrt;
using namespace Windows::Media::Protection;
using namespace Windows::Foundation::Collections;
struct NativeProtection : implements<NativeProtection, IMFContentProtectionManager, IMediaProtectionManager> {
  IMFContentDecryptionModule* cdm;
  PropertySet properties;
  explicit NativeProtection(PlayReadyCdm& state):cdm(state.cdm) {
    auto iid=guid_of<IMediaProtectionPMPServer>(); void* object=nullptr;
    check_hresult(state.service->GetService(MF_CONTENTDECRYPTIONMODULE_SERVICE,reinterpret_cast<GUID const&>(iid),&object));
    properties.Insert(L"Windows.Media.Protection.MediaProtectionPMPServer",MediaProtectionPMPServer{object,take_ownership_from_abi});
  }
  HRESULT STDMETHODCALLTYPE BeginEnableContent(IMFActivate* activate,IMFTopology*,IMFAsyncCallback* callback,IUnknown* state) noexcept override {
    printf("BeginEnableContent\n");
    com_ptr<IUnknown> object; com_ptr<IMFAsyncResult> result;
    HRESULT hr=MFCreateAsyncResult(nullptr,callback,state,result.put());if(FAILED(hr))return hr;
    hr=activate->ActivateObject(IID_IUnknown,object.put_void());if(FAILED(hr))return hr;
    com_ptr<IMFContentEnabler> enabler;object->QueryInterface(IID_IMFContentEnabler,enabler.put_void());
    hr=cdm->SetContentEnabler(enabler.get(),result.get());LogHr("SetContentEnabler",hr);return hr;
  }
  HRESULT STDMETHODCALLTYPE EndEnableContent(IMFAsyncResult*) noexcept override{return S_OK;}
  event_token ServiceRequested(ServiceRequestedEventHandler const&){throw hresult_not_implemented();}
  void ServiceRequested(event_token const&){}
  event_token RebootNeeded(RebootNeededEventHandler const&){throw hresult_not_implemented();}
  void RebootNeeded(event_token const&){}
  event_token ComponentLoadFailed(ComponentLoadFailedEventHandler const&){throw hresult_not_implemented();}
  void ComponentLoadFailed(event_token const&){}
  PropertySet Properties(){printf("PMP properties queried\n");return properties;}
};
struct ProtectedSource : IMFMediaSource, IMFTrustedInput, IMFGetService {
  std::atomic<ULONG> refs{1}; com_ptr<IMFMediaSource> inner;com_ptr<IMFTrustedInput> input;com_ptr<IUnknown> authority;std::mutex lock;uint64_t duration;
  ProtectedSource(IMFMediaSource* source, IMFTrustedInput* trusted, uint64_t durationMs){inner.copy_from(source);input.copy_from(trusted);duration=durationMs*10000;}
  HRESULT STDMETHODCALLTYPE QueryInterface(REFIID iid, void** value)override {
    if(iid==IID_IUnknown||iid==IID_IMFMediaSource||iid==IID_IMFMediaEventGenerator)*value=static_cast<IMFMediaSource*>(this);
    else if(iid==IID_IMFTrustedInput)*value=static_cast<IMFTrustedInput*>(this);
    else if(iid==IID_IMFGetService)*value=static_cast<IMFGetService*>(this);
    else {*value=nullptr;return E_NOINTERFACE;}AddRef();return S_OK;
  }
  ULONG STDMETHODCALLTYPE AddRef()override{return ++refs;} ULONG STDMETHODCALLTYPE Release()override{auto n=--refs;if(!n)delete this;return n;}
  // Forward rate services as well as demuxing. Without this, Media Engine accepts a rate
  // property change but the underlying protected source continues playing at 1x.
  HRESULT STDMETHODCALLTYPE GetService(REFGUID service, REFIID iid, void** value)override {
    auto provider=inner.try_as<IMFGetService>();
    if(!provider){*value=nullptr;return MF_E_UNSUPPORTED_SERVICE;}
    return provider->GetService(service,iid,value);
  }
  HRESULT STDMETHODCALLTYPE GetCharacteristics(DWORD* v)override{return inner->GetCharacteristics(v);}
  HRESULT STDMETHODCALLTYPE CreatePresentationDescriptor(IMFPresentationDescriptor** v)override{HRESULT hr=inner->CreatePresentationDescriptor(v);if(SUCCEEDED(hr))(*v)->SetUINT64(MF_PD_DURATION,duration);return hr;}
  HRESULT STDMETHODCALLTYPE Start(IMFPresentationDescriptor* p,const GUID* g,const PROPVARIANT* v)override{return inner->Start(p,g,v);}
  HRESULT STDMETHODCALLTYPE Stop()override{return inner->Stop();} HRESULT STDMETHODCALLTYPE Pause()override{return inner->Pause();} HRESULT STDMETHODCALLTYPE Shutdown()override{return inner->Shutdown();}
  HRESULT STDMETHODCALLTYPE GetEvent(DWORD f,IMFMediaEvent** e)override{return inner->GetEvent(f,e);}
  HRESULT STDMETHODCALLTYPE BeginGetEvent(IMFAsyncCallback* c,IUnknown* s)override{return inner->BeginGetEvent(c,s);}
  HRESULT STDMETHODCALLTYPE EndGetEvent(IMFAsyncResult* r,IMFMediaEvent** e)override{return inner->EndGetEvent(r,e);}
  HRESULT STDMETHODCALLTYPE QueueEvent(MediaEventType t,REFGUID g,HRESULT h,const PROPVARIANT* v)override{return inner->QueueEvent(t,g,h,v);}
  HRESULT STDMETHODCALLTYPE GetInputTrustAuthority(DWORD id,REFIID iid,IUnknown** object)override{
    std::lock_guard<std::mutex> guard(lock);
    if(!authority){HRESULT hr=input->GetInputTrustAuthority(id,iid,authority.put());LogHr("HLS GetInputTrustAuthority",hr);if(FAILED(hr))return hr;}
    return authority->QueryInterface(iid,reinterpret_cast<void**>(object));
  }
};
struct SourceExtension : implements<SourceExtension,IMFMediaEngineExtension> {
  com_ptr<IUnknown> source;
  explicit SourceExtension(IMFMediaSource* value){source.copy_from(value);}
  HRESULT STDMETHODCALLTYPE CanPlayType(BOOL,BSTR,MF_MEDIA_ENGINE_CANPLAY* result) noexcept override{*result=MF_MEDIA_ENGINE_CANPLAY_NOT_SUPPORTED;return S_OK;}
  HRESULT STDMETHODCALLTYPE BeginCreateObject(BSTR,IMFByteStream*,MF_OBJECT_TYPE type,IUnknown** cancel,IMFAsyncCallback* callback,IUnknown* state) noexcept override {
    printf("BeginCreateObject type=%d\n",int(type));if(cancel)*cancel=nullptr;if(type!=MF_OBJECT_MEDIASOURCE)return MF_E_UNEXPECTED;
    com_ptr<IMFAsyncResult> result;HRESULT hr=MFCreateAsyncResult(source.get(),callback,state,result.put());
    if(FAILED(hr))return hr;result->SetStatus(S_OK);return callback->Invoke(result.get());
  }
  HRESULT STDMETHODCALLTYPE CancelObjectCreation(IUnknown*) noexcept override{return S_OK;}
  HRESULT STDMETHODCALLTYPE EndCreateObject(IMFAsyncResult* result,IUnknown** object) noexcept override{return result->GetObject(object);}
};
struct EngineNotify : implements<EngineNotify,IMFMediaEngineNotify> {
  std::atomic<HRESULT> error{S_OK};std::atomic<bool> ended{false};
  HRESULT STDMETHODCALLTYPE EventNotify(DWORD event,DWORD_PTR p1,DWORD p2) noexcept override {
    if(event==MF_MEDIA_ENGINE_EVENT_NOTIFYSTABLESTATE)SetEvent(reinterpret_cast<HANDLE>(p1));
    else if(event==MF_MEDIA_ENGINE_EVENT_ERROR)error=static_cast<HRESULT>(p2);
    else if(event==MF_MEDIA_ENGINE_EVENT_ENDED)ended=true;
    return S_OK;
  }
};

namespace fs=std::filesystem;
static int64_t nowMs(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();}
static Windows::Data::Json::JsonObject readJson(fs::path const& path){
  HANDLE f=CreateFileW(path.c_str(),GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,nullptr,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,nullptr);
  if(f==INVALID_HANDLE_VALUE)throw hresult_error(E_PENDING);
  LARGE_INTEGER size{};if(!GetFileSizeEx(f,&size)||size.QuadPart>128*1024){CloseHandle(f);throw hresult_invalid_argument();}
  std::string text(static_cast<size_t>(size.QuadPart),'\0');DWORD read=0;
  BOOL ok=ReadFile(f,text.data(),static_cast<DWORD>(text.size()),&read,nullptr);CloseHandle(f);
  if(!ok||read!=text.size())throw hresult_error(E_PENDING);
  return Windows::Data::Json::JsonObject::Parse(to_hstring(text));
}
static void writeJson(fs::path const& path,Windows::Data::Json::JsonObject const& value){
  auto temp=path;temp+=L".tmp";{std::ofstream f(temp,std::ios::binary);f<<to_string(value.Stringify());}
  MoveFileExW(temp.c_str(),path.c_str(),MOVEFILE_REPLACE_EXISTING);
}
struct Renderer {
  PlayReadyCdm cdm;
  com_ptr<IMFMediaEngine> engine;
  com_ptr<IMFMediaEngineEx> controls;
  com_ptr<IMFMediaSource> source;
  com_ptr<SourceExtension> extension;
  com_ptr<NativeProtection> protection;
  com_ptr<EngineNotify> notify;
  Renderer(fs::path const& directory, Windows::Data::Json::JsonObject const& command){
    fs::create_directories(directory/L"cdm");
    check_hresult(CreatePlayReadyCdm(&cdm,(directory/L"cdm").wstring()));
    std::ifstream init(directory/L"initData.bin",std::ios::binary);std::vector<uint8_t> pssh((std::istreambuf_iterator<char>(init)),{});
    std::vector<uint8_t> license;check_hresult(AcquireLicense(&cdm,pssh,std::wstring(command.GetNamedString(L"licenseAuthToken")),&license));
    if(!KeysUsable(&cdm,&cdm.key_id))throw hresult_error(MF_E_LICENSE_INCORRECT_RIGHTS);
    check_hresult(cdm.cdm->CreateTrustedInput(nullptr,0,&cdm.trusted_input));
    com_ptr<IMFSourceResolver> resolver;check_hresult(MFCreateSourceResolver(resolver.put()));
    com_ptr<IUnknown> resolved;MF_OBJECT_TYPE type;
    auto media=std::wstring(command.GetNamedString(L"mediaFile"));
    if(media.find(L"/")!=std::wstring::npos||media.find(L"\\")!=std::wstring::npos||media.find(L"..")!=std::wstring::npos)throw hresult_invalid_argument();
    auto file=fs::absolute(directory/media).wstring();
    check_hresult(resolver->CreateObjectFromURL(file.c_str(),MF_RESOLUTION_MEDIASOURCE,nullptr,&type,resolved.put()));
    auto inner=resolved.as<IMFMediaSource>();
    source.attach(new ProtectedSource(inner.get(),cdm.trusted_input,static_cast<uint64_t>(command.GetNamedNumber(L"durationMs"))));
    extension=make_self<SourceExtension>(source.get());notify=make_self<EngineNotify>();protection=make_self<NativeProtection>(cdm);
    com_ptr<IMFAttributes> attrs;check_hresult(MFCreateAttributes(attrs.put(),5));
    check_hresult(attrs->SetUnknown(MF_MEDIA_ENGINE_CALLBACK,notify.get()));
    check_hresult(attrs->SetUnknown(MF_MEDIA_ENGINE_EXTENSION,extension.get()));
    check_hresult(attrs->SetUINT32(MF_MEDIA_ENGINE_CONTENT_PROTECTION_FLAGS,1));
    GUID key={0x4e0212e2,0xe18f,0x41e1,{0x95,0xe5,0xc0,0xe7,0xe9,0x23,0x5b,0xc3}};
    GUID value={0xa6f3e465,0x3aca,0x442c,{0xa3,0xf0,0xad,0x6d,0xda,0xd8,0x39,0xae}};
    check_hresult(attrs->SetGUID(key,value));
    com_ptr<IMFMediaEngineClassFactory> factory;check_hresult(CoCreateInstance(CLSID_MFMediaEngineClassFactory,nullptr,CLSCTX_INPROC_SERVER,IID_IMFMediaEngineClassFactory,factory.put_void()));
    check_hresult(factory->CreateInstance(0,attrs.get(),engine.put()));
    check_hresult(engine->QueryInterface(IID_IMFMediaEngineEx,controls.put_void()));
    com_ptr<IMFMediaEngineProtectedContent> protectedEngine;check_hresult(engine->QueryInterface(IID_IMFMediaEngineProtectedContent,protectedEngine.put_void()));
    check_hresult(protectedEngine->SetContentProtectionManager(protection.get()));
    engine->SetVolume(command.GetNamedNumber(L"volume",1));
    check_hresult(engine->SetDefaultPlaybackRate(command.GetNamedNumber(L"rate",1)));
    BSTR url=SysAllocString(L"customSrc");HRESULT hr=engine->SetSource(url);SysFreeString(url);check_hresult(hr);
  }
  ~Renderer(){if(engine)engine->Shutdown();if(source)source->Shutdown();}
};
int wmain(int argc,wchar_t** argv){
  if(argc!=2)return 1;
  fs::path directory=argv[1];
  std::unique_ptr<Renderer> renderer;
  std::string error;
  try{
    init_apartment(apartment_type::multi_threaded);check_hresult(MFStartup(MF_VERSION));
    DWORD appContainer=0,returned=0;HANDLE token=nullptr;
    if(OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token)){GetTokenInformation(token,TokenIsAppContainer,&appContainer,sizeof(appContainer),&returned);CloseHandle(token);}
    UINT32 packageLength=0;bool packaged=GetCurrentPackageFullName(&packageLength,nullptr)!=APPMODEL_ERROR_NO_PACKAGE;
    int64_t seekRevision=-1;std::string activeToken;
    Windows::Data::Json::JsonObject command;
    int ticks=0;double panPhase=0,gatePhase=0;
    auto previousTick=std::chrono::steady_clock::now();
    for(;;){
      if(ticks%10==0)command=readJson(directory/L"command.json");
      auto tick=std::chrono::steady_clock::now();
      double elapsed=std::chrono::duration<double>(tick-previousTick).count();previousTick=tick;
      if(command.GetNamedBoolean(L"closed",false)||nowMs()-static_cast<int64_t>(command.GetNamedNumber(L"updatedAt"))>10000)break;
      auto token=to_string(command.GetNamedString(L"licenseAuthToken"));
      if(!renderer||token!=activeToken){renderer.reset();renderer=std::make_unique<Renderer>(directory,command);activeToken=token;seekRevision=-1;}
      auto& e=renderer->engine;
      check_hresult(renderer->notify->error.load());
      double rate=command.GetNamedNumber(L"rate",1);
      if(std::abs(e->GetPlaybackRate()-rate)>0.0001)check_hresult(e->SetPlaybackRate(rate));
      bool eightD=command.GetNamedBoolean(L"eightD",false);
      bool gate=command.GetNamedBoolean(L"tranceGate",false);
      if(!e->IsPaused()){
        panPhase+=elapsed*44100*(0.000002+command.GetNamedNumber(L"eightDSpeed",0.5)*0.000038);
        gatePhase+=elapsed*6.283185307179586*(1+command.GetNamedNumber(L"gateSpeed",0.5)*13);
      }
      if(!eightD)panPhase=0;if(!gate)gatePhase=0;
      double pan=eightD?std::sin(panPhase):0;
      double gain=1;
      if(gate){double sine=std::sin(gatePhase),pattern=command.GetNamedNumber(L"gatePattern",0.85),mix=command.GetNamedNumber(L"gateMix",0.9);
        gain=(1-mix)+mix*(((sine+1)*0.5)*(1-pattern)+(sine>=0?1:0)*pattern);
      }
      // Match the stereo gains of the PCM 8D processor using Windows volume/balance controls.
      double panGain=eightD?(1+std::abs(pan))/2:1;
      check_hresult(renderer->controls->SetBalance(pan));
      check_hresult(e->SetVolume(command.GetNamedNumber(L"volume",1)*gain*panGain));
      auto revision=static_cast<int64_t>(command.GetNamedNumber(L"seekRevision"));
      bool ready=e->GetReadyState()>=2;
      if(ready&&revision!=seekRevision){
        auto position=command.GetNamedNumber(L"positionMs")/1000.;
        if(position>0||seekRevision>=0)check_hresult(e->SetCurrentTime(position));
        renderer->notify->ended=false;seekRevision=revision;
      }
      bool playing=command.GetNamedBoolean(L"playing",false);
      if(playing&&e->IsPaused()&&!renderer->notify->ended.load())check_hresult(e->Play());
      if(!playing&&!e->IsPaused())check_hresult(e->Pause());
      if(ticks++%10==0){
      Windows::Data::Json::JsonObject status;
      status.SetNamedValue(L"playbackRate",Windows::Data::Json::JsonValue::CreateNumberValue(e->GetPlaybackRate()));
      status.SetNamedValue(L"balance",Windows::Data::Json::JsonValue::CreateNumberValue(renderer->controls->GetBalance()));
      status.SetNamedValue(L"outputVolume",Windows::Data::Json::JsonValue::CreateNumberValue(e->GetVolume()));
      status.SetNamedValue(L"appContainer",Windows::Data::Json::JsonValue::CreateBooleanValue(appContainer!=0));
      status.SetNamedValue(L"hasPackageIdentity",Windows::Data::Json::JsonValue::CreateBooleanValue(packaged));
      status.SetNamedValue(L"updatedAt",Windows::Data::Json::JsonValue::CreateNumberValue(nowMs()));
      status.SetNamedValue(L"positionMs",Windows::Data::Json::JsonValue::CreateNumberValue(e->GetCurrentTime()*1000));
      status.SetNamedValue(L"durationMs",Windows::Data::Json::JsonValue::CreateNumberValue(command.GetNamedNumber(L"durationMs")));
      status.SetNamedValue(L"state",Windows::Data::Json::JsonValue::CreateNumberValue(ready?(e->IsPaused()?4:3):1));
      status.SetNamedValue(L"ended",Windows::Data::Json::JsonValue::CreateBooleanValue(renderer->notify->ended.load()));
      status.SetNamedValue(L"error",Windows::Data::Json::JsonValue::CreateStringValue(L""));
      writeJson(directory/L"status.json",status);}
      Sleep(10);
    }
  }catch(hresult_error const& e){char code[32];snprintf(code,sizeof(code),"PlayReady 0x%08x",unsigned(e.code().value));error=code;}
   catch(...){error="PlayReady native initialization failed";}
  renderer.reset();
  try {
    auto command=readJson(directory/L"command.json");
    command.SetNamedValue(L"licenseAuthToken",Windows::Data::Json::JsonValue::CreateStringValue(L""));
    command.SetNamedValue(L"url",Windows::Data::Json::JsonValue::CreateStringValue(L""));
    command.SetNamedValue(L"closed",Windows::Data::Json::JsonValue::CreateBooleanValue(true));
    writeJson(directory/L"command.json",command);
    for(auto const& entry:fs::directory_iterator(directory))if(entry.path().extension()==L".mp4"){std::error_code ignored;fs::remove(entry.path(),ignored);}
  }catch(...){}
  if(!error.empty()){
    try{Windows::Data::Json::JsonObject status;status.SetNamedValue(L"updatedAt",Windows::Data::Json::JsonValue::CreateNumberValue(nowMs()));status.SetNamedValue(L"error",Windows::Data::Json::JsonValue::CreateStringValue(to_hstring(error)));writeJson(directory/L"status.json",status);}catch(...){}
  }
  return error.empty()?0:2;
}
