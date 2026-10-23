# ZIP36 external build update — 2026-10-23

**Later independently reviewed CI update:** both auth variants passed
[run39967867977](https://github.com/andrewpozdnakov7-jpg/SloooPipe/actions/runs/39967867977) with260 executions each, signing and matching certificates.
See [CI verification](CI_PREVIEW_20261023.md) and [preview](https://github.com/andrewpozdnakov7-jpg/SloooPipe/releases/tag/preview-20261023-01). The sections
below preserve the earlier user-reported WSL result; pending build-evidence claims
there do not override the later CI record. New-APK device acceptance is still open.

The user reports the following result from the external WSL build computer:

```text
BUILD SUCCESSFUL in 16m 37s
```

The preceding supplied process command identified source ZIP36 by its SHA256:
`3c796688dc421692135843d651f3cb2edca33ee492d5325fb3adcee94eed695c`.
Source development checkpoint:
`9bef2ccf6c1d47f480d5d49f2efa7d341ced5bc9`.
Public source snapshot containing the same application code:
`c41b6ec4cb15e61e8f047e05441cb85d0b777ff7`.

The command requested:

```text
:app:testDebugUnitTest :youtube-auth-api:test :shared:jvmTest
:app:verifyAuthIsolation :app:assembleDebug
-Pslooopipe.withAuth=true
```

This records user-reported success of the combined auth-enabled Gradle run.
Full console logs, task outcomes, test XML/counts and the resulting APK hash have
not been received or independently reviewed. The 59 integration tests counted in
source are not presented as 59 executed/passing tests. The auth-free variant is
not established by this result. No local APK/Gradle/JVM build was run by the agent.

The reported duration is longer than the user's usual build time. The supplied
process snapshot showed a recently started test executor; no specific cause for
the extra time was established. No toolchain, signing or builder change is made.

## Current acceptance boundary

The latest device regression remains [APK35](APK35_DEVICE_CHECKPOINT.md), with
[its matrix](APK35_ACCEPTANCE.csv). Source changes for the four APK35 defects are
published: explicit-zero/history priority, popup geometry, large-font labels and
automatic-popup native video-surface ownership. Their runtime acceptance is open.

Next: retain the complete WSL results and APK SHA256, then install the newly built
APK and check all four fixes plus queue/recreation/mode smoke tests. Check the
auth-free build/test/isolation separately. Audible sound remains deferred to the
user's physical-phone check; other partial/physical scenarios stay open.

The original ZIP36 source archive and ChatGPT report are historical handoffs and
remain unchanged. This follow-up updates current documentation only; application
code, dependencies, signing setup and builder revision
`20261022-09-memory-cleanup` are unchanged. No stable release, tag or APK asset is
published by this update.
