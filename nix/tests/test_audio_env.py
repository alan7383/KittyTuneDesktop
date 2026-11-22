import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import unittest


class AudioEnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="kittytune audio ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.home = self.root / "home"
        self.home.mkdir()
        self.fallback = self.root / "fallback.conf"
        self.fallback.write_text("pcm.!default { type pulse }\n")
        self.server = self.root / "PulseServer"
        self.system_config = self.root / "system-asound.conf"
        self.plugins = (self.root / "store-plugins").as_posix()
        hook = os.environ.get("KITTYTUNE_TEST_AUDIO_HOOK")
        source = Path(hook or Path(__file__).resolve().parents[1] / "audio-env.sh").read_text()
        config = re.search(r'export ALSA_CONFIG_PATH="([^"]+)"', source).group(1)
        plugins = re.search(r'export ALSA_PLUGIN_DIR="([^"]+)"', source).group(1)
        source = source.replace(config, self.fallback.as_posix())
        source = source.replace(plugins, self.plugins)
        source = source.replace("/mnt/wslg/PulseServer", shlex.quote(self.server.as_posix()))
        source = source.replace("/etc/asound.conf", shlex.quote(self.system_config.as_posix()))
        self.hook = self.root / "audio-env.sh"
        self.hook.write_bytes(source.encode())

    def environment(self, **settings):
        env = os.environ.copy()
        for key in ("ALSA_CONFIG_PATH", "ALSA_PLUGIN_DIR", "PULSE_SERVER"):
            env.pop(key, None)
        env["HOME"] = self.home.as_posix()
        env.update(settings)
        shell = os.environ.get("KITTYTUNE_TEST_SH") or shutil.which("sh")
        self.assertIsNotNone(shell, "Install a POSIX shell to run audio wrapper tests")
        result = subprocess.run(
            [shell, "-c", f'. {shlex.quote(self.hook.as_posix())}; '
             'printf "%s\\n" "${ALSA_CONFIG_PATH-}" "${ALSA_PLUGIN_DIR-}" "${PULSE_SERVER-}"'],
            env=env, capture_output=True, text=True, timeout=10, check=True,
        )
        self.assertEqual("", result.stderr)
        return dict(zip(("config", "plugins", "pulse"), result.stdout.splitlines(), strict=True))

    def test_explicit_configuration_survives_without_conventional_files(self):
        custom = self.root / "custom.conf"
        custom.write_text("pcm.!default { type null }\n")
        self.assertEqual(custom.as_posix(), self.environment(ALSA_CONFIG_PATH=custom.as_posix())["config"])

    def test_native_linux_keeps_alsa_defaults(self):
        actual = self.environment()
        self.assertEqual("", actual["config"])
        self.assertEqual("", actual["pulse"])
        self.assertEqual(self.plugins, actual["plugins"])

    def test_wslg_fallback_is_available_without_audio_configuration(self):
        self.server.touch()
        actual = self.environment()
        self.assertEqual(self.fallback.as_posix(), actual["config"])
        self.assertEqual("unix:" + self.server.as_posix(), actual["pulse"])

    def test_wslg_preserves_explicit_alsa_and_pulse_routing(self):
        self.server.touch()
        actual = self.environment(ALSA_CONFIG_PATH="/custom/audio.conf", PULSE_SERVER="tcp:audio-host")
        self.assertEqual("/custom/audio.conf", actual["config"])
        self.assertEqual("tcp:audio-host", actual["pulse"])

    def test_system_configuration_prevents_fallback(self):
        self.server.touch()
        self.system_config.touch()
        self.assertEqual("", self.environment()["config"])

    def test_user_configuration_prevents_fallback(self):
        self.server.touch()
        (self.home / ".asoundrc").touch()
        self.assertEqual("", self.environment()["config"])

    def test_missing_fallback_is_not_exported(self):
        self.server.touch()
        self.fallback.unlink()
        self.assertEqual("", self.environment()["config"])

    def test_plugin_override_is_a_single_directory(self):
        actual = self.environment(ALSA_PLUGIN_DIR="/custom/alsa-plugins")
        self.assertEqual("/custom/alsa-plugins", actual["plugins"])

    def test_empty_overrides_use_defaults(self):
        self.server.touch()
        actual = self.environment(ALSA_PLUGIN_DIR="", ALSA_CONFIG_PATH="", PULSE_SERVER="")
        self.assertEqual(self.plugins, actual["plugins"])
        self.assertEqual(self.fallback.as_posix(), actual["config"])


if __name__ == "__main__":
    unittest.main()
