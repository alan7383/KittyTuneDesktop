import threading
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = Path(os.environ.get('KITTYTUNE_CACHE_SCRIPT', ROOT/'scripts/optimize-windows-startup.ps1'))
@unittest.skipUnless(os.name == 'nt', 'Windows helper')
class StartupCacheTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tools = tempfile.TemporaryDirectory()
        cls.sdk = Path(cls.tools.name) / 'sdk'
        (cls.sdk / 'bin').mkdir(parents=True)
        compiler = Path(os.environ['WINDIR']) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
        subprocess.run([str(compiler), '/nologo', '/out:' + str(cls.sdk/'bin/java.exe'),
                        str(ROOT/'scripts/tests/CacheJvmFixture.cs')], check=True, creationflags=subprocess.CREATE_NO_WINDOW)
    @classmethod
    def tearDownClass(cls): cls.tools.cleanup()
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.image = self.root/'image Ω music'
        (self.image/'app').mkdir(parents=True)
        (self.image/'runtime/bin').mkdir(parents=True)
        (self.image/'app/main.jar').write_bytes(b'fixture')
        self.cfg = self.image/'app/KittyTune.cfg'
        self.original = '[Application]\napp.mainclass=com.alananasss.kittytune.MainKt\napp.classpath=$APPDIR/main.jar\n[JavaOptions]\njava-options=-Xmx512m\n'
        self.cfg.write_bytes(self.original.encode())
        self.ready = self.root/'holder.pid'
    def tearDown(self): self.temp.cleanup()
    def run_helper(self, extra=(), **flags):
        env=os.environ.copy()
        env.update(flags,FAKE_LOCK_READY=str(self.ready))
        release=threading.Event()
        def hold_file():
            deadline=time.monotonic()+10
            while not release.is_set() and time.monotonic()<deadline:
                try:
                    path=Path(self.ready.read_text(encoding='utf-8-sig'))
                    if path.is_file() and path.resolve().is_relative_to(self.image.resolve()):
                        with path.open('rb'):
                            Path(str(self.ready)+'.locked').write_text('ready')
                            release.wait(30)
                        return
                except (OSError,UnicodeDecodeError): pass
                time.sleep(.01)
        worker=threading.Thread(target=hold_file,daemon=True)
        if flags.get('FAKE_HOLD')=='1': worker.start()
        before=time.monotonic()
        try:
            result=subprocess.run(['pwsh.exe','-NoProfile','-File',str(SCRIPT),
                                   '-AppImage',str(self.image),'-JavaHome',str(self.sdk),*extra],
                                  env=env,capture_output=True,text=True,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
        finally:
            release.set()
            if worker.is_alive(): worker.join(5)
        return result,time.monotonic()-before
    def test_locked_cleanup_does_not_mask_success(self):
        result,elapsed=self.run_helper(FAKE_HOLD='1')
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        self.assertEqual('fresh-cache',(self.image/'app/kittytune-startup.aot').read_text())
        self.assertIn('java-options=-XX:AOTCache=$APPDIR/kittytune-startup.aot',self.cfg.read_text())
    def test_failed_assembly_preserves_existing_install(self):
        cache=self.image/'app/kittytune-startup.aot'; cache.write_text('previous-cache')
        result,_=self.run_helper(FAKE_FAIL='1')
        self.assertNotEqual(0,result.returncode)
        self.assertIn('assembly fixture failure',result.stdout+result.stderr)
        self.assertEqual('previous-cache',cache.read_text())
        self.assertEqual(self.original,self.cfg.read_text())
    def test_long_arguments_and_unicode_paths_survive(self):
        long_option='-Dfixture.long='+'x'*40000
        self.cfg.write_bytes((self.original+'java-options='+long_option+'\n').encode('utf-8'))
        log=self.root/'arguments.txt'
        result,_=self.run_helper(FAKE_ARGUMENT_LOG=str(log))
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        arguments=log.read_text(encoding='utf-8-sig').splitlines()
        self.assertIn(long_option,arguments)
        self.assertTrue(any(str(self.image) in argument for argument in arguments))
        home = Path(next(arg.removeprefix('-Duser.home=') for arg in arguments if arg.startswith('-Duser.home=')))
        temp = Path(next(arg.removeprefix('-Djava.io.tmpdir=') for arg in arguments if arg.startswith('-Djava.io.tmpdir=')))
        self.assertEqual(Path(tempfile.gettempdir()).resolve(), home.parent.parent.resolve())
        self.assertEqual(home.parent / 'temp', temp)
        self.assertFalse(home.parent.exists(), 'The temporary JVM profile must be cleaned up')
        self.assertIn('java-options=-XX:AOTCache=',self.cfg.read_text())
    def test_timeout_keeps_partial_diagnostics(self):
        result,elapsed=self.run_helper(('-TrainingTimeoutSeconds','1'),FAKE_DELAY='1')
        self.assertNotEqual(0,result.returncode)
        self.assertLess(elapsed,6)
        self.assertIn('timed out',result.stdout+result.stderr)
        self.assertIn('fixture running',result.stdout+result.stderr)
        self.assertEqual(self.original,self.cfg.read_text())

if __name__=='__main__':unittest.main(verbosity=2)
