# Incoming zero timestamp follow-up — 2026-10-26

## Preview1 device regression

The signed auth-enabled APK from CI 9050389 was installed over APK35 in updated
LDPlayer14.0.32.0, Android14/API34. Installed bytes were re-read and SHA256 matched
`1b7862d1bba6d64fbd479b24c8f16b54df2cd43febc46090eb0e086dc795ae4b`.
Selected preferences and known public-fixture history matched before/after install;
no app-data clear, replacement signing key or network/ADB configuration change.

[Focused matrix](PREVIEW1_RETEST_20261026.csv):19 coverage rows,17 PASS,1 FAIL,
1 BLOCKED. Original APK35 and CI reports remain historical and unchanged.

Passed: font200% header readability, paused recreation, CCC network picture,
automatic Home→popup→native return playing/paused, popup remember ON/OFF and
rotation clamping/restoration, canonical history resume, local document cancellation,
local video/pause/seek/moving frames, local mini controls/reopen/close, local M4A,
queue append/selection and3 native+3 mini queue-return cycles. The app PID stayed
unchanged and its scoped log had no Java FATAL EXCEPTION. Sound on the physical
phone remains deferred; no audible result is claimed.

Fresh and restored YouTube requests hit SignInConfirmNotBotException. This blocks
the YouTube-specific repeat; playback was tested independently with CCC/local
fixtures. No Wi-Fi toggling or attempt to bypass the challenge was performed.

## Confirmed remaining input-path defect

For the known public CCC fixture, saved history was194528ms. Opening its URL with
`?t=0` started around194635ms instead of zero. A canonical URL without timestamp
correctly resumed history. Thus the previous constructor-only fix is incomplete
when the Extractor's originalUrl no longer represents the user's actual URL.

MediaCCCStreamExtractor.getOriginalUrl returns its API frontend_link, which discards
the incoming query. Looking only at StreamInfo.originalUrl loses the explicit zero.
This CCC failure does not establish a new YouTube runtime result; that source is
blocked separately. The intended generic zero-request contract is source-independent.

The follow-up captures the raw requested URL before async detail extraction and
metadata UI normalization. A shared PlaybackStartIntent.createQueue factory uses
that input for fresh queues; RouterActivity's FetcherService also uses it for the
non-autoplay/video/audio/popup entry path. Provided queues are retained rather than
mutated. Parsed positive timestamps keep their existing priority; a normal request
does not inherit a stale zero metadata hint. Without a raw request, metadata remains
the compatibility fallback. No new raw-URL logging or persistence is introduced.

Four new JUnit cases exercise rewritten metadata, ordinary requests with stale-zero
metadata, positive timestamp precedence and absent-request fallback using real
SinglePlayQueue objects. All 63 integration cases, including the 12 timestamp
cases, passed in both CI variants. Static Ktlint/Checkstyle and diff checks pass.

## Follow-up result

Both auth variants passed CI at 2c22439. The exact signed auth-with APK passed the
warm-cache zero reproduction, active zero reuse, ordinary active/fresh history
and non-autoplay Router checks. Font/popup, queue and local-video smoke also passed.
Historical preview1 FAIL/BLOCKED rows above remain unchanged.
See [exact APK, CI/device scope and remaining limits](CI_PREVIEW_20261026.md) and
the [new focused matrix](RAW_ZERO_DEVICE_20261026.csv).
