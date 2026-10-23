# Signed CI preview — 2026-10-23

[Workflow run39967867977](https://github.com/andrewpozdnakov7-jpg/SloooPipe/actions/runs/39967867977), attempt1, succeeded for both auth variants.
Exact tested source: `9050389f7466faaf5f1dd2719437b40b11bae1e1`.
Tree: `a9cdc6a2d42b9e8d31c662022e79919cce275450`.
[Preview release](https://github.com/andrewpozdnakov7-jpg/SloooPipe/releases/tag/preview-20261023-01) tags this exact commit. The later main documentation
follow-up does not change application sources or the workflow used for these APKs.

## Observed results

| Variant | app:testDebugUnitTest | youtube-auth-api:test | shared:jvmTest | Failures/errors/skips |
| --- | ---: | ---: | ---: | --- |
| with |214 |15 |31 |0 /0 /0 |
| without |214 |15 |31 |0 /0 /0 |

There are 260 executions per variant, 520 across two builds, not 520 unique tests.
The 214 app cases include 59 NewPlayer integration cases, including all 8 new
PlaybackStartIntent cases. Downloaded XML sums match the CI manifests. Each suite
executed tests; no suite is an all-skipped success. Auth isolation verified exactly
one Extractor and hooks1/0 for with/without respectively.

CI signed both APKs using the existing supplied key, checked APK signatures and
zip alignment, and required the configured certificate SHA256 to match. Downloaded
APK hashes and ZIP CRC were checked; certificate identity was also read independently
from both APKs and matches the earlier tested APK35. Actual DEX includes the new
explicit-zero helper. APK ZIP entries have no signing-key/config containers or
known local workstation/user-path strings in the scoped publication check.
This is not an independent security audit or a substitute for device testing.

| Signed APK | SHA256 |
| --- | --- |
| SloooPipe-auth-with-signed.apk | `1b7862d1bba6d64fbd479b24c8f16b54df2cd43febc46090eb0e086dc795ae4b` |
| SloooPipe-auth-without-signed.apk | `f7ade7d24878c5c50a21713530837a2ea635cc09e4cd85ecc9134dc5131d60c2` |

Certificate SHA256: `a9fbac6c498f7ad8e7c56849afe43881966bed2ed2bc1dd1c73d0ffe0b9ebeba`.
Both use `org.schabi.newpipe.debug`, internal version 0.29.1/code 1015. Choose one
variant; they replace each other. APKs are debug builds with diagnostic logging,
published as an experimental prerelease. Review logs before sharing.

## Provenance and remaining acceptance

The full source archive in the release is Git archive of the exact CI commit,
6,220 files checked against Git blobs (two intentional BAT CRLF conversions are
normalized only for comparison). Its application trees match the ZIP36 source
checkpoint; the CI additions concern workflow/signing/reporting and documentation.
SHA256SUMS and BUILD-RESULTS accompany the APKs/source. No private signing file,
password, account session or raw device database is a release asset.

Latest device evidence remains [APK35](APK35_DEVICE_CHECKPOINT.md). The four ZIP36
fixes need new-APK regression: large fonts, explicit-zero/history priority, popup
remember ON/OFF and Home→automatic-popup→native video. Queue/recreation/mode smoke,
physical Android 16/audio and the documented partial cases remain open. Do not turn
the original APK35 FAIL rows into PASS solely from CI. No stable-release acceptance,
upstream PR, automatic update feed or standalone upstream demo result is claimed.
