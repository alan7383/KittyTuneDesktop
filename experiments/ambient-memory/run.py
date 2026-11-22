import argparse
import json
from pathlib import Path
import subprocess
import threading
import time

import psutil

parser = argparse.ArgumentParser()
parser.add_argument('--java', required=True)
parser.add_argument('--classpath', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--pairs', type=int, default=3)
args = parser.parse_args()
if args.pairs < 1:
    parser.error('--pairs must be positive')
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=False)
common = ['--enable-native-access=ALL-UNNAMED', '-Xms16m', '-Xmx512m', '-XX:+UseG1GC',
          '-XX:+UseCompactObjectHeaders', '-XX:ParallelGCThreads=2', '-XX:ConcGCThreads=1',
          '-cp', args.classpath.read_text().strip(),
          'com.alananasss.kittytune.experiments.AmbientMemoryProbe']

def arguments(name, values):
    path = root / (name + '.args')
    path.write_text('\n'.join('"' + str(a).replace('\\', '\\\\').replace('"', '\\"') + '"' for a in values), encoding='utf-8')
    return [args.java, '@' + str(path)]

with (root / 'prepare.log').open('w', encoding='utf-8') as log:
    subprocess.run(arguments('prepare', common + ['prepare', str(root)]), stdout=log, stderr=log, check=True, timeout=120)
rows = []
for pair in range(args.pairs):
    for variant in (['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']):
        with (root / f'{pair}-{variant}.stderr.log').open('w', encoding='utf-8') as err:
            process = subprocess.Popen(arguments(f'{pair}-{variant}', common + [variant, str(root)]),
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=err, text=True)
            timeout = threading.Timer(180, process.kill)
            timeout.daemon = True
            timeout.start()
            row = dict(pair=pair, variant=variant, phases=[], decode=[])
            try:
                tracked = psutil.Process(process.pid)
                for line in process.stdout:
                    if not line.startswith('{'):
                        continue
                    data = json.loads(line)
                    if data['phase'] == 'decode':
                        row['decode'].append(data)
                        continue
                    data['process_samples'] = []
                    for _ in range(5):
                        info = tracked.memory_info()
                        data['process_samples'].append(dict(rss=info.rss, private_bytes=getattr(info, 'private', None)))
                        time.sleep(0.15)
                    row['phases'].append(data)
                    process.stdin.write('continue\n')
                    process.stdin.flush()
                code = process.wait(timeout=10)
                if code:
                    raise RuntimeError(f'{variant} exited with {code}; see its stderr log')
                assert len(row['phases']) == 3 and len(row['decode']) == 3
                rows.append(row)
                (root / 'samples.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
                print(pair + 1, variant, [(p['phase'], round(p['heap_used'] / 1048576, 2)) for p in row['phases']], flush=True)
            finally:
                timeout.cancel()
                if process.poll() is None:
                    process.kill()
                    process.wait()
