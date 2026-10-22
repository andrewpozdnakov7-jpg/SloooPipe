# Source publication — 2026-10-22

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

The latest installed-device evidence is APK31 in LDPlayer14 / Android14. Repeat and
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
and decoding. Its 1,840 candidates in 148 files are classified by exact Git blob
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
  reported 1,840 candidates in 148 files. All candidate-containing files match the
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
