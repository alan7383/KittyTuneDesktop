from pathlib import Path
import hashlib,json,os,shutil,zipfile

import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--baseline',type=Path,required=True)
parser.add_argument('--candidate',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
root=args.output.resolve();root.mkdir(exist_ok=False)
sources={'normal':args.baseline.resolve(),'splash':args.candidate.resolve()}
targets={name:root/(name+'-comparison-image') for name in sources}
for name,source in sources.items():
    target=targets[name]
    if target.exists():raise RuntimeError('Refusing to overwrite image '+str(target))
    shutil.copytree(source,target,copy_function=os.link)
candidate=next(targets['splash'].glob('app/KittyTuneWindows-*.jar'))
with zipfile.ZipFile(candidate) as source:
    native={name:source.read(name) for name in source.namelist() if name.startswith('native/') and not name.endswith('/')}
assert any(name.endswith('libkittytune_audio_dsp.dll') for name in native),'Missing compiled native DSP'
manifest={}
for name,image in targets.items():
    jar=next(image.glob('app/KittyTuneWindows-*.jar'))
    staged=jar.with_suffix('.new.jar')
    with zipfile.ZipFile(jar) as source:
        entries={name:source.read(name) for name in source.namelist() if not name.endswith('/') and not name.startswith('native/')}
    entries.update(native)
    with zipfile.ZipFile(staged,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for entry,data in sorted(entries.items()):
            info=zipfile.ZipInfo(entry,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    # Replace the comparison hardlink; never overwrite the source image's linked file.
    os.replace(staged,jar)
    manifest[name]={'image':str(image),'jar':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
        'runtime_modules_sha256':hashlib.sha256((image/'runtime/lib/modules').read_bytes()).hexdigest(),
        'launcher_sha256':hashlib.sha256((image/'KittyTune.exe').read_bytes()).hexdigest()}
assert manifest['normal']['runtime_modules_sha256']==manifest['splash']['runtime_modules_sha256']
assert manifest['normal']['launcher_sha256']==manifest['splash']['launcher_sha256']
(root/'comparison-images.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(manifest,indent=2))
