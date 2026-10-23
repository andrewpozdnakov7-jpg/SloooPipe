# GitHub Actions build and existing-key signing

Run **Actions → Android APK and tests → Run workflow** on the intended source ref.
Choose `with`, `without` or `both` authentication variants (default: both).
The workflow is manual; pushing documentation does not automatically start builds.
The first run remains pending until signing secrets are configured and a linked
successful result is recorded.

Each variant runs on a separate Ubuntu 24.04 runner with JDK 21, project Gradle
wrapper, SDK platform 37.0 and Build Tools 36.0.0. Actions are pinned to release
commit hashes. CI builds the main SloooPipe app, not the independent NewPlayer demo.
Dependency caches are retained; tests re-execute under Xvfb.

Requested tasks:

```text
:app:testDebugUnitTest :youtube-auth-api:test :shared:jvmTest
:app:verifyAuthIsolation :app:assembleDebug
```

The CI init script disables Gradle's default debug signing and forces fresh tests.
After build/test/isolation validation, a separate step signs with the user's exact
existing key and verifies its expected certificate fingerprint. No replacement key
is created. A local `dev` branch matches the WSL builder's package selection:
`org.schabi.newpipe.debug`. Application sources and WSL builder are unchanged.
The workflow has read-only repository contents permission and does not publish a
Release, tag or upstream PR. Signing values are passed only to the signing step.

## Configure existing signing inputs once

Run this on the external WSL computer that already contains
`~/projects/slooopipe-src/signing`. GitHub CLI (`gh`), JDK/keytool, Python3 and curl
must be available. Authenticate `gh` to an account allowed to set this repository's
Actions secrets. Do not send the key, passwords or Base64 data in chat or commit them.

```bash
gh auth login --hostname github.com --git-protocol https --web
curl -fsSL https://raw.githubusercontent.com/andrewpozdnakov7-jpg/SloooPipe/main/tools/ci/upload-signing-secrets.py -o /tmp/slooopipe-upload-signing-secrets.py
python3 /tmp/slooopipe-upload-signing-secrets.py --signing-dir "$HOME/projects/slooopipe-src/signing"
```

If needed on Ubuntu, install GitHub CLI first: `sudo apt-get install gh`.
The helper reads the same `store.file`, `store.password`, `key.alias`, `key.password`
properties as the WSL builder. If `keystore.properties` is absent, it uses the same
existing `dashchan2-local-debug.keystore` fallback and existing defaults. Missing
keys fail; local files are not replaced. The certificate is exported in memory to
compute its public SHA256. Inputs go to `gh secret set` over stdin, not command
arguments or console output. Only configured secret names/public fingerprint print.

Five repository Actions secrets are set:

| Name | Existing input |
| --- | --- |
| `SLOOOPIPE_KEYSTORE_BASE64` | Existing keystore encoded in memory |
| `SLOOOPIPE_STORE_PASSWORD` | `store.password` |
| `SLOOOPIPE_KEY_ALIAS` | `key.alias` |
| `SLOOOPIPE_KEY_PASSWORD` | `key.password` |
| `SLOOOPIPE_CERT_SHA256` | Public fingerprint derived from the existing alias certificate |

They are stored in GitHub Actions secrets, outside repository files/history. CI
fails early if any is absent. Changing signing setup later requires running this
helper with the intended existing setup again. No raw values are displayed.

## Artifacts and verification

The artifact `SloooPipe-auth-<variant>-<run>-<attempt>` is retained for 14 days:

* Signed debug APK and SHA256 sidecar, after successful validation and signing.
* Build manifest: source commit/tree, run/attempt, auth variant, signed APK identity,
  verified certificate fingerprint and actual XML test totals/failures/errors/skips.
* Auth-isolation graph, test XML/HTML, Gradle log and Java version. Diagnostics are
  uploaded on failure too; a failed artifact is not a successful signed build.
  Artifact downloads require a GitHub login.

Checks require nonempty executed tests in all three suites, zero failures/errors,
expected auth hooks, expected APK package, successful APK signature/alignment
verification and an exact match to the configured certificate SHA256. The decoded
keystore and aligned intermediate exist only in a private temporary directory;
cleanup removes them even on failure. They are not uploaded or dependency-cached.
Certificate subject/issuer is not retained in the artifact manifest.

A green CI result proves the recorded compile/test/isolation/signing scope, not
playback, device acceptance, audible sound or behavior of every source. Installing
an update still requires the installed application's matching package/certificate
and compatible versionCode. The ordinary external WSL ZIP workflow and existing
source/report archives remain unchanged.
