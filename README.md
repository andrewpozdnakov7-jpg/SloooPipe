# SloooPipe

An experimental Android integration of **NewPipe with NewPlayer**. The goal is to
make familiar NewPipe workflows use NewPlayer and provide an implementation that
other developers can study, adapt and reuse.

[Русский](README.ru.md) · [Current results](RESULT.md) · [Architecture](docs/NEWPLAYER_SESSION_INTEGRATION.md)

## Development status

**Latest checkpoint: signed preview 2026-10-26**, source `2c22439`.
Both auth variants passed [GitHub Actions](https://github.com/andrewpozdnakov7-jpg/SloooPipe/actions/runs/40319678374): **264 tests per variant**
(218 app + 15 auth API + 31 shared), zero failures/errors/skips. The app suite
includes 63 NewPlayer integration cases and all 12 timestamp cases. APK hashes,
test XML, auth isolation and continuity of the existing signing key were verified.
[Download signed APKs and exact source](https://github.com/andrewpozdnakov7-jpg/SloooPipe/releases/tag/preview-20261026-01) ·
[Detailed CI/device results](docs/CI_PREVIEW_20261026.md).

The auth-enabled APK was installed and tested in updated LDPlayer / Android 14.
The raw incoming `t=0` defect is fixed even when extraction rewrites the URL and
cached history exists. Canonical links retain normal history/resume behavior.
The separate non-autoplay routing path also passed. Font 200%, automatic popup
return with picture, popup position/rotation, queue return and local video/mini
controls passed the focused regression. The auth-free APK has CI evidence only.
Paused popup rotation can delay the picture (black at 2 seconds, visible at 8 in
one repeat); this remains a partial case, separate from passed geometry checks.

| Evidence | Scope |
| --- | --- |
| [Current focused matrix](docs/RAW_ZERO_DEVICE_20261026.csv) | Exact signed auth-with CI artifact; update identity, raw-zero/history/router, font/popup, queue and local smoke. |
| [Preview1 batch](docs/PREVIEW1_RETEST_20261026.csv) | Historical 17 PASS, 1 FAIL, 1 BLOCKED; CCC failure is corrected by the newer APK. |
| [APK35 batch](docs/APK35_ACCEPTANCE.csv) | Historical broad coverage: 109 rows, 87 PASS, 8 PARTIAL, 5 FAIL, 2 NOT VERIFIED, 7 DEFERRED. Five FAIL represented four unique defects. |

These coverage counts are not a readiness percentage; historical results are not
silently transferred to a newer APK. Prior checks include live HLS/DVR, subtitles,
chapters, speed/quality, history settings, large queues, background/popup/PiP and
network recovery. Details and remaining gaps are in [current results](RESULT.md).

**No stable release is available.** Debug diagnostics remain enabled. YouTube
currently hits a not-bot challenge in the checked environment; optional sign-in
does not guarantee playback. Physical Android 16 and audible sound await phone
testing. Decoded audio-language/pitch, remote DASH manifests, scheduled/post-live
cases and SoundCloud remain partial or unverified. Cold remote paused-zero first
frames can remain black; local zero rendered. Android Auto, full SABR/PoToken and
a full refactor-branch port are not accepted. External player/Kodi remain external.

## Source layout

| Path | Purpose |
| --- | --- |
| `NewPipe/` | Main SloooPipe app on a pinned NewPipe dev snapshot |
| `NewPipe/third-party/NewPlayer/` | Embedded `:newplayer-local` library used by the app |
| `NewPipe/app/src/main/java/org/schabi/newpipe/newplayer/` | Kotlin playback coordinator, queue, modes, sources, history and UI hosts |
| `NewPipe/youtube-auth-api/` | Optional account API and diagnostic boundary |
| `NewPipe/third-party/NewPipeExtractor-auth/` | Extractor with optional auth hooks |
| `NewPlayer/` | Independent upstream-derived demo; editing it alone does not change the main APK |
| `tools/`, `docs/` | WSL builder, architecture, provenance and acceptance records |

[Exact upstream pins](UPSTREAM.json) · [Dev/refactor comparison and porting boundaries](docs/UPSTREAM_PORTING_20261022.md).
Pins are fixed for reproducibility; this repository does not claim to track every new upstream commit automatically.
Vendored README files describe their components; this root README describes SloooPipe.

## Build and test

[GitHub Actions: build, tests and existing-key signing](docs/CI_BUILD.md).
The manual workflow supports both auth variants and retains APK/test artifacts.
Existing-key signing is configured for this repository; both variants passed CI.
Forks require their own signing-secret configuration.


Use Linux/WSL, JDK 21, Android SDK platform 37.0 and Build Tools 36.0.0. The main app
pins Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21 and Media3 1.9.2. Its Gradle wrapper is included.
Headless shared JVM tests require Xvfb and xauth. SDK packages and dependencies must
already be installed or reachable; this is not a fully offline source distribution.

```bash
git clone https://github.com/andrewpozdnakov7-jpg/SloooPipe.git
cd SloooPipe/NewPipe
# For an existing checkout or extracted source archive, enter its NewPipe directory.
chmod +x gradlew
# Set JAVA_HOME and ANDROID_HOME for your Linux JDK and SDK.
xvfb-run -a ./gradlew :app:testDebugUnitTest :youtube-auth-api:test :shared:jvmTest :app:verifyAuthIsolation :app:assembleDebug -Pslooopipe.withAuth=true
```

Repeat with `-Pslooopipe.withAuth=false` to check the auth-free variant. Both
variants passed the current CI: 218 app tests, including all 63 NewPlayer integration
cases, plus 15 auth API and 31 shared tests per variant. Downloaded XML was reviewed.
The earlier external WSL result remains user-reported without full reports.
A normal checkout build writes APKs under `NewPipe/app/build/outputs/apk/` and uses
its debug signing setup. A different signing certificate cannot update an existing install.

For source-ZIP selection, retained run reports and signing with your existing external
key, use [the WSL builder workflow](tools/README-wsl.md), revision
`20261022-09-memory-cleanup`. It requires its own `signing/` inputs, keeps dependency
caches and attempts to release clean Linux RAM caches after the run. It supports
`--no-memory-cleanup`. Use the current preview source asset for the exact tested CI commit; ZIP36 and
earlier archives remain historical and unchanged. Public `main` can contain newer
publication documentation. The release tag identifies the tested source.

## Contributing and reuse

Start with the [project goal](PROJECT_GOAL.md), [session design](docs/NEWPLAYER_SESSION_INTEGRATION.md)
and [porting review](docs/UPSTREAM_PORTING_20261022.md). Generic NewPlayer changes and
NewPipe-specific hosts have separate ownership; portable patches have text-application
checks, not upstream build or acceptance. No upstream PR has been opened for this work.

When reporting a defect, include the source commit, APK SHA256, Android version,
auth variant and reproduction steps. Keep source review, compiler/tests, emulator
observations and human/device checks separate. Debug diagnostics are extensive:
review logs for credentials and private URLs before sharing. Auth diagnostics are
redacted and disabled in release; account sessions and signing keys are not distributed.
[Publication scope and privacy review](docs/SOURCE_PUBLICATION.md).

## Attribution and license

SloooPipe is an independent experiment, not an official NewPipe release.
GPL-3.0-or-later; see [LICENSE](LICENSE) and component notices. Upstream copyright
and provenance are preserved: [NewPipe](https://github.com/TeamNewPipe/NewPipe),
[NewPlayer](https://codeberg.org/TeamNewPipe/NewPlayer),
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), and
[PipePipe](https://github.com/InfinityLoop1308/PipePipeClient) for adapted optional sign-in.
