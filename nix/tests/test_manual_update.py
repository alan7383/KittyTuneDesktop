import base64
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


class ManualUpdateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="kittytune updater ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        repo = Path(__file__).resolve().parents[2]
        for name in ("scripts/update-nix.sh", "nix/update-release.py", "nix/package.nix"):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(repo / name, target)
        self.package = self.root / "nix/package.nix"
        self.original = self.package.read_text()
        version = re.search(r'version = "([0-9.]+)";', self.original).group(1)
        major, minor, patch = map(int, version.split("."))
        self.version = f"{major}.{minor}.{patch + 1}"
        self.release = {
            "tag_name": "v" + self.version,
            "assets": [
                {"name": f"kitty-tune_{self.version}_{arch}.deb", "state": "uploaded", "digest": "sha256:" + digest}
                for arch, digest in (("amd64", "11" * 32), ("arm64", "22" * 32))
            ],
        }
        self.stub = self.root / "stub"
        self.stub.mkdir()
        for name, text in {
            "curl": '#!/bin/sh\nprintf "%s\\n" "$@" > "$KITTYTUNE_TEST_REQUEST"\n'
                    'if [ "${KITTYTUNE_TEST_HTTP_FAIL:-}" = 1 ]; then exit 22; fi\n'
                    'cat "$KITTYTUNE_TEST_METADATA"\n',
            "nix-prefetch-url": "#!/bin/sh\nexit 1\n",
        }.items():
            path = self.stub / name
            path.write_bytes(text.encode())
            path.chmod(0o755)

    def run_update(self, *args, http_fail=False):
        metadata = self.root / "release.json"
        metadata.write_text(json.dumps(self.release))
        env = os.environ.copy()
        env.update(
            PATH=str(self.stub) + os.pathsep + env.get("PATH", ""),
            PYTHON=Path(sys.executable).as_posix(),
            KITTYTUNE_TEST_METADATA=metadata.as_posix(),
            KITTYTUNE_TEST_REQUEST=(self.root / "request").as_posix(),
            KITTYTUNE_TEST_HTTP_FAIL="1" if http_fail else "0",
        )
        shell = env.get("KITTYTUNE_TEST_BASH") or shutil.which("bash")
        self.assertIsNotNone(shell, "Install bash to run updater tests")
        stub_path = self.stub.as_posix()
        if os.name == "nt":
            stub_path = subprocess.check_output(
                [shell, "-c", 'cygpath -u "$1"', "path", str(self.stub)], text=True).strip()
        env["KITTYTUNE_TEST_STUB"] = stub_path
        return subprocess.run([shell, "-c", 'export PATH="$KITTYTUNE_TEST_STUB:$PATH"; exec "$BASH" "$@"',
                               "updater-test", str(self.root / "scripts/update-nix.sh"), *args],
                              env=env, capture_output=True, text=True, timeout=15)

    def assert_updated(self):
        updated = self.package.read_text()
        self.assertIn(f'version = "{self.version}";', updated)
        for byte in (0x11, 0x22):
            self.assertIn("sha256-" + base64.b64encode(bytes([byte]) * 32).decode(), updated)

    def test_explicit_version_updates_both_hashes(self):
        for prefix in ("", "v"):
            with self.subTest(prefix=prefix):
                self.package.write_bytes(self.original.encode())
                result = self.run_update(prefix + self.version)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assert_updated()
                self.assertIn("/releases/tags/v" + self.version, (self.root / "request").read_text())

    def test_latest_release_updates_both_hashes(self):
        result = self.run_update()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_updated()
        self.assertIn("/releases/latest", (self.root / "request").read_text())

    def test_http_failure_does_not_modify_package(self):
        self.assertNotEqual(0, self.run_update(self.version, http_fail=True).returncode)
        self.assertEqual(self.original, self.package.read_text())

    def test_missing_asset_does_not_modify_package(self):
        self.release["assets"].pop()
        self.assertNotEqual(0, self.run_update(self.version).returncode)
        self.assertEqual(self.original, self.package.read_text())

    def test_invalid_digest_does_not_modify_package(self):
        self.release["assets"][1]["digest"] = "invalid"
        self.assertNotEqual(0, self.run_update(self.version).returncode)
        self.assertEqual(self.original, self.package.read_text())

    def test_invalid_version_is_rejected_before_request(self):
        self.assertNotEqual(0, self.run_update("invalid-version").returncode)
        self.assertFalse((self.root / "request").exists())
        self.assertEqual(self.original, self.package.read_text())


if __name__ == "__main__":
    unittest.main()
