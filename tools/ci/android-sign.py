#!/usr/bin/env python3
"""Sign a validated CI APK with the existing key; publish only verified output."""

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def run(command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f'{Path(command[0]).name} failed; signing inputs are not printed')
    return result.stdout


def main():
    root = Path(__file__).resolve().parents[2]
    output = Path(os.environ['CI_ARTIFACT_DIR'])
    manifest = json.loads((output/'build-validation.json').read_text())
    assert manifest['source_commit'] == os.environ['GITHUB_SHA']
    assert manifest['auth'] == os.environ['AUTH_VARIANT']
    unsigned = root/'NewPipe/app/build/outputs/apk/debug'/manifest['unsigned_apk']['name']
    assert unsigned.resolve().parent == (root/'NewPipe/app/build/outputs/apk/debug').resolve()
    assert hashlib.sha256(unsigned.read_bytes()).hexdigest() == manifest['unsigned_apk']['sha256']
    expected = os.environ['SLOOOPIPE_CERT_SHA256'].lower()
    assert re.fullmatch(r'[0-9a-f]{64}', expected), 'Invalid expected certificate fingerprint'
    sdk = Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
    with tempfile.TemporaryDirectory(prefix='slooopipe-signing-', dir=os.environ['RUNNER_TEMP']) as directory:
        private = Path(directory)
        os.chmod(private, 0o700)
        key = private/'existing.keystore'
        key.write_bytes(base64.b64decode(os.environ['SLOOOPIPE_KEYSTORE_BASE64'], validate=True))
        os.chmod(key, 0o600)
        aligned = private/'aligned.apk'
        signed = private/'signed.apk'
        run([str(sdk/'zipalign'), '-p', '-f', '4', str(unsigned), str(aligned)])
        run([str(sdk/'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', os.environ['SLOOOPIPE_KEY_ALIAS'],
             '--ks-pass', 'env:SLOOOPIPE_STORE_PASSWORD', '--key-pass', 'env:SLOOOPIPE_KEY_PASSWORD',
             '--v4-signing-enabled', 'false', '--out', str(signed), str(aligned)])
        certificates = run([str(sdk/'apksigner'), 'verify', '--verbose', '--print-certs', str(signed)])
        fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', certificates, re.MULTILINE)
        assert [x.lower() for x in fingerprints] == [expected], 'Certificate differs from the existing configured key'
        run([str(sdk/'zipalign'), '-c', '-p', '4', str(signed)])
        name = f"SloooPipe-auth-{manifest['auth']}-signed.apk"
        digest = hashlib.sha256(signed.read_bytes()).hexdigest()
        manifest['apk'] = {'name': name, 'sha256': digest, 'bytes': signed.stat().st_size,
                           'package': manifest['unsigned_apk']['package'], 'signed': True,
                           'certificate_sha256': expected}
        # Do not retain certificate subject/issuer or private signing files in artifacts.
        shutil.copyfile(signed, output/name)
        (output/(name+'.sha256')).write_text(f'{digest}  {name}\n', encoding='utf-8')
        (output/'build-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as stream:
        stream.write(f'\nSigned APK: `{name}`\n\nSHA256: `{digest}`\n\nExisting certificate fingerprint matched.\n')
    print('Signed APK verified against the existing certificate; private temporary files removed.')


if __name__ == '__main__':
    main()
