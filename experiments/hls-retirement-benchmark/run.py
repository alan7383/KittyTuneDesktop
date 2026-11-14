import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--baseline-ref', required=True)
parser.add_argument('--config', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--pairs', type=int, default=5)
parser.add_argument('--samples', type=int, default=12)
parser.add_argument('--warmups', type=int, default=3)
parser.add_argument('--hold-ms', type=int, default=500)
args = parser.parse_args()
config = json.loads(args.config.read_text())
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
experiment = Path(__file__).resolve().parent
java = Path(config['javaHome']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
source_paths = ['src/main/kotlin/com/alananasss/kittytune/audio/' + name
                for name in ('AudioEngine.kt', 'HlsStreamAdapter.kt')]

def invoke(arguments, name, env=None):
    argfile = output / (name + '.args')
    argfile.write_text('\n'.join('"' + str(a).replace('\\', '\\\\').replace('"', '\\"') + '"' for a in arguments), encoding='utf-8')
    result = subprocess.run([str(java), '@' + str(argfile)], capture_output=True, text=True,
                            env=env, timeout=180)
    (output / (name + '.stdout')).write_text(result.stdout, encoding='utf-8')
    (output / (name + '.stderr')).write_text(result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(f'{name} failed: see {output / (name + ".stderr")}')
    return result.stdout

provenance = {'baselineRef': args.baseline_ref, 'sources': {}, 'dependencies': {}}
for variant in ('baseline', 'candidate'):
    sources = []
    hashes = {}
    for path in source_paths:
        data = (subprocess.check_output(['git', 'show', args.baseline_ref + ':' + path], cwd=args.repo)
                if variant == 'baseline' else (args.repo / path).read_bytes())
        target = output / variant / 'source' / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        sources.append(str(target))
        hashes[path] = hashlib.sha256(data).hexdigest()
    classes = output / variant / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    invoke(['-cp', os.pathsep.join(config['compilerClasspath']),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
            '-jvm-target', '21', '-module-name', config['moduleName'],
            '-Xfriend-paths=' + ','.join(config['friendPaths']),
            '-cp', os.pathsep.join(config['runtimeClasspath']), '-d', classes,
            *sources, experiment / 'HlsRetirementBenchmark.kt'], 'compile-' + variant)
    provenance['sources'][variant] = hashes
provenance['benchmarkSha256'] = hashlib.sha256((experiment / 'HlsRetirementBenchmark.kt').read_bytes()).hexdigest()
for path in config['friendPaths']:
    provenance['dependencies'][Path(path).name] = hashlib.sha256(Path(path).read_bytes()).hexdigest()
(output / 'source-provenance.json').write_text(json.dumps(provenance, indent=2))
version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
(output / 'environment.json').write_text(json.dumps({'platform': platform.platform(),
    'processor': platform.processor(), 'java': version.stderr, 'pairs': args.pairs,
    'samplesPerForkAndScenario': args.samples, 'warmupsPerForkAndScenario': args.warmups,
    'holdMs': args.hold_ms, 'jvmArgs': ['-Xms256m', '-Xmx512m', '-XX:+UseG1GC',
    '-XX:ParallelGCThreads=2', '-XX:ConcGCThreads=1', '-Djava.awt.headless=true']}, indent=2))
rows = []
for pair in range(args.pairs):
    order = ('baseline', 'candidate') if pair % 2 == 0 else ('candidate', 'baseline')
    for variant in order:
        profile = output / 'profiles' / f'{pair}-{variant}'
        env = os.environ.copy()
        for variable, subdir in [('APPDATA', 'roaming'), ('LOCALAPPDATA', 'local')]:
            path = profile / subdir
            path.mkdir(parents=True, exist_ok=True)
            env[variable] = str(path)
        (profile / 'home').mkdir(parents=True, exist_ok=True)
        cp = [str(output / variant / 'classes'), str(args.repo / 'src/test/resources'), *config['runtimeClasspath']]
        stdout = invoke(['--enable-native-access=ALL-UNNAMED', '-Xms256m', '-Xmx512m',
            '-XX:+UseG1GC', '-XX:ParallelGCThreads=2', '-XX:ConcGCThreads=1',
            '-Djava.awt.headless=true', '-Duser.home=' + str(profile / 'home'),
            '-Djava.library.path=' + config.get('nativeDir', ''), '-cp', os.pathsep.join(cp),
            'com.alananasss.kittytune.audio.HlsRetirementBenchmark', args.warmups, args.samples, args.hold_ms],
            f'run-{pair}-{variant}', env)
        lines = [line for line in stdout.splitlines()
                 if line.startswith(('scenario,', 'idle,', 'pending-license,', 'closing-session,'))]
        current = list(csv.DictReader(io.StringIO('\n'.join(lines))))
        if len(current) != 3 * args.samples:
            raise RuntimeError('missing benchmark samples')
        for row in current:
            rows.append({'pair': pair, 'variant': variant, **{k: (v if k == 'scenario' else int(v)) for k, v in row.items()}})
        print(f'pair {pair + 1}/{args.pairs}: {variant} complete', flush=True)
with (output / 'samples.csv').open('w', newline='') as file:
    writer = csv.DictWriter(file, fieldnames=list(rows[0]), lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows)
summary = {}
for scenario in ('idle', 'pending-license', 'closing-session'):
    summary[scenario] = {}
    for variant in ('baseline', 'candidate'):
        selected = [r for r in rows if r['variant'] == variant and r['scenario'] == scenario]
        result = {'count': len(selected)}
        for key in ('operation_ns', 'heartbeat_ns', 'ui_cpu_ns', 'ui_allocated_bytes', 'held_ns'):
            values = sorted(r[key] for r in selected)
            result[key] = {'median': statistics.median(values), 'p95': values[max(0, int(len(values) * .95 + .999) - 1)],
                           'forkMedians': [statistics.median(r[key] for r in selected if r['pair'] == pair) for pair in range(args.pairs)]}
        summary[scenario][variant] = result
(output / 'summary.json').write_text(json.dumps(summary, indent=2))
