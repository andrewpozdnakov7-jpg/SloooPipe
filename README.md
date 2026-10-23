# SloooPipe

An experimental Android integration of **NewPipe with NewPlayer**. The goal is to
make familiar NewPipe workflows use NewPlayer and provide an implementation that
other developers can study, adapt and reuse.

[Русский](README.ru.md) · [Current results](RESULT.md) · [Architecture](docs/NEWPLAYER_SESSION_INTEGRATION.md)

## Development status

**Source checkpoint: ZIP36, 2026-10-23. Latest device evidence: APK35**, tested in
LDPlayer / Android 14. Network Play through NewPlayer, local media, queues/history,
live HLS/DVR, subtitles/chapters, quality/speed and background/popup/PiP were exercised.
The paused-recreation fix from ZIP35 passed at font scales 1.3/2.0/default.
No stable release is available.

**Both auth variants passed [GitHub Actions](https://github.com/andrewpozdnakov7-jpg/SloooPipe/actions/runs/39967867977):** 214 app  + 15 auth API  + 31 shared
tests per variant, zero failures/errors/skips. Downloaded XML totals, APK hashes and
existing signing-certificate identity were checked. [Signed preview and source](https://github.com/andrewpozdnakov7-jpg/SloooPipe/releases/tag/preview-20261023-01) ·
[CI verification record](docs/CI_PREVIEW_20261023.md). New-APK device regression remains pending.

| Area | APK35 evidence and current boundary |
| --- | --- |
| Network and local playback | Moving video, Play/Pause/seek; local audio, muxed progressive, Bandcamp audio and live HLS/DVR checked. Audible sound awaits a physical-phone check. |
| Queue and history |20 native/mini transitions, mutations, repeat/shuffle, 318-item restored queue, history/resume toggles and finished-video restart checked. Fresh `t=0` wrongly restores history. |
| Controls and modes | Quality, actual speed, chapters, visible subtitles, preview modes, background, explicit popup and PiP checked. Automatic popup return loses video; popup forgets position. |
| Lifecycle and recovery | Paused/playing recreation and minimize=None return passed; transport error/Retry and late-request cancellation isolation checked. |
| ZIP36 fixes | Explicit-zero preservation and consistent position priority, popup geometry persistence, large-font labels and automatic popup surface-owner lifetime. Static checks pass; new APK retest pending. |
| Optional YouTube sign-in | Experimental and controlled at build time. It cannot guarantee access to every video or network address. |

[APK35 checkpoint and exact APK hash](docs/APK35_DEVICE_CHECKPOINT.md) ·
[Full matrix](docs/APK35_ACCEPTANCE.csv): 109 coverage rows, 87 PASS, 8 PARTIAL,
5 FAIL, 2 NOT VERIFIED and 7 DEFERRED. Five failing rows describe four unique APK35
defects. Counts include source checks; they are not a product-readiness percentage.
**ZIP36 application sources pass CI for both auth variants; fixes still need device retesting.**

The matrix above preserves the original APK35 batch; later CI evidence is separate.
Full acceptance still needs new-APK device regression and physical Android 16 checks. Audio-language and
pitch controls were exercised, but decoded/acoustic results remain partial. Remote
DASH manifest, scheduled live and post-live audio are unverified; SoundCloud did not
yield a session in the checked fixture. Audible sound is deferred, at the user's
request, to a physical phone. Cold remote paused-zero first frames may remain black;
controlled local zero did render. Legacy/media-browser classes remain; Android Auto,
full SABR/PoToken and a full refactor-branch port are not accepted at this checkpoint.
External-player/Kodi actions remain external. ZIP numbers identify source handoffs.

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

Repeat with `-Pslooopipe.withAuth=false` to check the auth-free variant. These are
validation commands, not a claim that both variants have passed. There are 59
app/NewPlayer integration test cases; all 59 executed successfully per CI variant,
within 214 app tests. Full CI XML/counts were reviewed; the earlier WSL run remains
user-reported without its complete reports.
A normal checkout build writes APKs under `NewPipe/app/build/outputs/apk/` and uses
its debug signing setup. A different signing certificate cannot update an existing install.

For source-ZIP selection, retained run reports and signing with your existing external
key, use [the WSL builder workflow](tools/README-wsl.md), revision
`20261022-09-memory-cleanup`. It requires its own `signing/` inputs, keeps dependency
caches and attempts to release clean Linux RAM caches after the run. It supports
`--no-memory-cleanup`. The supplied ZIP36 is the current source handoff; earlier archives remain unchanged. Public `main`
contains the same application code with refreshed publication documentation; follow
the workflow above to create a source ZIP from a checkout.

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
