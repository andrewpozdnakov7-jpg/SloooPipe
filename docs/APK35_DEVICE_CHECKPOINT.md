# APK35 device checkpoint and ZIP36 source handoff — 2026-10-23

**Later build update:** the user reports a successful external ZIP36 auth-enabled
build/test run in 16m 37s. [Evidence and limits](ZIP36_BUILD_CHECKPOINT.md).
The device results and matrix below remain APK35 observations; they do not accept
the four fixes on a new APK.

The installed APK35 was exercised in LDPlayer, Android 14 / API34, 720×1280.
Package: `org.schabi.newpipe.debug`; SHA256:
`dd75ce32a9ba727b7c814b79e63470fd11919dcef25f4075e616cbc8804f2ae1`;
45,426,082 bytes. The hash was checked again at the end. No APK was compiled or
installed during this run. Full WSL build/test XML and source-to-APK attestation
were not supplied; installed-byte identity does not prove complete build provenance.

Source ZIP35 has SHA256
`ea3a5b944cd3c6963a1a504c1a3ec656695adb9f790f8baa4b3f91999b86af99`.
Its6, 209 file paths match source checkpoint
`2239bd622759f74d688f707fac3ec77268269145`: 6, 207 literal Git blobs match, and two
`.bat` files match after their intentional archive CRLF conversion is normalized.
ZIP36 contains subsequent fixes. Its external build was later reported successful;
it has not been device-tested here.

## Completed batch

[Full matrix](APK35_ACCEPTANCE.csv): 109 rows —87 PASS, 8 PARTIAL, 5 FAIL,
2 NOT VERIFIED and 7 DEFERRED. These are coverage rows, including static/source
checks, not109 independent device tests or a percentage of product readiness.
Five FAIL rows describe four unique confirmed APK35 defects; they remain FAIL
until a new APK verifies the fixes. Two early suspected bugs were rejected after
corrected gesture/end-of-stream timing and are not counted as defects.

* Ordinary NewPipe Play opened NewPlayer; network and local video showed moving
  frames, Play/Pause and seeking. Local audio and network Bandcamp audio played.
  Progressive muxed media, generated YouTube DASH sources and live HLS were covered.
* Paused Activity recreation at font scales 1.3/2.0/default retained pause and
  position. Playing recreation retained intent. This verifies the ZIP35 lifecycle
  fix in the tested cases. Actual speeds1.25/1.5/2 matched engine diagnostics.
* Twenty queue transitions (10 native and 10 mini) retained expected Play/Pause;
  nonselection paused drift was at most20 ms. Reorder, delete before/current/last,
  duplicates, auto-next, RepeatOff/One/All and shuffle were exercised.
* A paginated playlist loaded108 initial items, then212 and 318; middle selection,
  fetch-more, shuffle and a318-item saved queue survived controlled app restart.
* History enabled/disabled, resume enabled/disabled, list-position display,
  enqueue versus selection, completed-video restart and chapter03:44 were checked.
  The explicit-zero exception is a confirmed defect below. Read-only queries
  inspected only known public fixture rows; no raw database is distributed.
* Quality360p/720p, German TTML with visible subtitles, chapter selection,
  preview thumbnails HIGH/LOW/OFF and multiple audio-language choices were checked.
  Audio-language switching preserves queue/intent, but decoded language was not
  independently identified. Pitch/silence controls persisted; acoustic results
  were not measured. ZIP36 adds language/pitch/silence diagnostic fields.
* Fresh live HLS had a900, 000 ms DVR window with live=true/seekable=true,
  moving video, rewind and pause. The fixture later ended; its archive is not
  presented as a new live test. No ordinary live resume history was saved.
* Mini-player, background, explicit popup, drag/clamping, real PiP and notification
  controls worked in the recorded cases. Automatic Home-to-popup return is the
  separate failing path below. Minimize=None paused at 123560 ms; task-focus return
  without a new Intent kept exactly that position and a visible image.
* Confirmation cancel/accept, stale confirmation after close/selection, local
  replacement cancellation and popup/background replacement were exercised.
* Temporary emulator-only HTTPS REJECT produced a visible playback error; removing
  the rule and Retry restored the same queue item. Held requestA could not replace
  selectedB (12 samples); a held request could not revive a closed session
  (10 samples). Every exact temporary rule was removed and cleanup recorded.
* Actual external Gallery playback caused focus loss; return left SloooPipe paused.
  A package-limited synthetic noisy event paused playback. These do not establish
  physical headset/call or transient focus-gain behavior.
* A controlled local colorbar video rendered paused position 0 with frame counter
  00:00:00.000. This does not prove all cold remote paused-zero first frames render.

## Four confirmed defects and source fixes

| APK35 defect | Evidence | ZIP36 change; runtime acceptance pending |
| --- | --- | --- |
| Header labels split/truncate at font 2.0 |003-recreate-paused-font 20 | Preserve resource casing on native/popup buttons; allow three lines on mode labels. |
| Fresh URL with `t=0` resumes saved 93167 ms; `t=90` correctly starts 90000 |121/122; history-timestamp-cases.json | Preserve explicit zero from original URL in PlayQueueItem before canonicalization; use the same explicit-recovery-first policy on fresh and reused queues. |
| Enabled popup remember-position setting is ignored |130b/133; popup-position-memory.json | Read/write existing width/x/y preferences and clamp restored geometry; remember-off uses default geometry. Resize gestures are not added. |
| Home automatically opens popup; Open Player returns black video while playback position advances |156/157/158 | Finish the stopped native Activity after automatic popup handoff, matching the working explicit popup action; next native host gets a fresh surface. |

The last defect was reproduced both paused and playing. The source fix does not
seek, replay a queue or create a second player; it changes the surface-owner
lifetime. It specifically requires the external APK Home/popup/return regression.
No claim is made that a source-reviewed fix has already passed on-device.

## Static verification of ZIP36

Six production files changed and one new test file was added. Ktlint is clean on
the six changed/new Kotlin files; Checkstyle is clean on PlayQueueItem.java;
`git diff --check` passes. Eight new JUnit cases exercise the real StreamInfo,
PlayQueueItem, SinglePlayQueue and start/resume policies. Total integration source
inventory is 59 `@Test` cases. None were compiled/executed on this Windows host.

Builder revision `20261022-09-memory-cleanup`, signing inputs, dependency pins and
the generic NewPlayer patch are unchanged. The13-file generic patch was applied
again to pristine pinned official NewPlayer, reverse-checked, with Hilt preserved.
Its7 portable contract cases are separate from the 59 app cases. Text application
does not prove an upstream build, demo run or acceptance. No upstream PR exists.

## Remaining limits and next acceptance

* Build/test both auth variants externally, retain full XML/counts, isolation
  results and APK hash. ZIP36 has a user-reported auth-enabled build success;
  full compiler/test reports, the auth-free run and new-APK device results remain pending.
* Retest all four fixes together: font 2.0, fresh/reused explicit-zero versus saved
  history and positive timestamps, remember-position ON/OFF plus rotation/clamping,
  automatic popup return while playing and paused. Repeat queue/recreation/mode
  smoke checks and verify new diagnostic language/pitch/silence fields.
* Scheduled-not-started stream and remote DASH manifest are not verified.
  SoundCloud fixture produced no playback session; its cause was not isolated.
  Post-live audio, real account mutation and decoded audio-language confirmation
  remain incomplete. Do not infer them from other passing source types.
* Transient audio-focus gain, physical headset/call, Android 16/OnePlus 13, heap/native
  leaks and audible sound remain incomplete. At the user's request, audible sound
  is deferred to a physical phone. No new sound confirmation was requested here.
* A newly selected cold remote item paused at zero may show a black first frame;
  that broader limitation remains unresolved. Controlled local zero did render.
  Legacy/media-browser classes remain; Android Auto and full SABR/PoToken are not
  accepted. YouTube account/IP access is not guaranteed by this integration.

During an early Wi-Fi interruption test ADB became unavailable. Recovery involved
LDPlayer restart, switching its ADB setting to local, and enabling Android Wi-Fi;
the individual causes were not isolated. Later fault injection kept Wi-Fi and ADB
intact. All temporary firewall rules were removed. Final state: original background
minimize setting, font 1.0, rotation0, Wi-Fi enabled, speed/pitch1, skip-silenceOFF,
original detached-pitchON, queue1 paused at 123560. Existing app data/signing retained.

The private evidence set contains screenshots/XML, filtered diagnostics and fixture
records. The ChatGPT handoff includes selected reviewed evidence, changes and this
matrix. Raw APKs, databases, account sessions, general logcat, notification dumps,
network identifiers and signing files are excluded from public Git and reports.
