# First source publication — 2026-10-21

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
