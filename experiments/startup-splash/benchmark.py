import argparse, ctypes as c, hashlib, json, os, re, shutil, subprocess, time
from ctypes import wintypes as w
from pathlib import Path
from types import SimpleNamespace
import psutil

root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--baseline', type=Path, required=True)
parser.add_argument('--candidate', type=Path, required=True)
parser.add_argument('--jdk', type=Path, required=True)
parser.add_argument('--runs', type=int, default=8)
parser.add_argument('--smoke', action='store_true')
parser.add_argument('--output', required=True, type=Path)
options = parser.parse_args()
out = options.output.resolve()
out.mkdir(exist_ok=False)
args = SimpleNamespace(quiet_timeout=60)
def save(name, value):
    (out/name).write_text(json.dumps(value, indent=2))
def sum_memory(values):
    return tuple(map(sum, zip(*values))) if values else (0,0)
user = c.WinDLL('user32', use_last_error=True)
callback = c.WINFUNCTYPE(w.BOOL, w.HWND, w.LPARAM)
user.EnumWindows.argtypes = [callback, w.LPARAM]
user.GetWindowThreadProcessId.argtypes = [w.HWND, c.POINTER(w.DWORD)]
user.IsWindowVisible.argtypes = [w.HWND]
user.GetWindowTextW.argtypes = [w.HWND, w.LPWSTR, c.c_int]

def window_visible(pids):
    found = []
    @callback
    def visit(hwnd, unused):
        pid = w.DWORD()
        user.GetWindowThreadProcessId(hwnd, c.byref(pid))
        if pid.value in pids and user.IsWindowVisible(hwnd):
            title = c.create_unicode_buffer(512)
            user.GetWindowTextW(hwnd, title, len(title))
            if title.value == 'KittyTune': found.append(True)
        return True
    user.EnumWindows(visit, 0)
    return bool(found)

class Tree:
    def __init__(self, pid):
        self.known = {}
        self.cpu = {}
        self.add(psutil.Process(pid))

    def add(self, process):
        try: self.known[process.pid] = process.create_time()
        except psutil.Error: pass

    def sample(self):
        memory = []
        pids = []
        for pid, born in list(self.known.items()):
            try:
                process = psutil.Process(pid)
                if process.create_time() != born: continue
                for child in process.children(recursive=True): self.add(child)
                with process.oneshot():
                    info = process.memory_info()
                    cpu = process.cpu_times()
                    self.cpu[pid, born] = cpu.user + cpu.system
                    memory.append((info.private, info.rss))
                    pids.append(pid)
            except (psutil.Error, OSError): pass
        private, working = sum_memory(memory)
        return dict(private_bytes=private, working_set_bytes=working,
                    cpu_seconds=sum(self.cpu.values()), pids=pids,
                    system_cpu_percent=psutil.cpu_percent())

    def stop(self):
        self.sample()
        live = []
        for pid, born in self.known.items():
            try:
                p = psutil.Process(pid)
                if p.create_time() == born:
                    p.terminate()
                    live.append(p)
            except psutil.Error: pass
        _, remaining = psutil.wait_procs(live, timeout=5)
        for p in remaining:
            try: p.kill()
            except psutil.Error: pass

java = options.jdk.resolve()/'bin/java.exe'
seed = out/'seed'
for name in ['roaming/KittyTune/icons','local','home/Desktop','temp','program-data','public/Desktop','program-files']:
    (seed/name).mkdir(parents=True,exist_ok=True)
prefs=dict(has_completed_setup=True,protected_tracks_announced=True,audio_cache_remux_version=2,
    background_playback_shortcuts=False,discord_rpc_enabled=False,
    proxy_enabled=True,proxy_host='127.0.0.1',proxy_port=9,proxy_type='HTTP')
for name,value in [('prefs.json',prefs),('soundtune_auth.json',{'is_guest_mode':True}),
    ('kitty_pet.json',{'announced':True}),('whats_new.json',{'seen':'2026-11-together'})]:
    (seed/'roaming/KittyTune'/name).write_text(json.dumps(value),encoding='utf-8')
classes=out/'agent-classes';classes.mkdir()
asm=next(options.baseline.resolve().glob('app/asm-*.jar'))
subprocess.run([str(options.jdk.resolve()/'bin/javac.exe'),'-cp',str(asm),'-d',str(classes),
    str(root/'StartupBenchAgent.java'),str(root/'RendererProbe.java')],check=True,capture_output=True)
import zipfile
agent=out/'isolation-agent.jar'
with zipfile.ZipFile(agent,'w') as archive:
    archive.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nPremain-Class: StartupBenchAgent\nAgent-Class: RendererProbe\n\n')
    for p in classes.rglob('*.class'):archive.write(p,p.relative_to(classes).as_posix())
    with zipfile.ZipFile(asm) as source:
        for name in source.namelist():
            if name.endswith('.class'):archive.writestr(name,source.read(name))
env={**os.environ,'APPDATA':str(seed/'roaming'),'LOCALAPPDATA':str(seed/'local')}
subprocess.run([str(java),'-cp',str(options.baseline.resolve()/'app/*'),str(root/'SeedIcon.java'),
    str(seed/'roaming/KittyTune/icons')],env=env,check=True,capture_output=True)
def visible_windows(pids):
    found=[]
    user.GetClassNameW.argtypes=[w.HWND,w.LPWSTR,c.c_int]
    user.GetWindowRect.argtypes=[w.HWND,c.POINTER(w.RECT)]
    @callback
    def visit(hwnd,unused):
        pid=w.DWORD();user.GetWindowThreadProcessId(hwnd,c.byref(pid))
        if pid.value not in pids or not user.IsWindowVisible(hwnd):return True
        rect=w.RECT();user.GetWindowRect(hwnd,c.byref(rect))
        if rect.right-rect.left<100 or rect.bottom-rect.top<50:return True
        title=c.create_unicode_buffer(256);kind=c.create_unicode_buffer(256)
        user.GetWindowTextW(hwnd,title,256);user.GetClassNameW(hwnd,kind,256)
        found.append(dict(title=title.value,kind=kind.value,width=rect.right-rect.left,height=rect.bottom-rect.top))
        return True
    user.EnumWindows(visit,0)
    return found
images = {}
for variant in ['normal','splash']:
    image = (options.baseline if variant=='normal' else options.candidate).resolve()
    launcher=image/'KittyTuneBench.exe'
    if launcher.exists():
        assert launcher.read_bytes()==(image/'KittyTune.exe').read_bytes(),'Unexpected benchmark launcher'
    else:shutil.copy2(image/'KittyTune.exe',launcher)
    images[variant]=image
save('metadata.json',dict(source='matched 1.8.0 development images; full feature versus original',
    endpoint='main content drawn plus main router and owned visible window',
    filesystem='warm filesystem cache; restarted processes, no OS cache eviction',
    build='same base source104f39bd; baseline and candidate repacked identically with same native DSP; same media isolation; candidate includes real progress and fade',
    fixture='empty cache isolated offlineguest; noaudio; lowerCPUpriority; both disable SMTC/globalshortcuts/Discord and Zapretwrites; ongoinguserplayback allowed; 40%prelaunchCPUguard',
    runs=options.runs,private_memory='process tree; excludes GPU',
    images={v:{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in im.glob('app/KittyTuneWindows*.jar')} for v,im in images.items()}))
rows=[]
def launch(variant,count,label):
    folder=out/label; profile=folder/'profile'
    shutil.copytree(seed,profile)
    tracks=profile/'local/KittyTuneCache/audio/tracks'; tracks.mkdir(parents=True,exist_ok=True)
    import random
    rng=random.Random(42)
    for i in range(count):
        p=tracks/f'{i}.mp3';p.write_bytes(bytes(4096)); stamp=1600000000+rng.randrange(100000)
        os.utime(p,(stamp,stamp))
    log=profile/'roaming/KittyTune/startup.log';log.unlink(missing_ok=True)
    image=images[variant]
    config=(image/'app/KittyTune.cfg').read_text(encoding='utf-8-sig')
    extras=['-Dkittytune.startuplog=true','-Duser.home='+str(profile/'home'),
        '-Djava.io.tmpdir='+str(profile/'temp'),'-Djpackage.app-path='+str(image/'KittyTuneBench.exe'), '-javaagent:'+str(agent)+'=normal']
    config=config.replace('[JavaOptions]','[JavaOptions]\n'+'\n'.join('java-options='+x for x in extras))
    staged=image/'app/KittyTuneBench.next.cfg'
    staged.write_text(config,encoding='utf-8')
    os.replace(staged,image/'app/KittyTuneBench.cfg')
    env={k.upper():v for k,v in os.environ.items()}
    for k,v in dict(APPDATA='roaming',LOCALAPPDATA='local',USERPROFILE='home',HOME='home',TEMP='temp',TMP='temp',
        ProgramData='program-data',ALLUSERSPROFILE='program-data',PUBLIC='public',OneDrive='home',ProgramFiles='program-files').items():
        env[k.upper()]=str(profile/v)
    for k in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','SKIKO_RENDER_API','SKIKO_GPU_PRIORITY']:env.pop(k,None)
    pre=[]
    for attempt in range(60):
        pre.append(psutil.cpu_percent(interval=1))
        if len(pre)>=3 and max(pre[-3:])<40:break
    else:
        save('load-guard-failure.json',dict(readings=pre,threshold=40));raise RuntimeError('Background CPU stayed above40%')
    tree=None;raw=[];feedback=None;main_ms=None;reveal_ms=None
    with (folder/'stdout.log').open('w') as stdout,(folder/'stderr.log').open('w') as stderr:
        try:
            start=time.perf_counter()
            process=subprocess.Popen([str(image/'KittyTuneBench.exe')],env=env,cwd=folder,stdout=stdout,stderr=stderr,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
            tree=Tree(process.pid)
            while time.perf_counter()-start<60:
                sample=tree.sample();sample['t']=time.perf_counter()-start;raw.append(sample)
                if feedback is None:
                    visible=visible_windows(sample['pids'])
                    if visible:feedback=dict(ms=sample['t']*1000,windows=visible)
                text=log.read_text(encoding='utf-8') if log.exists() else ''
                if 'window content drawn' in text and 'router: composing main screen' in text and window_visible(sample['pids']):
                    if main_ms is None:main_ms=(time.perf_counter()-start)*1000
                    if variant=='normal' or 'startup reveal drawn' in text:
                        reveal_ms=(time.perf_counter()-start)*1000
                        break
                if not sample['pids']:raise RuntimeError('App exited')
                time.sleep(.02)
            else:raise RuntimeError('Startup timeout')
            ms=(time.perf_counter()-start)*1000
            for _ in range(20):
                time.sleep(.25);sample=tree.sample();sample['t']=time.perf_counter()-start;raw.append(sample)
                if feedback is None:
                    visible=visible_windows(sample['pids'])
                    if visible:feedback=dict(ms=sample['t']*1000,windows=visible)
            row=dict(label=label,variant=variant,count=count,ms=main_ms,reveal_ms=reveal_ms,prelaunch_cpu=pre,feedback=feedback)
            if variant=='splash' and not any(w['kind']=='JavaSplash' for w in (feedback or {}).get('windows',[])):
                raise RuntimeError('Native splash feedback was not observed')
            observer_pid=max(tree.sample()['pids'],key=lambda pid:psutil.Process(pid).memory_info().private)
            observer=subprocess.run([str(java),'-cp',str(agent),'RendererProbe',str(observer_pid),str(agent),str(folder/'renderer.json')],capture_output=True,timeout=30)
            (folder/'renderer-probe.log').write_bytes(observer.stdout+observer.stderr)
            if observer.returncode:raise RuntimeError('Renderer observer failed')
            renderer=json.loads((folder/'renderer.json').read_text(encoding='utf-8-sig'))
            layers=[x for x in renderer['layers'] if x['window']=='KittyTune']
            if not layers or renderer['errors']:raise RuntimeError('Missing main renderer metadata')
            row['renderer']=layers
            stderr.flush()
            isolation=(folder/'stderr.log').read_text(errors='replace')
            for marker in ['PlayerViewModel.initWindowsSmtcService','ZapretManager.autoConfigureOnce']:
                if marker not in isolation:raise RuntimeError('Required isolation missing: '+marker)

            rows.append(row);save('samples.json',rows)
            (folder/'resources.json').write_text(json.dumps(raw))
            print(label,'feedback',round((feedback or {}).get('ms',-1)),'main',round(main_ms),'revealed',round(reveal_ms),flush=True)
        finally:
            if tree:tree.stop()
for count in [0]:
    for variant in images:launch(variant,count,f'warmup-{count}-{variant}')
    if options.smoke:break
    for pair in range(options.runs):
        for variant in (['normal','splash'] if pair%2==0 else ['splash','normal']):
            launch(variant,count,f'pair-{count}-{pair}-{variant}')
save('status.json',{'state':'PASSED','trials':len(rows)})
