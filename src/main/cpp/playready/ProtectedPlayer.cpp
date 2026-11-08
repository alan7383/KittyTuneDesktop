// Native protected playback. Audio stays inside Windows' PlayReady/PMP pipeline.
// The desktop app exchanges transport commands and status through per-session files;
// no browser, third-party app, content key or decrypted samples are involved.
#include <windows.h>
#include <winrt/windows.foundation.h>
#include <winrt/windows.foundation.collections.h>
#include <winrt/windows.data.json.h>
#include <winrt/windows.media.core.h>
#include <winrt/windows.media.playback.h>
#include <winrt/windows.media.protection.h>
#include <winrt/windows.media.protection.playready.h>
#include <winrt/windows.storage.h>
#include <winrt/windows.applicationmodel.core.h>
#include <winrt/windows.applicationmodel.activation.h>
#include <winrt/windows.ui.core.h>
#include <filesystem>
#include <fstream>
#include <atomic>
#include <map>
#include <memory>
#include <mutex>
#include <thread>

using namespace winrt;
using namespace Windows::Foundation;
using namespace Windows::Foundation::Collections;
using namespace Windows::Data::Json;
using namespace Windows::Media::Playback;
using namespace Windows::Media::Protection;
using namespace Windows::Media::Protection::PlayReady;
namespace fs = std::filesystem;

static int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
}
static JsonObject readJson(fs::path const& path) {
    std::ifstream file(path, std::ios::binary);
    // The JVM creates a session directory before publishing its first atomic command.
    // Seeing that directory must not publish a permanent JSON error to the new engine.
    if (!file) throw std::runtime_error("Command not published yet");
    std::string text((std::istreambuf_iterator<char>(file)), {});
    if (text.size() > 128 * 1024) throw hresult_invalid_argument();
    JsonObject json;
    if (!JsonObject::TryParse(to_hstring(text), json)) throw std::runtime_error("Command not ready");
    return json;
}
static void writeJson(fs::path const& path, JsonObject const& json) {
    auto temp = path; temp += L".tmp";
    { std::ofstream file(temp, std::ios::binary | std::ios::trunc); file << to_string(json.Stringify()); }
    // Atomic replacement also lets the JVM keep reading while we publish the next status.
    if (!MoveFileExW(temp.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING)) {
        std::error_code ignored; fs::remove(temp, ignored);
    }
}
struct PlaybackResult {
    std::mutex mutex;
    hstring token;
    std::string error;
    bool ended = false;
    void fail(hresult code, std::string const& phase) {
        char hex[16]; snprintf(hex, sizeof(hex), "%08x", unsigned(code.value));
        std::scoped_lock lock(mutex);
        // Never include a request URI, token or response body in IPC diagnostics.
        error = phase + " (0x" + hex + ")";
    }
};
static fire_and_forget service(std::shared_ptr<PlaybackResult> result, ServiceRequestedEventArgs args) {
    auto request = args.Request().try_as<IPlayReadyServiceRequest>();
    if (!request) { args.Completion().Complete(false); co_return; }
    try {
        if (args.Request().try_as<PlayReadyLicenseAcquisitionServiceRequest>()) {
            request.Uri(Uri(L"https://license.media-streaming.soundcloud.cloud/playback/playready?license_token=" + result->token));
        }
        // Includes Windows individualization on first use. Microsoft owns the challenge,
        // license processing, protected decoding and rendering.
        co_await request.BeginServiceRequest();
        args.Completion().Complete(true);
    } catch (hresult_error const& error) {
        result->fail(error.code(), "PlayReady service failed");
        args.Completion().Complete(false);
    }
}
struct Session {
    MediaPlayer player{nullptr};
    std::shared_ptr<PlaybackResult> result = std::make_shared<PlaybackResult>();
    hstring url;
    int64_t seekRevision = -1;
    int64_t pendingSeek = 0;
    bool seekPending = true;
    bool playing = false;
    void open(JsonObject const& command) {
        result->token = command.GetNamedString(L"licenseAuthToken");
        url = command.GetNamedString(L"url");
        // The exact string-valued mapping used by Windows' sample and SoundCloud's UWP app.
        auto manager = MediaProtectionManager();
        auto mapping = PropertySet();
        hstring const system = L"{F4637010-03C3-42CD-B932-B48ADF3A6A54}";
        mapping.Insert(system, box_value(L"Windows.Media.Protection.PlayReady.PlayReadyWinRTTrustedInput"));
        manager.Properties().Insert(L"Windows.Media.Protection.MediaProtectionSystemIdMapping", mapping);
        manager.Properties().Insert(L"Windows.Media.Protection.MediaProtectionSystemId", box_value(system));
        manager.Properties().Insert(L"Windows.Media.Protection.MediaProtectionContainerGuid", box_value(L"{9A04F079-9840-4286-AB92-E65BE0885F95}"));
        manager.Properties().Insert(L"Windows.Media.Protection.UseSoftwareProtectionLayer", box_value(true));
        auto state = result;
        manager.ServiceRequested([state](auto const&, auto const& args) { service(state, args); });
        manager.ComponentLoadFailed([state](auto const&, auto const& args) {
            state->fail(E_FAIL, "PlayReady component failed"); args.Completion().Complete(false);
        });
        player = MediaPlayer();
        player.CommandManager().IsEnabled(false); // KittyTune's existing SMTC session owns media keys.
        player.ProtectionManager(manager);
        player.AutoPlay(false);
        player.MediaFailed([state](auto const&, auto const& args) { state->fail(args.ExtendedErrorCode(), "Protected playback failed"); });
        player.MediaEnded([state](auto const&, auto const&) { std::scoped_lock lock(state->mutex); state->ended = true; });
        player.Volume(command.GetNamedNumber(L"volume", 1));
        player.Source(Windows::Media::Core::MediaSource::CreateFromUri(Uri(url)));
    }
    void update(JsonObject const& command) {
        player.Volume(std::clamp(command.GetNamedNumber(L"volume", 1), 0.0, 1.0));
        player.PlaybackSession().PlaybackRate(std::clamp(command.GetNamedNumber(L"rate", 1), 0.5, 2.0));
        auto revision = int64_t(command.GetNamedNumber(L"seekRevision", 0));
        if (revision != seekRevision) {
            seekRevision = revision;
            pendingSeek = std::max(int64_t(0), int64_t(command.GetNamedNumber(L"positionMs", 0)));
            seekPending = true;
            std::scoped_lock lock(result->mutex); result->ended = false;
        }
        if (seekPending && player.PlaybackSession().NaturalDuration().count() > 0) {
            player.PlaybackSession().Position(std::chrono::duration_cast<TimeSpan>(std::chrono::milliseconds(pendingSeek)));
            seekPending = false;
        }
        auto requestedPlaying = command.GetNamedBoolean(L"playing", false);
        if (playing != requestedPlaying) {
            playing = requestedPlaying;
            if (playing) player.Play(); else player.Pause();
        }
    }
    JsonObject status() {
        auto playback = player.PlaybackSession();
        JsonObject status;
        status.Insert(L"updatedAt", JsonValue::CreateNumberValue(double(nowMs())));
        status.Insert(L"positionMs", JsonValue::CreateNumberValue(double(playback.Position().count() / 10000)));
        status.Insert(L"durationMs", JsonValue::CreateNumberValue(double(playback.NaturalDuration().count() / 10000)));
        status.Insert(L"state", JsonValue::CreateNumberValue(int(playback.PlaybackState())));
        std::scoped_lock lock(result->mutex);
        status.Insert(L"error", JsonValue::CreateStringValue(to_hstring(result->error)));
        status.Insert(L"ended", JsonValue::CreateBooleanValue(result->ended));
        return status;
    }
    ~Session() { if (player) { player.Pause(); player.Close(); } }
};
struct Controller {
    fs::path root;
    std::map<fs::path, std::unique_ptr<Session>> sessions;
    int64_t lastActive = nowMs();
    static bool validId(std::wstring const& name) {
        return name.size() == 36 && std::all_of(name.begin(), name.end(), [](wchar_t c) { return (c >= L'0' && c <= L'9') || (c >= L'a' && c <= L'f') || c == L'-'; });
    }
    void remove(fs::path const& directory) {
        sessions.erase(directory);
        // Only remove our known files; never recursively delete a directory from IPC input.
        std::error_code ignored;
        for (auto name : {L"command.json", L"status.json", L"command.json.tmp", L"status.json.tmp"}) fs::remove(directory / name, ignored);
        fs::remove(directory, ignored);
    }
    void tick() {
        JsonObject alive;
        alive.Insert(L"updatedAt", JsonValue::CreateNumberValue(double(nowMs())));
        writeJson(root / L"alive.json", alive);
        std::error_code ignored;
        for (auto const& entry : fs::directory_iterator(root, ignored)) {
            if (!entry.is_directory() || !validId(entry.path().filename().wstring())) continue;
            auto directory = entry.path();
            try {
                auto command = readJson(directory / L"command.json");
                auto age = nowMs() - int64_t(command.GetNamedNumber(L"updatedAt", 0));
                if (command.GetNamedBoolean(L"closed", false) || age > 10000) { remove(directory); continue; }
                auto& session = sessions[directory];
                if (!session || session->url != command.GetNamedString(L"url")) {
                    session = std::make_unique<Session>(); session->open(command);
                }
                session->update(command);
                writeJson(directory / L"status.json", session->status());
            } catch (hresult_error const& error) {
                JsonObject status;
                auto result = PlaybackResult(); result.fail(error.code(), "Native player failed");
                status.Insert(L"error", JsonValue::CreateStringValue(to_hstring(result.error)));
                status.Insert(L"updatedAt", JsonValue::CreateNumberValue(double(nowMs())));
                writeJson(directory / L"status.json", status);
                sessions.erase(directory);
            } catch (std::exception const&) { /* A file being replaced is retried on the next tick. */ }
        }
        if (!sessions.empty()) lastActive = nowMs();
        else if (nowMs() - lastActive > 60000) Windows::ApplicationModel::Core::CoreApplication::Exit();
    }
};
struct View : implements<View, Windows::ApplicationModel::Core::IFrameworkView> {
    Windows::UI::Core::CoreWindow window{nullptr};
    void Initialize(Windows::ApplicationModel::Core::CoreApplicationView const& view) {
        view.Activated([](auto const&, auto const&) { Windows::UI::Core::CoreWindow::GetForCurrentThread().Activate(); });
    }
    void SetWindow(Windows::UI::Core::CoreWindow const& value) { window = value; }
    void Load(hstring const&) {}
    void Run() {
        auto controller = std::make_shared<Controller>();
        controller->root = fs::path(Windows::Storage::ApplicationData::Current().LocalFolder().Path().c_str()) / L"bridge";
        fs::create_directories(controller->root);
        auto dispatcher = window.Dispatcher();
        auto pending = std::make_shared<std::atomic<bool>>(false);
        std::jthread worker([controller, dispatcher, pending](std::stop_token stop) {
            init_apartment(apartment_type::multi_threaded);
            while (!stop.stop_requested()) {
                try {
                    if (!pending->exchange(true)) dispatcher.RunAsync(Windows::UI::Core::CoreDispatcherPriority::Normal,
                        [controller, pending] { controller->tick(); pending->store(false); });
                }
                catch (...) { break; }
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
        });
        window.Dispatcher().ProcessEvents(Windows::UI::Core::CoreProcessEventsOption::ProcessUntilQuit);
    }
    void Uninitialize() {}
};
struct ViewSource : implements<ViewSource, Windows::ApplicationModel::Core::IFrameworkViewSource> {
    Windows::ApplicationModel::Core::IFrameworkView CreateView() { return make<View>(); }
};
int main() {
    init_apartment(apartment_type::multi_threaded);
    // UWP passes activation arguments: they must never be treated as a console probe invocation.
    Windows::ApplicationModel::Core::CoreApplication::Run(make<ViewSource>());
}
