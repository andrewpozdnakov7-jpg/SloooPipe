# Local Android license metadata

The Android app enables AboutLibraries 15.3.0 `offlineMode` so that generating
library definitions does not download license text from SPDX. This does not
enable Gradle's dependency offline mode or skip dependency metadata generation.

The JSON files in `licenses/` contain the eight complete license texts already
present in upstream's checked-in
`shared/src/androidMain/assets/aboutlibraries.json` at NewPipe commit
`f46290f28ab44fa514bac0a9f0252fd7d2afb66f`. Their `hash` and `spdxId` fields use
the existing SPDX identifiers so AboutLibraries merges them with detected
dependency licenses. The existing custom library definitions are retained.

When dependency updates introduce another license, add its complete text and
metadata here and check the regenerated library definitions. This snapshot
covers the checked-in Android metadata; the resolved dependency graph still
needs verification on the build computer.

The desktop and iOS export configurations are unchanged.
