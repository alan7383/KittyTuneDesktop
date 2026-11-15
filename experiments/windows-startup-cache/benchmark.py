import argparse
import atexit
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
parser.add_argument('--app-image',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--pairs',type=int,default=10)
parser.add_argument('--disposable-image',action='store_true',required=True)
args=parser.parse_args()
out=args.output.resolve()
if out.exists():parser.error('Use a new output directory')
out.mkdir(parents=True)
image=args.app_image.resolve();app=image/'app';exe=image/'KittyTune.exe'
original_cfg=(app/'KittyTune.cfg').read_bytes()
(out/'original.cfg').write_bytes(original_cfg)
atexit.register(lambda: (app/'KittyTune.cfg').write_bytes(original_cfg))
metadata=dict(executable_sha256=sha(exe), aot_sha256=sha(app/'kittytune-startup.aot'),
              default_archives=[p.name for p in (image/'runtime/bin/server').glob('*.jsa')],
              app_jars={p.name:sha(p) for p in app.glob('KittyTuneWindows-*.jar')},
              pairs=args.pairs, endpoint='main screen drawn and visible', filesystem_cache='warm')
(out/'metadata.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
lines=(app/'KittyTune.cfg').read_text(encoding='utf-8-sig').splitlines()
lines=[x for x in lines if 'AOTCache=' not in x and 'AOTMode=' not in x]
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
    owned = owned_pids(pid)
    snapshot = kernel.CreateToolhelp32Snapshot(2, 0)
    if snapshot == w.HANDLE(-1).value:
        raise c.WinError()
    try:
        entry = ProcessEntry()
        entry.size = c.sizeof(entry)
        more = kernel.Process32FirstW(snapshot, c.byref(entry))
        found = []
        while more:
            if entry.parent in owned and entry.name.lower() == 'powershell.exe':
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


def owned_pids(pid):
    snapshot=kernel.CreateToolhelp32Snapshot(2,0)
    try:
        entry=ProcessEntry();entry.size=c.sizeof(entry);more=kernel.Process32FirstW(snapshot,c.byref(entry));parents={}
        while more:
            parents[entry.pid]=entry.parent;more=kernel.Process32NextW(snapshot,c.byref(entry))
        owned={pid}
        for _ in range(5):owned.update(k for k,v in parents.items() if v in owned)
        return owned
    finally:kernel.CloseHandle(snapshot)


def visible(pid):
    owned=owned_pids(pid)
    found = []
    @callback_type
    def visit(hwnd, unused):
        owner = w.DWORD()
        user.GetWindowThreadProcessId(hwnd, c.byref(owner))
        if owner.value in owned and user.IsWindowVisible(hwnd):
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
    extra=['java-options=-Dkittytune.startuplog=true', 'java-options=-XX:AOTMode='+('on' if variant=='candidate' else 'off')]
    if variant=='candidate':extra+=['java-options=-XX:AOTCache=$APPDIR/kittytune-startup.aot']
    extra += ['java-options=-Duser.home='+str(profile/'home'), 'java-options=-Djava.io.tmpdir='+str(profile/'temp'), 'java-options=-Xlog:aot=info:file=aot.log']
    config=[]
    for line in lines:
        config.append(line)
        if line=='[JavaOptions]':config.extend(extra)
    (app/'KittyTune.cfg').write_bytes(('\n'.join(config)+'\n').encode('utf-8'))
    env = os.environ.copy()
    env.update(APPDATA=str(profile / 'roaming'), LOCALAPPDATA=str(profile / 'local'),
               TEMP=str(profile / 'temp'), TMP=str(profile / 'temp'))
    for name in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
                 'SKIKO_RENDER_API', 'SKIKO_GPU_PRIORITY']:
        env.pop(name, None)
    with (folder / 'stdout.log').open('w', encoding='utf-8') as stdout, \
            (folder / 'stderr.log').open('w', encoding='utf-8') as stderr:
        start = time.perf_counter()
        process = subprocess.Popen([str(exe)],
                                   env=env, cwd=folder, stdout=stdout, stderr=stderr)
        try:
            while time.perf_counter() - start < 60:
                text = log.read_text(encoding='utf-8') if log.exists() else ''
                if 'window content drawn' in text and visible(process.pid):
                    break
                if process.poll() is not None and len(owned_pids(process.pid))==1:
                    raise RuntimeError(f'{label}: process exited: {process.returncode}')
                time.sleep(0.025)
            else:
                raise RuntimeError(f'{label}: main window did not draw and become visible')
            elapsed = (time.perf_counter() - start) * 1000
            marks = {m.group(2).strip(): int(m.group(1)) for m in re.finditer(
                r'\+\s*(\d+) ms process.*?since first mark\s+([^\n]+)', text)}
            if 'router: composing main screen' not in marks:
                raise RuntimeError('The run did not reach the real main screen.')
            row = dict(variant=variant, run=label, warmup=warmup,
                       drawn_visible_ms=round(elapsed, 3), marks=marks)
            rows.append(row)
            print(label, round(elapsed), 'ms', flush=True)
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
    return profile


launch('baseline', 'warmup-baseline', True)
initialized = launch('candidate', 'warmup-candidate', True)
shutil.copytree(initialized, seed, dirs_exist_ok=True)
for pair in range(args.pairs):
    order = ['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']
    for variant in order:
        launch(variant, f'pair-{pair}-{variant}')
        rows[-1]['pair'] = pair
    (out / 'samples.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
