import argparse
import ctypes as c
from ctypes import wintypes as w
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


parser = argparse.ArgumentParser()
parser.add_argument('--app-image', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--baseline-jar', type=Path, required=True)
parser.add_argument('--candidate-jar', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--pairs', type=int, default=10)
parser.add_argument('--probe-pairs', type=int, default=5)
args = parser.parse_args()
if os.name != 'nt':
    parser.error('This runner measures the Windows app.')
out = args.output.resolve()
if out.exists():
    parser.error('Use a new output directory; previous samples are never overwritten.')
out.mkdir(parents=True)
app = args.app_image.resolve() / 'app'
runtime = out / 'runtime'
shutil.copytree(args.app_image / 'runtime', runtime)
shutil.copy2(args.java_home / 'bin/java.exe', runtime / 'bin/java.exe')
versions = [subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
            .stderr.splitlines()[:2]
            for java in [runtime / 'bin/java.exe', args.java_home / 'bin/java.exe']]
if versions[0] != versions[1]:
    parser.error('The supplied JDK must match the packaged runtime build.')
lines = (app / 'KittyTune.cfg').read_text(encoding='utf-8-sig').splitlines()
options = [line.removeprefix('java-options=').replace('$APPDIR', str(app))
           for line in lines if line.startswith('java-options=') and 'AOTCache=' not in line]
classpath = [line.removeprefix('app.classpath=').replace('$APPDIR', str(app))
             for line in lines if line.startswith('app.classpath=') and 'KittyTuneWindows-' not in line]
variants = dict(baseline=args.baseline_jar.resolve(), candidate=args.candidate_jar.resolve())
source_jars = {k: sha(v) for k, v in variants.items()}
# Native DSP source is unchanged; remove the rebuilt DLL's PE timestamp/debug-ID difference.
with zipfile.ZipFile(variants['baseline']) as baseline:
    dsp = baseline.read('native/libkittytune_audio_dsp.dll')
for variant, source in variants.copy().items():
    controlled = out / f'{variant}.jar'
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(controlled, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in original.infolist():
            data = dsp if entry.filename == 'native/libkittytune_audio_dsp.dll' else original.read(entry.filename)
            target.writestr(entry, data)
    variants[variant] = controlled
metadata = dict(jars={k: sha(v) for k, v in variants.items()},
                source_jars=source_jars,
                java_version=versions[0],
                runtime_jvm_sha256=sha(runtime / 'bin/server/jvm.dll'),
                options=options, pairs=args.pairs, prelaunch_cpu_limit=0.20,
                method='fresh JVMs, alternating pairs, warm filesystem cache, synthetic guest, refused local HTTP proxy')
(out / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')

kernel = c.WinDLL('kernel32', use_last_error=True)
user = c.WinDLL('user32', use_last_error=True)
kernel.GetProcessTimes.argtypes = [w.HANDLE] + [c.POINTER(w.FILETIME)] * 4
callback_type = c.WINFUNCTYPE(w.BOOL, w.HWND, w.LPARAM)
user.EnumWindows.argtypes = [callback_type, w.LPARAM]
user.GetWindowThreadProcessId.argtypes = [w.HWND, c.POINTER(w.DWORD)]
user.GetWindowTextW.argtypes = [w.HWND, w.LPWSTR, c.c_int]
user.IsWindowVisible.argtypes = [w.HWND]


class ProcessEntry(c.Structure):
    _fields_ = [('size', w.DWORD), ('usage', w.DWORD), ('pid', w.DWORD),
                ('heap', c.c_size_t), ('module', w.DWORD), ('threads', w.DWORD),
                ('parent', w.DWORD), ('priority', w.LONG), ('flags', w.DWORD),
                ('name', w.WCHAR * 260)]


kernel.CreateToolhelp32Snapshot.argtypes = [w.DWORD, w.DWORD]
kernel.CreateToolhelp32Snapshot.restype = w.HANDLE
kernel.Process32FirstW.argtypes = [w.HANDLE, c.POINTER(ProcessEntry)]
kernel.Process32NextW.argtypes = [w.HANDLE, c.POINTER(ProcessEntry)]
kernel.CloseHandle.argtypes = [w.HANDLE]


def powershell_children(pid):
    snapshot = kernel.CreateToolhelp32Snapshot(2, 0)
    if snapshot == w.HANDLE(-1).value:
        raise c.WinError()
    try:
        entry = ProcessEntry()
        entry.size = c.sizeof(entry)
        more = kernel.Process32FirstW(snapshot, c.byref(entry))
        found = []
        while more:
            if entry.parent == pid and entry.name.lower() == 'powershell.exe':
                found.append(entry.pid)
            more = kernel.Process32NextW(snapshot, c.byref(entry))
        return found
    finally:
        kernel.CloseHandle(snapshot)


def ticks(value):
    return value.dwLowDateTime + (value.dwHighDateTime << 32)


def system_times():
    idle, kt, ut = w.FILETIME(), w.FILETIME(), w.FILETIME()
    if not kernel.GetSystemTimes(c.byref(idle), c.byref(kt), c.byref(ut)):
        raise c.WinError()
    return ticks(idle), ticks(kt) + ticks(ut)


def process_cpu(process):
    values = [w.FILETIME() for _ in range(4)]
    if not kernel.GetProcessTimes(w.HANDLE(int(process._handle)), *map(c.byref, values)):
        raise c.WinError()
    return ticks(values[2]) + ticks(values[3])


def quiet():
    consecutive = 0
    deadline = time.monotonic() + 120
    while consecutive < 3:
        before = system_times()
        time.sleep(1)
        after = system_times()
        busy = 1 - (after[0] - before[0]) / (after[1] - before[1])
        consecutive = consecutive + 1 if busy < 0.20 else 0
        if time.monotonic() > deadline:
            raise RuntimeError('Machine stayed busy; stop other builds before benchmarking.')


def visible(pid):
    found = []
    @callback_type
    def visit(hwnd, unused):
        owner = w.DWORD()
        user.GetWindowThreadProcessId(hwnd, c.byref(owner))
        if owner.value == pid and user.IsWindowVisible(hwnd):
            title = c.create_unicode_buffer(256)
            user.GetWindowTextW(hwnd, title, len(title))
            if title.value == 'KittyTune':
                found.append(hwnd)
        return True
    user.EnumWindows(visit, 0)
    return bool(found)


seed = out / 'seed'
for directory in ['roaming/KittyTune', 'local', 'home', 'temp']:
    (seed / directory).mkdir(parents=True)
for name, data in [
    ('prefs.json', dict(has_completed_setup=True, protected_tracks_announced=True,
                        proxy_enabled=True, proxy_host='127.0.0.1', proxy_port=9, proxy_type='HTTP')),
    ('soundtune_auth.json', dict(is_guest_mode=True)),
    ('kitty_pet.json', dict(announced=True)),
]:
    (seed / 'roaming/KittyTune' / name).write_text(json.dumps(data), encoding='utf-8')
rows = []


def launch(variant, label, warmup=False):
    quiet()
    folder = out / label
    profile = folder / 'profile'
    shutil.copytree(seed, profile)
    log = profile / 'roaming/KittyTune/startup.log'
    log.unlink(missing_ok=True)
    vmargs = options + ['-Dkittytune.startuplog=true', '-Xlog:cds=info',
                        f'-Duser.home={profile / "home"}', f'-Djava.io.tmpdir={profile / "temp"}',
                        '-cp', ';'.join([str(variants[variant])] + classpath),
                        'com.alananasss.kittytune.MainKt']
    argfile = folder / 'launch.args'
    argfile.write_text('\n'.join('"' + a.replace('\\', '\\\\').replace('"', '\\"') + '"'
                                for a in vmargs), encoding='utf-8')
    env = os.environ.copy()
    env.update(APPDATA=str(profile / 'roaming'), LOCALAPPDATA=str(profile / 'local'),
               TEMP=str(profile / 'temp'), TMP=str(profile / 'temp'))
    for name in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
                 'SKIKO_RENDER_API', 'SKIKO_GPU_PRIORITY']:
        env.pop(name, None)
    with (folder / 'stdout.log').open('w', encoding='utf-8') as stdout, \
            (folder / 'stderr.log').open('w', encoding='utf-8') as stderr:
        before = system_times()
        start = time.perf_counter()
        process = subprocess.Popen([str(runtime / 'bin/java.exe'), '@' + str(argfile)],
                                   env=env, cwd=app, stdout=stdout, stderr=stderr)
        try:
            while time.perf_counter() - start < 60:
                text = log.read_text(encoding='utf-8') if log.exists() else ''
                if 'window content drawn' in text and visible(process.pid):
                    break
                if process.poll() is not None:
                    raise RuntimeError(f'{label}: process exited: {process.returncode}')
                time.sleep(0.025)
            else:
                raise RuntimeError(f'{label}: main window did not draw and become visible')
            elapsed = (time.perf_counter() - start) * 1000
            cpu = process_cpu(process)
            after = system_times()
            external = max(0, 1 - (after[0] - before[0] + cpu) / (after[1] - before[1]))
            marks = {m.group(2).strip(): int(m.group(1)) for m in re.finditer(
                r'\+\s*(\d+) ms process.*?since first mark\s+([^\n]+)', text)}
            if 'router: composing main screen' not in marks:
                raise RuntimeError('The run did not reach the real main screen.')
            row = dict(variant=variant, run=label, warmup=warmup,
                       drawn_visible_ms=round(elapsed, 3), process_cpu_ms=cpu / 10000,
                       other_cpu_including_helpers=round(external, 5), marks=marks)
            rows.append(row)
            print(label, round(elapsed), 'ms, other CPU including app helpers',
                  round(external * 100, 1), flush=True)
        finally:
            stable = 5
            if process.poll() is None:
                deadline = time.monotonic() + 30
                stable = 0
                while stable < 5 and time.monotonic() < deadline:
                    stable = 0 if powershell_children(process.pid) else stable + 1
                    time.sleep(0.1)
                cleanup = subprocess.run(['taskkill.exe', '/F', '/T', '/PID', str(process.pid)],
                                         capture_output=True, timeout=10,
                                         creationflags=subprocess.CREATE_NO_WINDOW)
                (folder / 'cleanup.log').write_bytes(cleanup.stdout + cleanup.stderr)
            process.wait(timeout=10)
            if stable < 5:
                raise RuntimeError('The old PnP query did not drain; no further launches are comparable.')
    (out / 'samples.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
    return profile, external


launch('baseline', 'warmup-baseline', True)
initialized, _ = launch('candidate', 'warmup-candidate', True)
shutil.copytree(initialized, seed, dirs_exist_ok=True)
for pair in range(args.pairs):
    order = ['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']
    for variant in order:
        launch(variant, f'pair-{pair}-{variant}')
        rows[-1]['pair'] = pair
    (out / 'samples.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')

if args.probe_pairs:
    driver = out / 'probe-driver'
    driver.mkdir()
    subprocess.run([str(args.java_home / 'bin/javac.exe'), '-cp', ';'.join(classpath), '-d', str(driver),
                    str(Path(__file__).with_name('DeviceProbe.java'))], check=True)
    probes = []
    for pair in range(args.probe_pairs):
        order = ['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']
        for variant in order:
            quiet()
            profile = seed
            cp = ';'.join([str(driver), str(variants[variant])] + classpath)
            argfile = out / 'probe.args'
            vmargs = options + [f'-Duser.home={profile / "home"}',
                                f'-Djava.io.tmpdir={profile / "temp"}', '-cp', cp, 'DeviceProbe']
            argfile.write_text('\n'.join('"' + a.replace('\\', '\\\\').replace('"', '\\"') + '"'
                                        for a in vmargs), encoding='utf-8')
            env = os.environ.copy()
            env.update(APPDATA=str(profile / 'roaming'), LOCALAPPDATA=str(profile / 'local'))
            result = subprocess.run([str(runtime / 'bin/java.exe'), '@' + str(argfile)],
                                    capture_output=True, text=True, env=env, timeout=60, check=True)
            match = re.search(r'probe_ms=([\d.]+),detected=(true|false)', result.stdout)
            if not match:
                raise RuntimeError(result.stdout + result.stderr)
            probes.append(dict(pair=pair, variant=variant, ms=float(match[1]), detected=match[2]))
            print('probe', pair, variant, match[1], 'ms', flush=True)
            (out / 'probes.json').write_text(json.dumps(probes, indent=2), encoding='utf-8')
