# Signed preview follow-up — 2026-10-26

Exact CI source: `2c224398638e5699a5c8c55c29259a41a4d21c53`. [Workflow](https://github.com/andrewpozdnakov7-jpg/SloooPipe/actions/runs/40319678374), attempt 1, both auth jobs successful.
Per variant: 218 app + 15 auth API + 31 shared = 264 executions, zero failures,
errors or skips. The app suite includes all 63 NewPlayer integration cases, with
12 PlaybackStartIntent cases (four new). Downloaded XML totals and APK hashes were
checked against manifests. Auth isolation passed with one Extractor and hooks
enabled only in the auth-enabled build. CI apksigner checks passed; both downloaded
APK certificates independently match the previous preview/APK35 identity.

| Variant | APK SHA256 |
| --- | --- |
| with | `c7944090fddad5d40b522061a80f0d2d753a02f99a89907fb93c7f7df3b924a5` |
| without | `310b2c4dbd840d6404f7e910b07b2cdd85c95b8999935475c6f3bb8dd5aa021e` |

Certificate SHA256: `a9fbac6c498f7ad8e7c56849afe43881966bed2ed2bc1dd1c73d0ffe0b9ebeba`.
Package remains `org.schabi.newpipe.debug`; internal version 0.29.1 (1015).
Choose one variant: they replace each other using the same existing signing key.

## Device result

The exact **auth-with** artifact was installed in place in LDPlayer 14.0.32.0,
Android 14/API 34. Installed bytes were re-read and matched. Selected preferences
and known public CCC history survived installation. No data clear, network/ADB
configuration change, replacement key or local APK compilation was used.

[Focused matrix](RAW_ZERO_DEVICE_20261026.csv): 14 rows,
1 BLOCKED, 1 PARTIAL, 12 PASS.
These are coverage rows, not a product-readiness percentage. Evidence filenames
refer to locally retained diagnostics; raw logs, databases and screenshots are not
published with this report. The auth-free APK passed CI/static checks, but was not
installed in this device batch.

The failing warm-cache CCC case is fixed: saved history 194641 ms, incoming `?t=0`,
fresh playback paused around 1459 ms. Active same-item zero request also rewinds;
canonical active requests retain position, and fresh canonical requests resume
saved history. With autoplay disabled, the separate Router FetcherService path
also starts near zero (616 ms). Positive timestamp priority passed unit tests;
CCC does not support positive extractor timestamps, so no positive-CCC runtime
claim is made. Autoplay was restored to its original Wi-Fi setting.

Font 200% exposed all six readable header controls and retained the exact paused
position through recreation. Home→popup→native returned a visible video image in
both playing and paused states; exact paused position remained unchanged. Popup
remember-ON geometry survived reopen, landscape clamped inside the screen, and
portrait restored geometry. Font, rotation and background-minimize defaults were
restored. Native/background-mini queue returns retained the existing queue,
selected index and exact pause. Local picker video, seek, moving frames, pause,
mini controls, reopen and close were exercised. See the matrix for exact scope.

Paused-popup picture after rotation remains PARTIAL: short-wait captures were
black. Repeated rotations preserved exact pause/position; in one repeat the frame
was black after 2 seconds and visible after 8 seconds. A permanent black screen
was not reproduced, but delayed picture is an open issue, not a passed visual
rotation claim. Geometry acceptance is independent of that picture limitation.

The scoped app-PID log showed no Java FATAL EXCEPTION during this batch; this does
not prove absence of all crash types. YouTube playback remains a separate blocked
scenario (not-bot challenge); no YouTube access fix is claimed. Physical Android 16,
audible sound, decoded audio-language/pitch validation and broader partial cases
remain open. Sound was deliberately deferred to the user's phone.

## Historical evidence and limits

[Preview1 matrix](PREVIEW1_RETEST_20261026.csv) remains unchanged: 17 PASS, 1 FAIL
(CCC zero request), 1 BLOCKED (YouTube). The new result closes the CCC defect on a
different exact APK; it does not rewrite historical evidence. The earlier
[APK35 matrix](APK35_ACCEPTANCE.csv) remains the broader coverage record.
Popup remember-OFF and local M4A passed preview1; they were not repeated in this
smaller follow-up. The auth-free APK has no new device result.

This is an experimental debug preview with diagnostic logging enabled, not stable
acceptance. Review logs before sharing. No automatic updater-feed change or upstream
PR is part of this checkpoint. [Signed preview and exact source](https://github.com/andrewpozdnakov7-jpg/SloooPipe/releases/tag/preview-20261026-01).
