import argparse,json,random,statistics
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('run',type=Path)
args=parser.parse_args()
root=args.run.resolve()
status=json.loads((root/'status.json').read_text())
assert status['state']=='PASSED','Run did not complete'
rows=json.loads((root/'samples.json').read_text())
pairs={}
for row in rows:
    if row['label'].startswith('pair-'):
        pairs.setdefault(int(row['label'].split('-')[2]),{})[row['variant']]=row
assert len(pairs)>=8 and all(set(p)=={'normal','splash'} for p in pairs.values()),'Incomplete pairs'
def pct(values,p):
    values=sorted(values);k=(len(values)-1)*p;i=int(k)
    return values[i]+(values[min(i+1,len(values)-1)]-values[i])*(k-i)
def summary(a,b):
    delta=[y-x for x,y in zip(a,b)];rng=random.Random(42)
    samples=[statistics.median(rng.choices(delta,k=len(delta))) for _ in range(20000)]
    return dict(normal_median=statistics.median(a),splash_median=statistics.median(b),
        normal_p95=pct(a,.95),splash_p95=pct(b,.95),paired_median_delta=statistics.median(delta),
        paired_bootstrap95=[pct(samples,.025),pct(samples,.975)])
results={'pairs':len(pairs)}
for key,field in [('feedback',lambda r:r['feedback']['ms']),('main',lambda r:r['ms']),('revealed',lambda r:r['reveal_ms'])]:
    results[key]=summary([field(p['normal']) for p in pairs.values()],[field(p['splash']) for p in pairs.values()])
renderer_keys=set()
for pair in pairs.values():
    for row in pair.values():
        renderer_keys.add(tuple((layer['api'],layer['width'],layer['height'],layer['scaleX'],layer['scaleY'],layer['info']) for layer in row['renderer']))
assert len(renderer_keys)==1,'Actual renderer/dimensions/scale changed; do not combine trials'
results['renderer']=rows[0]['renderer']
reveal=results['revealed']
results['publication_gate_passed']=(results['feedback']['splash_median']<results['feedback']['normal_median']/2
    and reveal['paired_median_delta']<=0 and reveal['paired_bootstrap95'][1]<=100)
lines=['# Animated startup — Windows comparison','',
    '| Endpoint | Original median / p95 | Splash median / p95 | Paired median delta (95% bootstrap) |',
    '|---|---:|---:|---:|']
for name,key in [('First visible feedback','feedback'),('Underlying main frame','main'),('Fully revealed main window','revealed')]:
    value=results[key];ci=value['paired_bootstrap95']
    lines.append(f"| {name} | {value['normal_median']/1000:.3f} / {value['normal_p95']/1000:.3f} s | {value['splash_median']/1000:.3f} / {value['splash_p95']/1000:.3f} s | {value['paired_median_delta']:.1f} ms ({ci[0]:.1f}, {ci[1]:.1f}) |")
lines+=['','Negative differences favor the splash candidate. The fully revealed endpoint includes the default fade; early feedback is not counted as player readiness.',
    f"Publication gate passed: **{results['publication_gate_passed']}**. See PROTOCOL.md for criteria fixed before the paired run.",'',
    '| Pair | Order | Original main / revealed ms | Splash main / revealed ms | Splash feedback ms |',
    '|---:|---|---:|---:|---:|']
for index,pair in pairs.items():
    a,b=pair['normal'],pair['splash']
    lines.append(f"| {index+1} | {'original first' if index%2==0 else 'splash first'} | {a['ms']:.1f} / {a['reveal_ms']:.1f} | {b['ms']:.1f} / {b['reveal_ms']:.1f} | {b['feedback']['ms']:.1f} |")
lines+=['','Method and limitations:', '',
    '- Equivalent 1.8.0 development builds from104f39bd; main JARs repacked identically with identical native DSP resources, launcher and runtime. This is not a comparison with the minified installed release.',
    '- Eight alternating AB/BA pairs; one warmup per variant excluded. Restarted processes, warm filesystem caches, no reboot/cache eviction. Every accepted slow trial retained.',
    '- Same isolated setup-complete guest seed with empty cache/library. Both arms disable global media keys, Discord, Windows SMTC registration and Zapret configuration writes. No audio playback started and no installed-app changes.',
    '- Below-normal priority and a40% system CPU gate for three seconds before launch allow concurrent user playback. Background load samples are retained. These results do not establish a universal zero-overhead guarantee.',
    '- Actual renderer, dimensions and DPI verified after timed windows. CPU/private memory/working set samples include five seconds after reveal; observer attachment is excluded. GPU memory and physical audio quality are not measured.',
    '- The main-frame endpoint requires main router composition, content drawn and owned main visibility. Full reveal additionally requires a draw with fade cover at zero. Neither measures online feed readiness or validates every input action.',
    '- p95 and seeded bootstrap intervals are descriptive for eight pairs. No Linux runtime, reboot-cold-disk or other hardware performance claims.', '']
(root/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
(root/'RESULTS.md').write_text('\n'.join(lines),encoding='utf-8')
print(json.dumps(results,indent=2))
