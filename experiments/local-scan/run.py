import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess
import wave

parser = argparse.ArgumentParser()
parser.add_argument('--java', required=True)
parser.add_argument('--baseline-classpath', type=Path, required=True)
parser.add_argument('--candidate-classpath', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--files', type=int, default=300)
parser.add_argument('--pairs', type=int, default=3)
args = parser.parse_args()
if args.files < 1 or args.pairs < 1:
    parser.error('--files and --pairs must be positive')
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=False)
audio = root / 'audio'
audio.mkdir()
for i in range(args.files):
    with wave.open(str(audio / f'track-{i:04}.wav'), 'wb') as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(44100)
        stream.writeframes(b'\x00\x00' * 88200)

classpaths = {
    'baseline': args.baseline_classpath.read_text().strip(),
    'candidate': args.candidate_classpath.read_text().strip(),
}
samples = []
for pair in range(args.pairs):
    for variant in (['baseline', 'candidate'] if pair % 2 == 0 else ['candidate', 'baseline']):
        profile = root / f'profile-{pair}-{variant}'
        env = os.environ.copy()
        env['KITTYTUNE_SCAN_BENCHMARK'] = '1'
        for key, folder in [('APPDATA', 'roaming'), ('LOCALAPPDATA', 'local')]:
            path = profile / folder
            path.mkdir(parents=True)
            env[key] = str(path)
        home = profile / 'home'
        home.mkdir()
        arguments = ['--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true',
                     f'-Duser.home={home}', '-cp', classpaths[variant],
                     'com.alananasss.kittytune.experiments.LocalScanBenchmark',
                     str(audio), str(args.files), variant]
        argfile = root / f'{pair}-{variant}.args'
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '\\\\').replace('"', '\\"') + '"' for a in arguments), encoding='utf-8')
        result = subprocess.run([args.java, '@' + str(argfile)], env=env, capture_output=True, text=True, timeout=180)
        (root / f'{pair}-{variant}.log').write_text(result.stdout + result.stderr, encoding='utf-8')
        result.check_returncode()
        rows = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{"variant":')]
        assert len(rows) == 5
        samples.extend(dict(pair=pair, **row) for row in rows)
        (root / 'samples.json').write_text(json.dumps(samples, indent=2), encoding='utf-8')
        print(f'{pair + 1}: {variant} median {statistics.median(r["wall_ms"] for r in rows):.2f} ms', flush=True)

for variant in classpaths:
    rows = [r for r in samples if r['variant'] == variant]
    print(variant, {key: statistics.median(r[key] for r in rows)
                    for key in ('wall_ms', 'cpu_ms', 'allocated_bytes')})
