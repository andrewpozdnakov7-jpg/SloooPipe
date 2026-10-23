# SloooPipe

An experimental Android integration of **NewPipe with NewPlayer**. The goal is to
make familiar NewPipe workflows use NewPlayer and provide an implementation that
other developers can study, adapt and reuse.

[Русский](README.ru.md) · [Current results](RESULT.md) · [Architecture](docs/NEWPLAYER_SESSION_INTEGRATION.md)

## Development status

**Source checkpoint: ZIP34, 2026-10-23.** Latest device evidence: APK33 in
LDPlayer / Android 14. The stale mini-player after session close passed its regression;
the speed label now reflects actual state. A remaining portrait layout defect wraps
its “x” onto a second line. ZIP34 changes only that button's sizing and single-line
text; this new source change has static checks and still needs an external APK build.
ZIP numbers identify source handoffs, not upstream releases. No stable release is available.

| Area | Implementation and verification |
| --- | --- |
| Network playback | Normal Play uses the Kotlin NewPlayer coordinator. APK33 rendered network video and preserved Play/Pause in 20 queue cycles. |
| Queue and mini-player | 10 native and 10 mini-player cycles completed; active-item retention without selection and paused drift up to 17 ms were recorded. Cancel on another detail page returns to the actual current item. |
| Session close | Notification Close with another detail's replacement dialog open, stale OK and Back leaves no active playback session or stale mini in the checked sequence. |
| Modes | APK33 popup and PiP displayed video, handled Play/Pause and returned with paused position retained. This is a scoped regression, not the complete mode matrix. |
| Local media | The Local service entry opens a document-picker host. APK33 mp4 playback, paused seeking, mini-player and Close were checked; picker/replacement Cancel retained the network session. |
| ZIP34 source fix | Replace the fixed-size speed icon button with a content-sized transparent button and single-line text. New APK verification pending. |
| Optional YouTube sign-in | Experimental and controlled at build time. Signing in does not guarantee that YouTube will allow every video or network address. |

[APK33 evidence and APK hash](docs/APK33_DEVICE_CHECKPOINT.md) ·
[Scoped APK33 matrix](docs/APK33_ACCEPTANCE.csv) ·
[Historical broader APK32 checks](docs/APK32_DEVICE_CHECKPOINT.md).
One automation step needed an explicit fullscreen exit before the last mini-player
cycle; the checkpoint documents that recovery. These device results belong to APK33,
not ZIP34. Audible sound is deferred, at the user's request, to a separate physical-phone check.

A black first frame after selecting an unplayed item while paused at position zero
remains a documented limitation; subsequent Play displays video. Full acceptance is
incomplete: both auth build/test/isolation reports and complete XML/counts, source/
track/history/resume/lifecycle coverage, asynchronous races, network recovery and
physical Android 16 checks remain pending. Legacy player/media-browser classes remain;
Android Auto interoperability is unverified. Explicit external-player and Kodi actions
remain external. Full SABR/PoToken support and a complete refactor-branch port are
outside this checkpoint.

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
validation commands, not a claim that both variants have passed. There are 51
app/NewPlayer JVM test cases in source; complete current-run XML/counts are pending.
A normal checkout build writes APKs under `NewPipe/app/build/outputs/apk/` and uses
its debug signing setup. A different signing certificate cannot update an existing install.

For source-ZIP selection, retained run reports and signing with your existing external
key, use [the WSL builder workflow](tools/README-wsl.md), revision
`20261022-09-memory-cleanup`. It requires its own `signing/` inputs, keeps dependency
caches and attempts to release clean Linux RAM caches after the run. It supports
`--no-memory-cleanup`. The supplied ZIP34 is the current source handoff; earlier archives remain unchanged. Public `main`
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
