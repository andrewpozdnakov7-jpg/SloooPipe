#!/usr/bin/env python3
"""Validate actual CI outputs; never infer executed tests from the source inventory."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


def main():
    root = Path(__file__).resolve().parents[2]
    output = Path(os.environ['CI_ARTIFACT_DIR'])
    variant = os.environ['AUTH_VARIANT']
    assert variant in ('with', 'without'), variant
    suites = {}
    for module, task in [('app', 'testDebugUnitTest'), ('youtube-auth-api', 'test'), ('shared', 'jvmTest')]:
        files = sorted((root/'NewPipe'/module/'build/test-results'/task).glob('TEST-*.xml'))
        assert files, f'No fresh test XML for {module}:{task}'
        totals = dict(tests=0, failures=0, errors=0, skipped=0)
        for path in files:
            suite = ET.parse(path).getroot()
            assert suite.tag == 'testsuite', str(path)
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
        assert totals['tests'] > totals['skipped'], f'No executed tests: {module}'
        assert totals['failures'] == totals['errors'] == 0, (module, totals)
        suites[f'{module}:{task}'] = totals

    isolation = root/'NewPipe/app/build/reports/auth/dependency-graph.txt'
    fields = dict(line.split('=', 1) for line in isolation.read_text().splitlines()[:3])
    assert fields == {'withAuth': str(variant == 'with').lower(), 'extractors': '1', 'hooks': '1' if variant == 'with' else '0'}, fields

    apks = sorted((root/'NewPipe/app/build/outputs/apk/debug').glob('*.apk'))
    assert len(apks) == 1, [x.name for x in apks]
    apk = apks[0]
    sdk = Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
    verification = subprocess.run([str(sdk/'apksigner'), 'verify', str(apk)], capture_output=True, text=True)
    assert verification.returncode != 0, 'CI APK unexpectedly signed'
    assert 'DOES NOT VERIFY' in verification.stdout + verification.stderr, verification.stderr
    badging = subprocess.check_output([str(sdk/'aapt'), 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    assert match and match[1] == 'org.schabi.newpipe.debug', badging.splitlines()[:1]
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    manifest = {
        'source_commit': os.environ['GITHUB_SHA'],
        'source_tree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=root, text=True).strip(),
        'workflow_run': os.environ['GITHUB_RUN_ID'],
        'workflow_attempt': os.environ['GITHUB_RUN_ATTEMPT'],
        'auth': variant,
        'unsigned_apk': {'name': apk.name, 'sha256': digest, 'bytes': apk.stat().st_size, 'package': match[1], 'signed': False},
        'test_suites': suites,
        'auth_isolation': fields,
        'device_testing': 'NOT RUN',
    }
    (output/'build-validation.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    summary = Path(os.environ['GITHUB_STEP_SUMMARY'])
    with summary.open('a', encoding='utf-8') as stream:
        stream.write(f'## SloooPipe auth-{variant}\n\nBuild and fresh test validation completed; signing follows.\n\n')
        stream.write('| Suite | Tests | Skipped | Failures | Errors |\n| --- | ---: | ---: | ---: | ---: |\n')
        for suite, totals in suites.items():
            stream.write(f"| {suite} | {totals['tests']} | {totals['skipped']} | {totals['failures']} | {totals['errors']} |\n")
        stream.write('\nAuth isolation verified. Device testing remains pending.\n')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
