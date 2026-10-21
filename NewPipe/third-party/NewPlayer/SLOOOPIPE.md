# Embedded NewPlayer

This directory vendors the library's `src/main`, consumer rules, license and
version catalog from official Codeberg `dev` commit
`212ca1c8d9419a19cb5aed7f0a61e22e8732a25c` (2026-10-20). The live Codeberg API
still returned this commit on 2026-10-21.

Source archive: https://codeberg.org/TeamNewPipe/NewPlayer/archive/212ca1c8d9419a19cb5aed7f0a61e22e8732a25c.zip

SHA256: `d3468229bc50266b123579d85b049e5e28c4057561fca1c220cc84ff12836549`.

The original GPL notices and `LICENSE` are retained. The host includes this as
`:newplayer-local`; there is no binary download from JitPack for NewPlayer.
Android Media3 remains at the upstream 1.9.2 version. Gradle, AGP, Kotlin,
compile SDK and Build Tools come from the current SloooPipe/NewPipe host.

Local adaptations:

- Replace the Hilt entry points in the service, view and view model with direct
  construction and `NewPlayerProvider` implemented by the host Application.
- Report media-controller connection failures through `errorFlow`.
- Cancel pending controller connections on release and discard stale callbacks.
- Invalidate queued playback work on release, so closing the screen cannot start
  a previously queued file after the player has been released.
- Only release the service's own ExoPlayer during destruction, so a previous
  service instance cannot release a replacement player created during reopening.
- Use a host build script without Hilt/KSP or Dokka. The host supplies local
  content through the upstream `MediaRepository.getMediaItem` contract and
  `NewPlayerImpl.onNewPlayer` factory.
- Declare Activity Compose and Lifecycle Runtime Compose directly after removing
  Hilt's transitive dependencies; expose Media3 DataSource to the host factory.

The separate repository-root `NewPlayer/` demo is unchanged. It is not the
library embedded by the main SloooPipe APK; use `--component main` to build this
integration. Device playback and compilation must be verified on the build computer.
