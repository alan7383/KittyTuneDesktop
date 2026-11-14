import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--config', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--baseline-ref', default='c4885d6')
parser.add_argument('--pairs', type=int, default=5)
parser.add_argument('--warmup-ms', type=int, default=3000)
parser.add_argument('--measure-ms', type=int, default=6000)
args = parser.parse_args()
repo = args.repo.resolve()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
config = json.loads(args.config.read_text())
experiment = Path(__file__).resolve().parent
java = Path(config['javaHome']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')

def invoke(arguments, name, env=None):
    argfile = out / (name + '.args')
    argfile.write_bytes(('\n'.join('"' + str(a).replace('\\', '\\\\').replace('"', '\\"') + '"' for a in arguments)).encode())
    result = subprocess.run([str(java), '@' + str(argfile)], capture_output=True, text=True, env=env, timeout=180)
    (out / (name + '.stdout')).write_bytes(result.stdout.encode())
    (out / (name + '.stderr')).write_bytes(result.stderr.encode())
    if result.returncode:
        raise RuntimeError(f'{name} failed; see {out / (name + ".stderr")}')
    return result.stdout

path = 'src/main/kotlin/com/alananasss/kittytune/utils/FadingEdge.kt'
provenance = {'baselineRef': args.baseline_ref, 'sources': {},
    'fixtureSha256': hashlib.sha256((experiment / 'ScrollEdgeBenchmark.kt').read_bytes()).hexdigest()}
for variant in ('baseline', 'candidate'):
    source = (subprocess.check_output(['git', 'show', args.baseline_ref + ':' + path], cwd=repo)
              if variant == 'baseline' else (repo / path).read_bytes())
    directory = out / variant
    directory.mkdir(exist_ok=True)
    (directory / 'FadingEdge.kt').write_bytes(source)
    provenance['sources'][variant] = hashlib.sha256(source).hexdigest()
    invoke(['-cp', os.pathsep.join(config['compilerClasspath']), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-jvm-target', '21', '-module-name', config['moduleName'],
        '-Xplugin=' + config['composePlugin'], '-cp', os.pathsep.join(config['runtimeClasspath']),
        '-d', directory / 'classes', directory / 'FadingEdge.kt', experiment / 'ScrollEdgeBenchmark.kt'], 'compile-' + variant)
(out / 'source-provenance.json').write_bytes((json.dumps(provenance, indent=2) + '\n').encode())
version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
environment = {'platform': platform.platform(), 'processor': platform.processor(), 'java': version.stderr,
    'pairs': args.pairs, 'warmupMs': args.warmup_ms, 'measureMs': args.measure_ms,
    'jvmFlags': ['-Xms256m', '-Xmx512m', '-XX:+UseG1GC', '-XX:ParallelGCThreads=2', '-XX:ConcGCThreads=1'],
    'windowSize': [1280, 720], 'repaintTimerMs': 16}
(out / 'environment.json').write_bytes((json.dumps(environment, indent=2) + '\n').encode())
rows = []
for pair in range(args.pairs):
    for variant in (('baseline', 'candidate') if pair % 2 == 0 else ('candidate', 'baseline')):
        profile = out / 'profiles' / f'{pair}-{variant}'
        env = os.environ.copy()
        for variable, name in [('APPDATA', 'roaming'), ('LOCALAPPDATA', 'local')]:
            directory = profile / name
            directory.mkdir(parents=True, exist_ok=True)
            env[variable] = str(directory)
        (profile / 'home').mkdir(parents=True, exist_ok=True)
        cp = [str(out / variant / 'classes'), *config['runtimeClasspath']]
        native_args = ['-Dskiko.library.path=' + config['nativeDir']] if config.get('nativeDir') else []
        stdout = invoke(['--enable-native-access=ALL-UNNAMED', '-Xms256m', '-Xmx512m', '-XX:+UseG1GC',
            '-XX:ParallelGCThreads=2', '-XX:ConcGCThreads=1', '-Duser.home=' + str(profile / 'home'),
            *native_args, '-cp', os.pathsep.join(cp),
            'ScrollEdgeBenchmark', args.warmup_ms, args.measure_ms], f'run-{pair}-{variant}', env)
        parsed = [json.loads(line) for line in stdout.splitlines() if line.startswith('{"scenario":')]
        if len(parsed) != 2:
            raise RuntimeError('missing benchmark phases')
        for result in parsed:
            trace = {'pair': pair, 'variant': variant, **result}
            (out / f'trace-{pair}-{variant}-{result["scenario"]}.json').write_bytes((json.dumps(trace) + '\n').encode())
            row = {k: v for k, v in trace.items() if k not in ('dispatchTimesNs', 'frameTimesNs')}
            row['edtCpuNsPerFrame'] = row['edtCpuNs'] / row['frames']
            row['edtBytesPerFrame'] = row['edtBytes'] / row['frames']
            rows.append(row)
        print(f'pair {pair + 1}/{args.pairs}: {variant} complete', flush=True)
renderers = {r['renderer'] for r in rows}
if len(renderers) != 1:
    raise RuntimeError('renderer changed between forks')
with (out / 'forks.csv').open('w', newline='') as file:
    writer = csv.DictWriter(file, fieldnames=list(rows[0]), lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows)
summary = {}
metrics = ('frames', 'edtCpuNsPerFrame', 'edtBytesPerFrame', 'dispatchP95Ns', 'frameRecordMedianNs', 'frameRecordP95Ns', 'dispatchOver16ms')
for scenario in ('single-list', 'six-carousels'):
    summary[scenario] = {}
    for variant in ('baseline', 'candidate'):
        selected = [r for r in rows if r['scenario'] == scenario and r['variant'] == variant]
        summary[scenario][variant] = {key: {'median': statistics.median(r[key] for r in selected),
            'forkValues': [r[key] for r in selected]} for key in metrics}
(out / 'summary.json').write_bytes((json.dumps(summary, indent=2) + '\n').encode())
