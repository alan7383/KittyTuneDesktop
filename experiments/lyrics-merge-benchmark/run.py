import argparse
import csv
import json
import math
from pathlib import Path
import platform
import statistics
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--java-home', required=True, type=Path)
parser.add_argument('--baseline-classpath', required=True, type=Path)
parser.add_argument('--candidate-classpath', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--pairs', type=int, default=5)
args = parser.parse_args()
if args.pairs < 1:
    parser.error('--pairs must be positive')
for path in (args.baseline_classpath, args.candidate_classpath):
    if not path.is_file() or not path.read_text().strip():
        parser.error(f'missing or empty classpath file: {path}')
args.output.mkdir(parents=True, exist_ok=True)
java = args.java_home / 'bin' / ('java.exe' if platform.system() == 'Windows' else 'java')
flags = ['-Xms256m', '-Xmx512m', '-XX:+UseG1GC', '-XX:ParallelGCThreads=2',
         '-XX:ConcGCThreads=1', '-XX:MaxGCPauseMillis=50', '-Djava.awt.headless=true']
metadata = {'platform': platform.platform(), 'processor': platform.processor(),
            'java': subprocess.run([str(java), '-version'], capture_output=True, text=True).stderr,
            'flags': flags, 'pairs': args.pairs, 'order': [], 'samplesPerSizePerFork': 25,
            'warmupBurstsPerSize': 12, 'sizes': [24, 96, 240]}
rows = []
columns = ['variant', 'results', 'iteration', 'wallNs', 'cpuNs', 'bytes', 'maxMergeNs', 'over16ms', 'checksum']
for pair in range(args.pairs):
    for variant in (['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']):
        cp_file = getattr(args, variant + '_classpath')
        metadata['order'].append({'pair': pair, 'variant': variant})
        start = time.time()
        java_args = [*flags, '-cp', cp_file.read_text().strip(), 'LyricsMergeBenchmark', variant]
        argfile = args.output / f'{pair}-{variant}.args'
        argfile.write_text('\n'.join('"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
                                     for value in java_args), encoding='utf-8')
        run = subprocess.run([str(java), '@' + str(argfile.resolve())], capture_output=True, text=True)
        (args.output / f'{pair}-{variant}.stdout').write_text(run.stdout)
        (args.output / f'{pair}-{variant}.stderr').write_text(run.stderr)
        if run.returncode:
            raise RuntimeError(f'{variant} JVM failed: {run.stderr[-2000:]}')
        parsed = list(csv.reader(line for line in run.stdout.splitlines() if line.startswith(variant + ',')))
        if len(parsed) != 75:
            raise RuntimeError(f'Expected 75 measurements, got {len(parsed)}')
        for row in parsed:
            values = dict(zip(columns, row))
            rows.append({'pair': pair, **values})
        print(f'pair {pair + 1}/{args.pairs}: {variant} completed in {time.time()-start:.1f}s', flush=True)
with (args.output / 'samples.csv').open('w', newline='') as file:
    writer = csv.DictWriter(file, fieldnames=['pair', *columns])
    writer.writeheader()
    writer.writerows(rows)

def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)]

summary = {}
for size in metadata['sizes']:
    checksums = {row['checksum'] for row in rows if int(row['results']) == size}
    if len(checksums) != 1:
        raise RuntimeError(f'Output checksum differs at size {size}: {checksums}')
    summary[size] = {}
    for variant in ('baseline', 'candidate'):
        selected = [row for row in rows if row['variant'] == variant and int(row['results']) == size]
        wall = [int(row['wallNs']) / 1e6 for row in selected]
        per_fork = [statistics.median([int(row['wallNs']) / 1e6 for row in selected if int(row['pair']) == pair])
                    for pair in range(args.pairs)]
        summary[size][variant] = {'samples': len(selected), 'burstMedianMs': statistics.median(wall),
            'burstP95Ms': percentile(wall, .95), 'cpuMedianMs': statistics.median(int(row['cpuNs']) / 1e6 for row in selected),
            'allocatedMedianBytes': statistics.median(int(row['bytes']) for row in selected),
            'singleMergeP95Ms': percentile([int(row['maxMergeNs']) / 1e6 for row in selected], .95),
            'mergesOver16ms': sum(int(row['over16ms']) for row in selected), 'forkMedianMs': per_fork}
(args.output / 'environment.json').write_text(json.dumps(metadata, indent=2))
(args.output / 'summary.json').write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
