import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--java', required=True)
parser.add_argument('--baseline-classpath', type=Path, required=True)
parser.add_argument('--candidate-classpath', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--pairs', type=int, default=3)
args = parser.parse_args()
if args.pairs < 1:
    parser.error('--pairs must be positive')
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=False)
classpaths = {'baseline': args.baseline_classpath.read_text().strip(),
              'candidate': args.candidate_classpath.read_text().strip()}
samples = []
for pair in range(args.pairs):
    for variant in (['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']):
        arguments = ['-Xms256m', '-Xmx512m', '-cp', classpaths[variant],
                     'com.alananasss.kittytune.experiments.TimeStretcherBenchmark', variant]
        argfile = root / f'{pair}-{variant}.args'
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '\\\\').replace('"', '\\"') + '"' for a in arguments), encoding='utf-8')
        result = subprocess.run([args.java, '@' + str(argfile)], capture_output=True, text=True, timeout=180)
        (root / f'{pair}-{variant}.log').write_text(result.stdout + result.stderr, encoding='utf-8')
        result.check_returncode()
        rows = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        assert len(rows) == 20
        samples.extend(dict(pair=pair, **row) for row in rows)
        (root / 'samples.json').write_text(json.dumps(samples, indent=2), encoding='utf-8')
        print(pair + 1, variant, {mode: statistics.median(r['wall_ms'] for r in rows if r['mode'] == mode)
                                  for mode in ['unity', 'pitch', 'stretch']}, flush=True)

for mode in ['unity', 'pitch', 'stretch', 'first-block']:
    rows = [r for r in samples if r['mode'] == mode]
    if mode != 'first-block':
        assert len({r['checksum'] for r in rows}) == 1
    key = 'allocated_bytes' if mode == 'first-block' else 'wall_ms'
    print(mode, {v: statistics.median(r[key] for r in rows if r['variant'] == v) for v in classpaths})
