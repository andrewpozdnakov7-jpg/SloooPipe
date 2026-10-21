# SloooPipe

An experimental integration of **NewPipe with NewPlayer**. The goal is to make familiar
NewPipe workflows work through NewPlayer and provide an implementation that upstream
developers and other projects can study and reuse.

[Русский](README.ru.md)

**Early development.** This repository contains application sources, including the
ZIP23 queue-duration fix. Its APK has not been tested yet; the latest device evidence
is from APK22. No stable release is available.

## Verified behavior

On APK22 in LDPlayer / Android 14:

- Standard Play in a NewPipe video page opens NewPlayer.
- A YouTube video plays at 1280×720. Decoded video, an AAC audio track and output to
  Android's audio mixer are confirmed; audio was not assessed by human listening.
- Pause, seek and play work. Back retains a paused queue and mini-player with the
  correct title; the mini-player resumes playback.
- NewPipe history records the view. Reopening after an application restart resumes
  the saved position.
- “Add next” adds a second video. Android's MEDIA_NEXT command switches to that item,
  and the mini-player updates to the new title.

Earlier APK20 checks covered selected local-file and live-stream playback scenarios.
They have not all been repeated on the current snapshot.
[Detailed device checkpoint and remaining acceptance](docs/MAIN_PLAY_QUEUE_HISTORY_DEVICE_CHECKPOINT.md).

## Known issues and limits

- **APK22 crashes when opening the queue UI** because NewPlayer requires duration
  metadata. ZIP23 supplies known durations and handles unknown/live durations in the
  queue UI and aggregate progress. This fix has static checks only; an APK retest is pending.
- Queue editing, repeat/shuffle, automatic next, long-playlist pagination and the full
  queue lifecycle still need device acceptance.
- Optional YouTube sign-in and authenticated playback are experimental. A captured
  browser session does not establish that a particular video can be played. Full
  SABR and PoToken support are not implemented.
- Background and popup playback still use the legacy player. Full PiP/background
  migration and complete queue restoration after process death are unfinished.
- Physical Android 16 testing and the full lifecycle/source matrix are pending.
- Debug builds contain extensive diagnostics. Auth diagnostics use redaction and are
  disabled in release builds. Inspect logs before sharing them publicly.

## Source layout

| Path | Purpose |
| --- | --- |
| [`NewPipe/`](NewPipe) | Main SloooPipe application on a pinned NewPipe `dev` snapshot |
| [`NewPipe/third-party/NewPlayer/`](NewPipe/third-party/NewPlayer) | Embedded library, module `:newplayer-local` |
| [`NewPipe/app/src/main/java/org/schabi/newpipe/newplayer/`](NewPipe/app/src/main/java/org/schabi/newpipe/newplayer) | Host, lazy sources, queue and history |
| [`NewPipe/youtube-auth-api/`](NewPipe/youtube-auth-api) | Optional auth API and sanitized diagnostics |
| [`NewPipe/third-party/NewPipeExtractor-auth/`](NewPipe/third-party/NewPipeExtractor-auth) | Extractor with optional auth hooks |
| [`NewPlayer/`](NewPlayer) | Separate upstream demo, independent from the embedded library |
| [`tools/`](tools) | WSL ZIP builder and dependency helpers |
| [`docs/`](docs) | Architecture, implementation notes and acceptance evidence |

[Architecture](docs/MAIN_PLAY_QUEUE_HISTORY_STAGE3.md) · [Goal](PROJECT_GOAL.md) ·
[Upstream revisions](UPSTREAM.json) · [Current status](RESULT.md)

## Build and test

The main application pins JDK 21, Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21,
Android SDK platform 37.0 and Build Tools 36.0.0. The first build requires network
access to dependency repositories or a prepared local cache.

For Linux/WSL:

```bash
git clone https://github.com/andrewpozdnakov7-jpg/SloooPipe.git
cd SloooPipe/NewPipe
chmod +x gradlew
./gradlew :app:testDebugUnitTest :youtube-auth-api:test :shared:jvmTest \
  :app:verifyAuthIsolation :app:assembleDebug -Pslooopipe.withAuth=false
```

Set `JAVA_HOME` and `ANDROID_HOME` for your installation. `:shared:jvmTest` needs a
working display; on a headless machine run the Gradle command through `xvfb-run -a`
with Xvfb/xauth installed. Use `-Pslooopipe.withAuth=true` for experimental sign-in.
This is a test-build instruction, not a claim that this snapshot passed all tasks.
The 22 app/NewPlayer JVM cases, including five new duration cases, have not been
run on this computer.

The existing ZIP workflow is described in [`tools/README-wsl.md`](tools/README-wsl.md).
It requires your own signing configuration in the external build directory. No
project signing key or account session is distributed here. The upstream build
derives debug application-ID suffixes from the Git branch; the ZIP builder creates
local `dev` metadata for a deterministic `.debug` suffix.

## Contributing

Start with the device checkpoint and architecture. Report the exact commit,
Android version and authentication mode. Remove cookies, tokens, account details
and private URLs from logs before sharing them.

## Attribution and license

SloooPipe is an independent experiment. Upstream copyright and license notices are
preserved. The application and our changes use GPL-3.0-or-later; see [LICENSE](LICENSE)
and the notices supplied with each component.

- [NewPipe](https://github.com/TeamNewPipe/NewPipe)
- [NewPlayer](https://codeberg.org/TeamNewPipe/NewPlayer)
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
- [PipePipe](https://github.com/InfinityLoop1308/PipePipeClient), adapted optional sign-in components
