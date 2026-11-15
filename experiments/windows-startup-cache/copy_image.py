import argparse
from pathlib import Path
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
parser.add_argument('destination', type=Path)
args = parser.parse_args()
source = args.source.resolve()
destination = args.destination.resolve()
if destination.exists() or destination.is_relative_to(source):
    parser.error('Use a new destination outside the source image.')
if not (source/'KittyTune.exe').is_file():
    parser.error('Source must contain KittyTune.exe, app and runtime.')
destination.mkdir(parents=True)
for directory in ['app', 'runtime']:
    shutil.copytree(source/directory, destination/directory,
                    ignore=shutil.ignore_patterns('startup-training-*', 'kittytune-startup.aot'))
shutil.copy2(source/'KittyTune.exe', destination/'KittyTune.exe')
