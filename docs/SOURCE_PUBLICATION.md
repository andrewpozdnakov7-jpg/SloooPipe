# ZIP36 successful-build follow-up — 2026-10-23

The user reports `BUILD SUCCESSFUL in 16m 37s` for the external ZIP36 auth-enabled
build/test run. [Build checkpoint](ZIP36_BUILD_CHECKPOINT.md) records the source
hash, requested tasks and evidence limits. Full logs/XML/counts, the resulting APK
hash, auth-free validation and new-APK device regression remain pending.

Application sources from public snapshot `c41b6ec4cb15e61e8f047e05441cb85d0b777ff7`
are unchanged. Both READMEs, RESULT, current provenance/status and the APK35 document
now link the follow-up. Original ZIP/report artifacts and the APK35 acceptance
matrix remain unchanged; no APK36 device PASS is inferred from build success.
The builder, signing inputs and dependencies are unchanged.

This documentation-only commit continues public main without importing private
history. Changed publication files receive secret/private-data checks; the rest of
the tree is verified identical to the already-reviewed public snapshot. No stable
Release, tag, APK asset or upstream PR is created. Earlier records below retain
status as of their original handoff; this follow-up supersedes their unbuilt claim
for ZIP36 only to the extent of the user-reported external build result.

---

# ZIP36 source publication — 2026-10-23

Latest device evidence is APK35, SHA256
`dd75ce32a9ba727b7c814b79e63470fd11919dcef25f4075e616cbc8804f2ae1`.
[Checkpoint](APK35_DEVICE_CHECKPOINT.md) · [Matrix](APK35_ACCEPTANCE.csv).
The109 coverage rows contain87 PASS, 8 PARTIAL, 5 FAIL (four unique APK35 defects),
2 NOT VERIFIED and 7 DEFERRED. These include source checks, not109 independent tests.

ZIP36 fixes explicit-zero preservation/position priority, popup geometry preferences,
large-font labels and automatic-popup native surface ownership. Eight new app JUnit
cases bring the source inventory to 59; language/pitch/silence diagnostic fields aid
the next run. Changed Kotlin/Java static checks pass, but no APK36 compile/JVM/device
result is claimed. Both external auth configurations and physical checks remain open.

Both root READMEs, RESULT and provenance/status records describe these boundaries.
The WSL builder09, signing setup, dependencies and 13-file generic NewPlayer patch
are unchanged. Generic patch pristine-pin application/reverse/Hilt checks passed
again; upstream compile/test/demo/acceptance is not claimed. Old source/report
archives remain immutable. No stable Release, tag, APK asset or upstream PR is made.

Publication continues public main using a source-snapshot commit, without importing
private development history. The final snapshot and new commit receive static
secret/private-data checks; unchanged upstream scanner candidates require exact
public blob provenance. Keys, sessions, APKs, raw databases, general logcat and
identifier-bearing system captures are excluded. This source-publication review
does not establish device acceptance or an independent security audit.

The entries below are historical; their current/pending claims apply to the original
checkpoint. They do not override the APK35/ZIP36 status above.

---

# ZIP35 source publication — 2026-10-23

APK34 verified the previous speed-button layout fix but exposed a paused-recreation
defect. [APK34 checkpoint](APK34_DEVICE_CHECKPOINT.md) · [Matrix](APK34_ACCEPTANCE.csv).
Tested APK SHA256:
`67367d66d0f47080277ad30076e83250d8242ecc8ffc5a65e18126c7ec0c5b8b`.
ZIP35 changes only `NewPlayerActivity.kt`: the initial Resume command in `onCreate`
is gated on a null saved state. Explicit new-intent Play remains unchanged.
Ktlint0 before/after and diff check pass; the new APK fix is not built/device-tested here.

This source snapshot updates EN/RU READMEs and current status. Previous ZIP34/source/
report files are immutable. Dependencies, builder09, signing inputs and the generic
NewPlayer patch are unchanged. Publication continues public main without private
development history; no stable Release, tag, APK asset or upstream PR is created.
The final snapshot and new commit receive a static privacy/secret review; unchanged
upstream findings use exact public-blob provenance. Raw APKs, general logcat, system
captures with identifiers, keys and account sessions remain outside public Git.
This is not full device acceptance or an independent security audit.

The records below are historical; “latest APK”, “pending” and publication statements
refer to their original checkpoint, not the current root README.

---

# ZIP34 source publication — 2026-10-23

This snapshot adds the single-line, content-sized speed button in embedded NewPlayer
`TopUI.kt`, updates both root READMEs and records the scoped APK33 regression.
[APK33 checkpoint](APK33_DEVICE_CHECKPOINT.md) · [Matrix](APK33_ACCEPTANCE.csv).
The tested APK hash is
`3ee2ba78cdfaa35c40e9304e3a152d7a35de24783a7260e5857381755ee2e958`.
The original ZIP33 and previous handoffs remain unchanged. ZIP34 itself still needs
an external build and device retest. Sound is deferred to a physical-phone check.

Publication continues public `main` with a source snapshot, without merging private
development history. No stable Release, tag, APK asset or upstream PR is created.
The final snapshot and new public commit are checked for secrets/private data;
unchanged upstream findings retain exact public-blob provenance. Keys, sessions,
raw APKs/general logcat and system captures with device/network identifiers stay
outside public Git. This is a static publication review, not a complete security audit.

The entries below are historical; their “latest APK” and publication status refer
to their respective checkpoint dates.

---

# Source publication: ZIP33 — 2026-10-22

Public `main` contains the application code from development checkpoint
`1ca928c3fa2d6ea0cd0e245b13153ef98f652073` (ZIP33), with refreshed EN/RU READMEs
and publication/status documents. The original source archive remains unchanged:
`SloooPipe-sources-20261022-33.zip`, SHA256
`d676bf647d4b1b051700c76c7ff81bdaf588078723d56c2c647adc24cb6d7e5d`.
The builder remains revision `20261022-09-memory-cleanup`; dependencies are unchanged.

## Changes and verification

The latest installed-device evidence is APK32, SHA256
`a42439f13958d0b48616d5d7e6ac94bd5f8f92175b60a3ae23fd26d98c6f40b8`, in
LDPlayer / Android 14. Ordinary Play, 20 queue cycles, actual repeat/auto-next,
queue mutations, quality, background/popup/PiP, notifications, local video/audio,
live HLS/DVR and saved-queue restoration were checked in the documented scope.
The user confirmed audible sound. See the [APK32 checkpoint](APK32_DEVICE_CHECKPOINT.md)
and [acceptance matrix](APK32_ACCEPTANCE.csv): 53 PASS, 30 NOTRUN, 10 PARTIAL and
3 FAIL rows. Three FAIL rows describe two unique UI defects; this is not full acceptance.

ZIP33 changes two production files: `VideoDetailFragment.java` retains NewPlayer
mini-player ownership across detail browsing and suppresses a stale mini after
session close; embedded NewPlayer `TopUI.kt` shows actual speed instead of a literal
“1x”. Changed Java passes Checkstyle. The Kotlin file has 24 pre-existing ktlint
findings, with no new finding or parser error; it is not fully lint-clean.
The cumulative generic patch was completed and text-applied to the pinned official
NewPlayer source with Hilt preserved. No upstream compile, demo run or PR is claimed.

ZIP33 has not yet been built or device-tested. Both auth build/test/isolation reports,
full test XML/counts, new-APK regression and physical Android 16 remain pending.
APK32 observations do not establish acceptance of ZIP33. Source-to-APK provenance
for the installed APK is not fully attested. [Current results](../RESULT.md).

## Publication and privacy scope

This is one source-snapshot commit continuing the existing public `main`; private
development history is not merged. No stable APK, Release, tag or upstream PR is
created. New commit metadata uses the public GitHub noreply identity.

The full final source snapshot is checked with Gitleaks 8.30.1, including nested
archives and decoding, and a separate local-path/workstation/email/private-network/
credential-container scan. Upstream scanner candidates are classified by exact
Git blob identity against pinned public sources, not blanket vendor exclusions.
Public attribution, synthetic test inputs and portable placeholders are distinguished
from private project data. No unresolved project-specific secret or private-data
finding remains in the reviewed snapshot. Detailed scanner reports stay outside Git.

Signing keys, account sessions, private general logcat, raw APKs, local build
configuration and device captures containing network identifiers are not published.
This is a static source-publication review, not a guarantee of anonymity in use or
an independent security audit. Debug diagnostics remain extensive; review logs before
sharing. Auth diagnostics redact sensitive data and are disabled in release.

Historical entries below retain their original verification boundaries; their old
“latest APK” or “not published” statements are not the current repository status.

---

# Historical ZIP32 source publication — 2026-10-22

This update replaces the public ZIP23 snapshot with application code matching
local ZIP32 checkpoint `d638f97f432d74a89cd1231fba5b7e918bef31ca`.
The original ZIP32 SHA256 is
`902663459fb51b670231468e2d96bfcce2a6ef4715becc4402eb779965ac0b9f`.
The source archive itself is unchanged; publication updates the root READMEs,
status/provenance metadata, build instructions and current-document pointers.
The builder remains revision `20261022-09-memory-cleanup`.

The update continues public `main` with a new source snapshot. Private development
history is not merged into public history. No stable APK, Release, tag or upstream
PR is created by this update. Historical checkpoints below and elsewhere describe
their dates; their old “not published” statements are not the current repository status.

## Functional scope

The latest installed-device evidence is APK31 in LDPlayer14 / Android 14. Repeat and
shuffle state after queue reopen, release after last-item removal, mini removal after
Back, new-queue visible playback and pause were checked. Automatic dismissal of the
empty player screen failed. ZIP32 addresses the release-event ordering in that host;
its new APK has not yet been built or device-tested by the reviewer.

[APK31 checkpoint](APK31_DEVICE_CHECKPOINT.md) · [Matrix](APK31_ACCEPTANCE.csv) ·
[Current results](../RESULT.md). APK29's 10+10 queue cycles and APK25's human sound
confirmation remain historical evidence. They are not acceptance of this snapshot.
Both auth variants, complete test XML/counts and full device acceptance are pending.

## Source privacy review

The full publication snapshot is scanned with Gitleaks 8.30.1, with archive traversal
and decoding. Its 1, 840 candidates in 148 files are classified by exact Git blob
identity against the pinned public upstream files reviewed at the first publication:
147 extractor fixture files and one file containing public YouTube client constants.
No project-specific secret finding remains unresolved. Vendored directories are not
blanket-excluded from this review.

Local-path, workstation, email, private-network and credential-container patterns
are checked across files and nested archives. Existing public attribution, synthetic
test inputs and portable placeholders are classified separately from private data.
Signing keys, account sessions, private raw logs, APKs and local build configuration
are excluded from this source snapshot. Detailed scanner reports remain outside Git.
New public commit metadata uses the GitHub noreply identity.

This is a static source-publication review. It does not certify anonymity during
network use or replace functional/device testing or an independent security audit.
Debug diagnostics remain extensive; inspect logs before sharing. Auth diagnostics
redact sensitive data and are disabled in release.

---

## Historical first source publication — 2026-10-21

The source snapshot is based on development checkpoint
`2c5127c7fa8741f662c97a7dcada3cfdf901cf4a` (ZIP23).
Application, test, build-script and vendored source contents match that checkpoint.
Publication changes concern the README, documentation, portable path examples and
rules excluding local credentials and diagnostics.

## Privacy review scope

- Reviewed the complete source snapshot, filenames and nested archive contents.
- Scanned with Gitleaks 8.30.1, including archive traversal and decoding. The scanner
  reported 1, 840 candidates in 148 files. All candidate-containing files match the
  pinned public NewPipe or NewPipeExtractor Git blobs byte for byte: 147 fixture
  files and one upstream file containing public YouTube client constants.
- Checked local paths, email-like strings, machine identifiers and private-network
  addresses. Local user paths in our build documentation were replaced with portable
  examples. Upstream attribution, public upstream data, synthetic test inputs and
  Kotlin labelled expressions were distinguished from project personal data.
- No project signing key, private account export, private log or project-specific
  credential was identified in the reviewed snapshot. Raw audit reports and device
  captures are kept outside the public source tree.
- The publication continues the existing public documentation history with a source
  snapshot. Its reachable commits and author metadata are included in the final
  publication check. Development work remains available in the local repository.
- Auth diagnostics install a sink only for debug builds. Their structured logger
  redacts sensitive values; WebView remote debugging is disabled. Debug logging is
  still extensive, and logs require review before sharing.

This is a static source-publication review. It does not certify anonymity during
network use, validate every upstream fixture, or replace device and security testing.
Public upstream provenance is the basis for classifying the scanner candidates;
the contents of credentials were not tested against remote services.

## Functional verification

The latest device evidence is from APK22. The queue UI crash was reproduced there.
The ZIP23 fix has static checks and five additional JVM test cases, but its APK and
the JVM cases have not been executed on this computer.

See [the device checkpoint](MAIN_PLAY_QUEUE_HISTORY_DEVICE_CHECKPOINT.md) and
[current status](../RESULT.md). No stable APK release accompanies this source snapshot.
