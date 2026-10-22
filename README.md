# SloooPipe

An experimental Android integration of **NewPipe with NewPlayer**. The goal is to
make familiar NewPipe workflows use NewPlayer and provide an implementation that
other developers can study, adapt and reuse.

[Русский](README.ru.md) · [Current results](RESULT.md) · [Architecture](docs/NEWPLAYER_SESSION_INTEGRATION.md)

## Development status

**Published source checkpoint: ZIP33, 2026-10-22.** The application code matches
development commit `1ca928c3fa2d6ea0cd0e245b13153ef98f652073`. ZIP33 has static checks;
its new APK has not yet been built or device-tested.

The latest device evidence is APK32 in LDPlayer / Android 14: 20 queue cycles,
ordinary Play, actual repeat/auto-next, queue mutations, quality, background/popup/PiP,
notification controls, local media, live HLS/DVR and saved-queue restoration were checked
in the documented scope. The user confirmed audible sound. Two UI defects were found:
a speed button fixed at “1x” and a stale mini-player after closing the queue while
browsing another detail page. ZIP33 contains source fixes for both. APK32 results do
not establish acceptance of the new APK.
ZIP numbers identify source handoffs, not upstream releases. No stable release is available.

| Area | Implementation and verification |
| --- | --- |
| Network playback | Normal Play, background and popup routes are connected to a Kotlin NewPlayer coordinator. Video, mini-player, fullscreen and PiP share the playback runtime; the complete mode matrix is still pending. |
| Local media | A Local entry in the service selector opens Android's document picker. Local playback uses the same NewPlayer integration and has a mini-player. |
| Queue and history | Media3 owns the active queue. The host connects feeds, selection, enqueue, reorder, removal, saved position and NewPipe history. Implementation is broader than the completed device checks. |
| Latest device check, APK32 | Empty-host dismissal, 20 queue cycles, actual repeat/auto-next, modes, local media and saved queue restore passed in the documented scope. |
| ZIP33 source fixes | Bind the speed button to actual state; retain mini-player ownership across detail browsing and close. New APK retest pending. |
| Optional YouTube sign-in | Experimental, controlled at build time. Signing in does not guarantee that YouTube will allow every video or network address. |

[APK32 evidence and exact APK hash](docs/APK32_DEVICE_CHECKPOINT.md) ·
[Current acceptance matrix](docs/APK32_ACCEPTANCE.csv).
Current results belong to APK32 only; previous checkpoints remain historical evidence.
The full acceptance matrix is incomplete, and the two source fixes need a new APK.

Still required: the full queue/source/mode/lifecycle matrix on the new APK, both auth
builds with test reports, additional source/track/lifecycle checks and physical Android 16 validation. Legacy
player/media-browser classes remain; Android Auto interoperability is unverified.
Explicit external-player and Kodi actions remain external. Full SABR/PoToken support
and a complete port to upstream's refactor branch are outside this checkpoint.

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
`--no-memory-cleanup`. The supplied ZIP33 archive remains unchanged. Public `main`
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
