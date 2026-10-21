# SloooPipe

An experimental Android project based on NewPipe, working toward an integration with NewPlayer.

[Русский](README.ru.md)

> **Early development.** This public repository currently contains the project
> overview and license only. Application sources are being developed locally
> and have not been published here. There is no stable SloooPipe release yet.

## Project goal

Make familiar NewPipe workflows work through NewPlayer and provide an
implementation that upstream developers and other projects can study and reuse.
The project does not need to invent differences from PipePipe or Straw to be useful.

## Current progress

The following describes the local development work, not code available in this repository.

| Area | Status |
| --- | --- |
| Main application | Based on a pinned NewPipe `dev` snapshot dated 2026-10-21; still uses ExoPlayer 2.19.1 |
| NewPlayer | Separate library and test app; integration research and a list of missing connections are prepared |
| YouTube sign-in | Optional implementation adapted from PipePipe; under device debugging |
| Session handling | Explicit capture/import and local storage; captured cookies are distinct from verified access and playback |
| Authenticated playback | Experimental MWEB player backend; full SABR and PoToken support are not implemented |
| Verification | The current dev-base migration has only passed static checks; earlier snapshots built in an external WSL environment |

**NewPlayer is not integrated into the main application yet.** End-to-end YouTube
sign-in and authenticated playback are not confirmed. These are active development
areas, not finished features.

## Planned work

1. Complete and verify the optional YouTube sign-in/session flow.
2. Refresh the selected NewPlayer base and implement the missing integration boundaries.
3. Connect playback controls, queue, background playback, notifications and PiP.
4. Check familiar NewPipe workflows on devices and document reproducible results.
5. Prepare understandable, reusable changes for upstream review.

## Source code and builds

Source code, build scripts, APKs and development logs are not included in this
public repository at this stage. Build instructions and implementation notes will
accompany the sources when they are published. The development history remains local.

## Credits

SloooPipe is an independent experiment, not an official TeamNewPipe release.
Credit for the upstream components belongs to their respective authors:

- [NewPipe](https://github.com/TeamNewPipe/NewPipe) — application and shared code.
- [NewPlayer](https://codeberg.org/TeamNewPipe/NewPlayer) — player library and test app.
- [PipePipe](https://github.com/InfinityLoop1308/PipePipeClient) — adapted sign-in components.
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — media extraction.

## License

The GNU GPL version 3 license text is included in [LICENSE](LICENSE).
Upstream licenses, authorship and copyright notices are preserved in the local
source tree and will accompany the corresponding components when published.
