"""Record reproducible emulator launch/memory evidence; this is not a phone benchmark."""
import argparse
import json
from pathlib import Path
import re
import statistics
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('package')
parser.add_argument('output', type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command):
    return subprocess.check_output(['adb', *command], text=True, encoding='utf-8', timeout=60)


measurements = []
for index in range(5):
    adb('shell', 'am', 'force-stop', args.package)
    launch = adb('shell', 'am', 'start', '-W', '-n', args.package + '/com.indirgitsin.app.MainActivity')
    (args.output / f'launch-{index + 1}.txt').write_text(launch, encoding='utf-8')
    if not re.search(r'^Status: ok\s*$', launch, re.M):
        raise SystemExit('App failed to launch: ' + launch)
    match = re.search(r'^TotalTime: (\d+)', launch, re.M)
    if not match:
        raise SystemExit('Missing launch timing: ' + launch)
    measurements.append(int(match[1]))
    time.sleep(1)
time.sleep(3)
memory = adb('shell', 'dumpsys', 'meminfo', args.package)
(args.output / 'meminfo.txt').write_text(memory, encoding='utf-8')
pss = re.search(r'TOTAL PSS:\s*(\d+)', memory) or re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
with (args.output / 'home.png').open('wb') as image:
    subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=image, check=True, timeout=30)
summary = dict(environment='GitHub Actions Android emulator; idle home; not physical-device performance',
               api=adb('shell', 'getprop', 'ro.build.version.sdk').strip(), package=args.package,
               cold_launch_ms=measurements, median_cold_launch_ms=statistics.median(measurements),
               idle_total_pss_kb=int(pss[1]) if pss else None)
(args.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary, indent=2))
