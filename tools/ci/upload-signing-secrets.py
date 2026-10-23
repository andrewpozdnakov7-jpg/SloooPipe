#!/usr/bin/env python3
"""Upload existing WSL signing inputs directly to repository Actions secrets."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--signing-dir', type=Path, default=Path.home()/'projects/slooopipe-src/signing')
    parser.add_argument('--repo', default='andrewpozdnakov7-jpg/SloooPipe')
    args = parser.parse_args()
    for command in ('gh', 'keytool'):
        if shutil.which(command) is None:
            sys.exit(f'{command} is required; install GitHub CLI/JDK and authenticate gh before running this helper.')
    directory = args.signing_dir.expanduser().resolve()
    config = directory/'keystore.properties'
    props = {}
    if config.is_file():
        for line in config.read_text(encoding='utf-8-sig').splitlines():
            if line.lstrip().startswith(('#', '!')) or '=' not in line:
                continue
            key, value = line.split('=', 1)
            props[key.strip()] = value
    else:
        # Exactly the existing WSL/Slooop fallback; no new key is generated.
        props = {'store.file': 'dashchan2-local-debug.keystore', 'store.password': 'android',
                 'key.alias': 'androiddebugkey', 'key.password': 'android'}
    required = ('store.file', 'store.password', 'key.alias', 'key.password')
    if any(not props.get(key) or '\n' in props[key] or '\r' in props[key] for key in required):
        sys.exit('Missing/invalid signing properties; existing files have not been changed.')
    key = Path(props['store.file'])
    if not key.is_absolute():
        key = directory/key
    if not key.is_file():
        sys.exit('Existing configured keystore not found. No new key will be created.')
    encoded = base64.b64encode(key.read_bytes())
    if len(encoded) > 48*1024:
        sys.exit('Encoded keystore exceeds the repository secret limit; do not upload a different key.')
    env = dict(os.environ, SLOOOPIPE_UPLOAD_STORE_PASS=props['store.password'])
    certificate = subprocess.run(['keytool', '-exportcert', '-keystore', str(key), '-alias', props['key.alias'],
                                  '-storepass:env', 'SLOOOPIPE_UPLOAD_STORE_PASS'], env=env, capture_output=True)
    if certificate.returncode:
        sys.exit('Existing key/alias/store password validation failed; secret values are not printed.')
    fingerprint = hashlib.sha256(certificate.stdout).hexdigest()
    values = {'SLOOOPIPE_KEYSTORE_BASE64': encoded,
              'SLOOOPIPE_STORE_PASSWORD': props['store.password'].encode(),
              'SLOOOPIPE_KEY_ALIAS': props['key.alias'].encode(),
              'SLOOOPIPE_KEY_PASSWORD': props['key.password'].encode(),
              'SLOOOPIPE_CERT_SHA256': fingerprint.encode()}
    for name, value in values.items():
        result = subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repo], input=value, capture_output=True)
        if result.returncode:
            sys.exit(f'Failed to set {name}. Check gh auth/repository access; secret values are not printed.')
        print('Configured '+name, flush=True)
    result = subprocess.run(['gh', 'secret', 'list', '--repo', args.repo, '--json', 'name'], capture_output=True)
    if result.returncode or not set(values).issubset({x['name'] for x in json.loads(result.stdout)}):
        sys.exit('Could not confirm all signing secret names. No secret values were printed.')
    print('Existing signing inputs uploaded directly to GitHub Actions secrets. Local files unchanged.')
    print('Public certificate SHA256: '+fingerprint)


if __name__ == '__main__':
    main()
