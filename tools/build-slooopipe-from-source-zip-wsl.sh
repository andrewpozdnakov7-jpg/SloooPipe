#!/usr/bin/env bash
# Run on the build computer in WSL. This script does not install tools or SDKs.
set -Eeuo pipefail

SCRIPT_REVISION="20261021-08-current-upstream"
# Minimum baseline taken from the current Slooop checkout; newer inputs stay newer.
MIN_GRADLE="9.6.1"
MIN_AGP="9.4.0"
MIN_COMPILE_SDK=37
BASE_DIR="${SLOOOPIPE_BASE_DIR:-${HOME}/projects/slooopipe-src}"
GRADLE_HOME_OVERRIDE="${SLOOOPIPE_GRADLE_USER_HOME:-}"
LOCAL_MAVEN_INIT=""
AUTH_MODE="${SLOOOPIPE_AUTH_MODE:-without}"
COMPONENT=""
OPERATION=""
MAIN_ZIP=""
PLAYER_ZIP=""
FRESH=0
RUN_TESTS=0
BUILD_APK=1
FULL_LOG=0
BUILD_PHASE="preparation"
OUT_DIR=""
MAIN_DIR=""
PLAYER_DIR=""
MAIN_TESTS_STARTED=0
PLAYER_TESTS_STARTED=0
declare -A EXTRACTED=()
declare -a SELECTED=()

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
	cat <<'USAGE'
SloooPipe source ZIP builder for WSL

Usage: bash build-slooopipe-from-source-zip-wsl.sh [options]
  --base-dir DIR                 Default: ~/projects/slooopipe-src
  --gradle-user-home DIR         Default: existing GRADLE_USER_HOME or ~/.gradle
  --component main|player|both   NewPipe / NewPlayer test-app / both
  --operation build|build-test|build-test-log|test
  --newpipe-zip FILE             ZIP containing the NewPipe project
  --newplayer-zip FILE           ZIP containing NewPlayer and test-app
  --auth with|without           Main app only; default without
  --fresh                       Extract into a new directory; preserve old work
  --help / --version

Missing component/operation/archive choices are prompted interactively.
ZIPs go in BASE/sources; keys and keystore.properties in BASE/signing.
Each run gets a new BASE/out/run-* directory. Work is retained under BASE/work.
Both components may use the same combined ZIP or separate ZIPs.
BASE/offline-maven is used automatically when a validated mirror is present.
No device tests or playback checks are run by this script.
USAGE
}

require_command() { command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"; }

choose_operation() {
	local choice
	while [[ -z "$OPERATION" ]]; do
		printf '\nOperation:\n  1) Build APK\n  2) Build APK + unit tests\n  3) Build APK + unit tests + full log\n  4) Unit tests only\n'
		read -r -p 'Operation [1/2/3/4]: ' choice || die 'No operation selected.'
		case "$choice" in
			1) OPERATION=build ;;
			2) OPERATION=build-test ;;
			3) OPERATION=build-test-log ;;
			4) OPERATION=test ;;
		esac
	done
	case "$OPERATION" in
		build) ;;
		build-test) RUN_TESTS=1 ;;
		build-test-log) RUN_TESTS=1; FULL_LOG=1 ;;
		test) RUN_TESTS=1; BUILD_APK=0 ;;
		*) die "Unknown operation: $OPERATION" ;;
	esac
}

choose_component() {
	local choice
	while [[ -z "$COMPONENT" ]]; do
		printf '\nComponent:\n  1) SloooPipe / NewPipe Android app\n  2) NewPlayer test-app\n  3) Both (independent builds)\n'
		read -r -p 'Component [1/2/3]: ' choice || die 'No component selected.'
		case "$choice" in 1) COMPONENT=main ;; 2) COMPONENT=player ;; 3) COMPONENT=both ;; esac
	done
	case "$COMPONENT" in
		main) SELECTED=(main) ;;
		player) SELECTED=(player) ;;
		both) SELECTED=(main player) ;;
		*) die "Unknown component: $COMPONENT" ;;
	esac
}

# Inspect without Gradle. Extraction rejects links, absolute paths and traversal.
# Git metadata from an archive is not used on the build computer.
archive_action() {
	python3 - "$@" <<'PY'
import pathlib
import re
import shutil
import stat
import sys
import zipfile

action, archive, component = sys.argv[1:4]
try:
    with zipfile.ZipFile(archive) as z:
        entries = []
        names = set()
        for info in z.infolist():
            name = info.filename
            p = pathlib.PurePosixPath(name)
            if (not name or '\\' in name or p.is_absolute() or '..' in p.parts
                    or re.match(r'^[A-Za-z]:', name) or any(ord(c) < 32 for c in name)):
                raise ValueError('Unsafe archive entry')
            mode = (info.external_attr >> 16) & 0xffff
            if stat.S_ISLNK(mode):
                raise ValueError('Symlinks are not supported in source ZIPs')
            if '.git' in p.parts:
                continue
            normalized = p.as_posix()
            if normalized in names:
                raise ValueError('Duplicate archive path: ' + normalized)
            names.add(normalized)
            entries.append((info, p))
        module = 'app' if component == 'main' else 'test-app'
        roots = []
        for name in sorted(names):
            suffix = module + '/build.gradle.kts'
            if name != suffix and not name.endswith('/' + suffix):
                continue
            root = name[:-len(suffix)]
            if root + 'settings.gradle.kts' not in names:
                continue
            if component == 'player' and root + 'new-player/build.gradle.kts' not in names:
                continue
            roots.append(root.rstrip('/') or '.')
        if len(roots) != 1:
            raise ValueError(f'Expected exactly one {component} project, found {len(roots)}')
        if action == 'inspect':
            print(roots[0])
        elif action == 'extract':
            target = pathlib.Path(sys.argv[4]).resolve()
            for info, p in entries:
                dest = target.joinpath(*p.parts)
                if not dest.resolve().is_relative_to(target):
                    raise ValueError('Archive path escapes extraction directory')
                if info.is_dir():
                    dest.mkdir(parents=True, exist_ok=True)
                else:
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(info) as src, dest.open('wb') as dst:
                        shutil.copyfileobj(src, dst)
        else:
            raise ValueError('Unknown archive action')
except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as exc:
    sys.exit(f'Archive error ({pathlib.Path(archive).name}): {exc}')
PY
}

choose_zip() {
	local component="$1" file choice i
	local -a candidates=()
	while IFS= read -r -d '' file; do
		if archive_action inspect "$file" "$component" >/dev/null 2>&1; then
			candidates+=("$file")
		fi
	done < <(find "$SOURCES_DIR" -maxdepth 1 -type f -iname '*.zip' -print0 | sort -z)
	[[ ${#candidates[@]} -gt 0 ]] || die "No suitable $component source ZIP in $SOURCES_DIR"
	if [[ ${#candidates[@]} -eq 1 ]]; then printf '%s\n' "${candidates[0]}"; return; fi
	printf '\nSelect %s source ZIP:\n' "$component" >&2
	for i in "${!candidates[@]}"; do
		printf '  %d) %s\n' "$((i + 1))" "$(basename "${candidates[$i]}")" >&2
	done
	while true; do
		read -r -p 'Archive number: ' choice || die 'No archive selected.'
		if [[ "$choice" =~ ^[0-9]{1,6}$ ]] && ((10#$choice >= 1 && 10#$choice <= ${#candidates[@]})); then
			printf '%s\n' "${candidates[$((10#$choice - 1))]}"; return
		fi
		printf 'Invalid selection.\n' >&2
	done
}

prepare_archive() {
	local archive="$1" component="$2" relative sha directory stored
	archive="$(realpath -e -- "$archive")"
	relative="$(archive_action inspect "$archive" "$component")"
	sha="$(sha256sum -- "$archive")"; sha="${sha%% *}"
	if [[ -n "${EXTRACTED[$sha]:-}" ]]; then
		directory="${EXTRACTED[$sha]}"
	else
		directory="$WORK_ROOT/$sha"
		if [[ "$FRESH" == 1 ]]; then directory="$(mktemp -d "$WORK_ROOT/${sha:0:16}.fresh.XXXXXXXX")"; fi
		if [[ -e "$directory/.slooopipe-source-sha256" ]]; then
			stored="$(cat "$directory/.slooopipe-source-sha256")"
			[[ "$stored" == "$sha" ]] || die 'Work directory marker mismatch. Use --fresh.'
			printf 'Reusing extracted archive: %s\n' "$directory"
		else
			if [[ -d "$directory" ]] && [[ -n "$(find "$directory" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
				die "Incomplete work directory: $directory. Retry with --fresh."
			fi
			mkdir -p "$directory"
			archive_action extract "$archive" "$component" "$directory"
			local after; after="$(sha256sum -- "$archive")"
			[[ "${after%% *}" == "$sha" ]] || die 'Source ZIP changed during extraction. Retry with --fresh.'
			printf '%s\n' "$sha" > "$directory/.slooopipe-source-sha256"
		fi
		EXTRACTED[$sha]="$directory"
	fi
	PREPARED_DIR="$(realpath -e -- "$directory/$relative")"
	printf '%s ZIP: %s\nSHA256: %s\nProject: %s\n' "$component" "$archive" "$sha" "$PREPARED_DIR" | tee -a "$OUT_DIR/BUILD_INFO.txt"
}

validate_wrapper() {
	local project="$1"
	[[ -s "$project/gradlew" && -s "$project/gradle/wrapper/gradle-wrapper.properties" ]] || die "Incomplete Gradle wrapper: $project"
	python3 - "$project/gradle/wrapper/gradle-wrapper.jar" <<'PY'
import sys, zipfile
try:
    with zipfile.ZipFile(sys.argv[1]) as z:
        if 'org/gradle/wrapper/GradleWrapperMain.class' not in z.namelist() or z.testzip():
            raise ValueError('Missing wrapper main class or damaged JAR')
except (OSError, ValueError, zipfile.BadZipFile) as exc:
    sys.exit(f'Invalid Gradle wrapper: {exc}')
PY
	# Only the extracted wrapper is normalized; Kotlin/Java/Gradle sources are untouched.
	sed -i 's/\r$//' "$project/gradlew"
	chmod +x "$project/gradlew"
}

prepare_project() {
	local project="$1"
	validate_wrapper "$project"
	# NewPipe reads git rev-parse during Gradle configuration. ZIPs have no .git.
	# A local, empty dev commit makes its package suffix deterministic (.debug).
	if [[ ! -d "$project/.git" ]]; then
		git -c init.templateDir= init --quiet --initial-branch=dev "$project"
		git -C "$project" -c core.hooksPath=/dev/null -c commit.gpgsign=false \
			-c user.name='SloooPipe local build' -c user.email='local-build@invalid' \
			commit --quiet --allow-empty --no-verify -m 'Local source ZIP build metadata'
	fi
	python3 - "$project/local.properties" "$ANDROID_HOME" <<'PY'
import pathlib, sys
value = sys.argv[2].replace('\\', '\\\\').replace(':', '\\:').replace(' ', '\\ ')
pathlib.Path(sys.argv[1]).write_text('sdk.dir=' + value + '\n', encoding='utf-8')
PY
	{
		printf '\nToolchain declared by %s:\n' "$project"
		cat "$project/gradle/wrapper/gradle-wrapper.properties"
		grep -E '^(agp|kotlin|ksp|kspVersion|hilt|hiltAndroid|media3)[[:space:]]*=' "$project/gradle/libs.versions.toml" || true
	} >> "$OUT_DIR/BUILD_INFO.txt"
}

require_sdk_platforms() {
	python3 - "$ANDROID_HOME" "$MAIN_DIR" "$PLAYER_DIR" "$MIN_GRADLE" "$MIN_AGP" "$MIN_COMPILE_SDK" <<'PY'
import pathlib, re, sys
sdk = pathlib.Path(sys.argv[1])
required = set()
build_tools = set()
def platform_name(major, minor=0):
    # Google publishes API 37 as android-37.0, including the zero minor.
    return f'android-{major}.{minor}' if major >= 37 or minor else f'android-{major}'
def version(value):
    if not re.fullmatch(r'\d+(\.\d+)*', value):
        sys.exit('Expected a stable numeric tool version: ' + value)
    return tuple(map(int, value.split('.')))
for directory in sys.argv[2:4]:
    if not directory:
        continue
    root = pathlib.Path(directory)
    wrapper = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    gradle = re.search(r'/gradle-([0-9.]+)-(?:bin|all)\.zip', wrapper)
    agp = re.search(r'^agp\s*=\s*"([^"]+)"', (root / 'gradle/libs.versions.toml').read_text(), re.M)
    if not gradle or version(gradle[1]) < version(sys.argv[4]):
        sys.exit('Source ZIP Gradle is below the Slooop baseline (' + sys.argv[4] + '). Use the migrated source ZIP.')
    if not agp or version(agp[1]) < version(sys.argv[5]):
        sys.exit('Source ZIP AGP is below the Slooop baseline (' + sys.argv[5] + '). Use the migrated source ZIP.')
    for module in ('app', 'new-player', 'test-app'):
        recipe = root / module / 'build.gradle.kts'
        if recipe.is_file():
            match = re.search(r'\bbuildToolsVersion\s*=\s*"([^"]+)"', recipe.read_text())
            if match:
                if version(match[1]) < version('36.0.0'):
                    sys.exit('Source ZIP Build Tools version is below the Slooop baseline')
                build_tools.add(match[1])
if sys.argv[2]:
    config = pathlib.Path(sys.argv[2], 'buildSrc/src/main/kotlin/ProjectConfig.kt').read_text()
    def number(name):
        match = re.search(r'\b' + name + r'\s*=\s*(\d+)', config)
        if not match:
            sys.exit('Cannot determine SDK version from ProjectConfig.kt: ' + name)
        return int(match[1])
    major = number('NEWPIPE_VERSION_SDK_COMPILE_MAJOR')
    minor = number('NEWPIPE_VERSION_SDK_COMPILE_MINOR')
    if major < int(sys.argv[6]):
        sys.exit('NewPipe compileSdk is below the Slooop baseline')
    required.add(platform_name(major, minor))
if sys.argv[3]:
    for module in ('new-player', 'test-app'):
        config = pathlib.Path(sys.argv[3], module, 'build.gradle.kts').read_text()
        match = re.search(r'\bcompileSdk\s*=\s*(\d+)', config)
        if not match:
            sys.exit('Cannot determine NewPlayer compileSdk in ' + module)
        if int(match[1]) < int(sys.argv[6]):
            sys.exit('NewPlayer compileSdk is below the Slooop baseline')
        required.add(platform_name(int(match[1])))
missing = [name for name in sorted(required) if not (sdk / 'platforms' / name / 'android.jar').is_file()]
print('Required SDK platforms: ' + ', '.join(sorted(required)))
if missing:
    sys.exit('Install SDK platform(s) on the build computer: ' + ', '.join(missing))
for tools in sorted(build_tools):
    if not (sdk / 'build-tools' / tools / 'source.properties').is_file():
        sys.exit('Install SDK Build Tools requested by the project: ' + tools)
print('Required SDK Build Tools: ' + ', '.join(sorted(build_tools)))
PY
}

prepare_signing() {
	local data
	# Same property names and relative-path rule as the supplied Slooop builder.
	data="$(python3 - "$SIGNING_DIR" <<'PY'
import pathlib, sys
directory = pathlib.Path(sys.argv[1])
try:
    props = {}
    config = directory / 'keystore.properties'
    if config.is_file():
        for line in config.read_text(encoding='utf-8-sig').splitlines():
            if line.lstrip().startswith(('#', '!')) or '=' not in line:
                continue
            key, value = line.split('=', 1)
            props[key.strip()] = value
    else:
        # The supplied Slooop script uses exactly these defaults without props.
        # Reuse that existing key, but never generate a replacement silently.
        props = {'store.file': 'dashchan2-local-debug.keystore',
                 'store.password': 'android', 'key.alias': 'androiddebugkey',
                 'key.password': 'android'}
    keys = ('store.file', 'store.password', 'key.alias', 'key.password')
    if any(not props.get(key) for key in keys):
        raise ValueError('Required properties: ' + ', '.join(keys))
    path = pathlib.Path(props['store.file'])
    if not path.is_absolute():
        path = directory / path
    if not path.is_file():
        raise ValueError('Configured keystore file does not exist')
    print(path.resolve())
    for key in keys[1:]:
        print(props[key])
except (OSError, ValueError) as exc:
    sys.exit(f'Signing configuration error: {exc}. No new key will be created.')
PY
)"
	local -a values
	mapfile -t values <<< "$data"
	[[ ${#values[@]} == 4 ]] || die 'Invalid signing configuration.'
	KEYSTORE="${values[0]}"; STORE_PASS="${values[1]}"; KEY_ALIAS="${values[2]}"; KEY_PASS="${values[3]}"
	printf 'Signing keystore: %s\nSigning alias: %s\n' "$KEYSTORE" "$KEY_ALIAS" >> "$OUT_DIR/BUILD_INFO.txt"
	# Check the existing key/passwords before an expensive build; never log passwords.
	(
		export SLOOOPIPE_STORE_PASS="$STORE_PASS" SLOOOPIPE_KEY_PASS="$KEY_PASS"
		keytool -list -keystore "$KEYSTORE" -storepass:env SLOOOPIPE_STORE_PASS -alias "$KEY_ALIAS"
	) > "$OUT_DIR/SIGNING_KEY_CHECK.txt" 2>&1
}

prepare_local_maven() {
	local repo="${SLOOOPIPE_LOCAL_MAVEN_REPO:-$BASE_DIR/offline-maven}"
	if [[ ! -d "$repo" ]]; then
		[[ -z "${SLOOOPIPE_LOCAL_MAVEN_REPO:-}" ]] || die "Local Maven directory is missing: $repo"
		printf 'Local Maven mirror: not configured\n' | tee -a "$OUT_DIR/BUILD_INFO.txt"
		return
	fi
	repo="$(realpath -e -- "$repo")"
	python3 - "$repo" <<'PY'
import hashlib, json, pathlib, re, sys
root = pathlib.Path(sys.argv[1]).resolve()
manifest = json.loads((root / 'mirror-manifest.json').read_text(encoding='utf-8'))
if manifest.get('schema') != 1 or not manifest.get('modules') or not manifest.get('files'):
    sys.exit('Invalid local Maven mirror manifest')
for module in manifest['modules']:
    if any(not re.fullmatch(r'[A-Za-z0-9_.-]+', module.get(key, '')) for key in ('group', 'name', 'version')):
        sys.exit('Invalid local Maven coordinate')
for entry in manifest['files']:
    relative = pathlib.PurePosixPath(entry['path'])
    if relative.is_absolute() or '..' in relative.parts or '\\' in entry['path']:
        sys.exit('Unsafe local Maven path')
    file = root.joinpath(*relative.parts)
    if not file.resolve().is_relative_to(root) or not file.is_file():
        sys.exit('Missing local Maven file: ' + entry['path'])
    digest = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1048576), b''):
            digest.update(chunk)
    if file.stat().st_size != entry['size'] or digest.hexdigest() != entry['sha256']:
        sys.exit('Local Maven file integrity failure: ' + entry['path'])
print(f"Local Maven mirror verified: {len(manifest['modules'])} modules, {len(manifest['files'])} files")
PY
	export SLOOOPIPE_MAVEN_REPO="$repo"
	LOCAL_MAVEN_INIT="$OUT_DIR/local-maven.gradle"
	cat > "$LOCAL_MAVEN_INIT" <<'GRADLE'
// Existing pinned dependencies transferred from JitPack; no source rewrites.
settingsEvaluated { settings ->
    def directory = new File(System.getenv('SLOOOPIPE_MAVEN_REPO'))
    def manifest = new groovy.json.JsonSlurper().parse(new File(directory, 'mirror-manifest.json'))
    def repos = settings.dependencyResolutionManagement.repositories
    repos.exclusiveContent {
        forRepository {
            repos.maven {
                name = 'SloooPipeTransferredJitPack'
                url = directory.toURI()
                metadataSources {
                    gradleMetadata()
                    mavenPom()
                }
            }
        }
        filter {
            manifest.modules.each { module ->
                includeVersion(module.group.toString(), module.name.toString(), module.version.toString())
            }
        }
    }
}
GRADLE
	printf 'Local Maven mirror: %s\n' "$repo" | tee -a "$OUT_DIR/BUILD_INFO.txt"
}

run_gradle() (
	local project="$1" component="$2"; shift 2
	cd "$project"
	local -a args=(--no-daemon --no-configuration-cache --console=plain --stacktrace)
	local -a launcher=()
	if [[ "$component" == main ]]; then
		if [[ "$AUTH_MODE" == with ]]; then
			[[ -d "$project/youtube-auth-pipepipe" ]] || die 'This source ZIP has no auth implementation'
			args+=(-Pslooopipe.withAuth=true)
		else
			args+=(-Pslooopipe.withAuth=false)
		fi
		rm -f -- "$project/app/build/reports/auth/dependency-graph.txt"
	fi
	[[ "$FULL_LOG" != 1 ]] || args+=(--info)
	[[ "$RUN_TESTS" != 1 ]] || args+=(-I "$OUT_DIR/fresh-test-results.gradle")
	[[ -z "$LOCAL_MAVEN_INIT" ]] || args+=(-I "$LOCAL_MAVEN_INIT")
	# shared:jvmTest includes Compose Desktop UI tests and needs an X display.
	if [[ "$component" == main && "$RUN_TESTS" == 1 && -f "$project/shared/build.gradle.kts" && -z "${DISPLAY:-}" ]]; then
		require_command xvfb-run; require_command Xvfb; require_command xauth
		launcher=(xvfb-run -a)
	fi
	printf 'Gradle command:' | tee -a "$OUT_DIR/BUILD_INFO.txt"
	printf ' %q' "${launcher[@]}" ./gradlew "$@" "${args[@]}" | tee -a "$OUT_DIR/BUILD_INFO.txt"
	printf '\n' | tee -a "$OUT_DIR/BUILD_INFO.txt"
	"${launcher[@]}" ./gradlew "$@" "${args[@]}" 2>&1 | tee "$OUT_DIR/build-$component.log"
)

save_reports() {
	local project="$1" component="$2"
	[[ -n "$project" && -d "$project" ]] || return 0
	if [[ "$component" == main && -f "$project/app/build/reports/auth/dependency-graph.txt" ]]; then
		mkdir -p "$OUT_DIR/reports/main/auth"
		cp -- "$project/app/build/reports/auth/dependency-graph.txt" "$OUT_DIR/reports/main/auth/"
	fi
	case "$component" in
		main) [[ "$MAIN_TESTS_STARTED" == 1 ]] || return 0 ;;
		player) [[ "$PLAYER_TESTS_STARTED" == 1 ]] || return 0 ;;
	esac
	python3 - "$project" "$OUT_DIR/reports/$component" <<'PY'
import pathlib, shutil, sys
root, target = map(pathlib.Path, sys.argv[1:])
for module in ('app', 'shared', 'youtube-auth-api', 'new-player', 'test-app', ''):
    for subdir in ('reports/tests', 'test-results'):
        src = root / module / 'build' / subdir
        if src.is_dir():
            shutil.copytree(src, target / (module or 'root') / subdir, dirs_exist_ok=True)
PY
}

clear_test_outputs() {
	# Remove generated test evidence before this run, so a failed/NO-SOURCE task
	# cannot inherit a previous run's passing XML or HTML from the work cache.
	python3 - "$1" <<'PY'
import pathlib, shutil, sys
root = pathlib.Path(sys.argv[1]).resolve()
for module in ('app', 'shared', 'youtube-auth-api', 'new-player', 'test-app'):
    for suffix in ('test-results', 'reports/tests'):
        path = root / module / 'build' / suffix
        if not path.resolve().is_relative_to(root):
            sys.exit('Test output path escapes project directory')
        if path.exists():
            shutil.rmtree(path)
PY
}

summarize_tests() {
	local project="$1" component="$2"
	python3 - "$project" "$component" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
tasks = [('app', 'testDebugUnitTest', True)] if sys.argv[2] == 'main' else [
    ('new-player', 'testDebugUnitTest', True), ('test-app', 'testDebugUnitTest', False)]
if sys.argv[2] == 'main' and (root / 'shared/build.gradle.kts').is_file():
    tasks.append(('shared', 'jvmTest', True))
if sys.argv[2] == 'main' and (root / 'youtube-auth-api').is_dir():
    tasks.append(('youtube-auth-api', 'test', True))
failed = False
for module, task, required in tasks:
    files = sorted((root / module / 'build/test-results' / task).glob('TEST-*.xml'))
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in files:
        suite = ET.parse(path).getroot()
        if suite.tag != 'testsuite':
            sys.exit('Unexpected JUnit XML format: ' + str(path))
        for key in counts:
            counts[key] += int(suite.get(key, '0'))
    ran = counts['tests'] - counts['skipped']
    has_sources = any((root / module / 'src/test').rglob('*.kt')) or any((root / module / 'src/test').rglob('*.java'))
    print(f'{module}:{task}: ' + ', '.join(f'{k}={v}' for k, v in counts.items()))
    if ran <= 0:
        print('  NOT CONFIRMED: no executed tests' if required or has_sources else '  No unit test sources/results; no test pass claimed')
        failed |= required or has_sources
    elif counts['failures'] or counts['errors']:
        failed = True
    else:
        print('  PASSED (executed tests only)')
if failed:
    sys.exit('Required unit tests did not produce a passing execution.')
print('Required unit tests passed. Device tests and playback remain unverified.')
PY
}

collect_apks() {
	local project="$1" module="$2" component="$3" apk base signed
	local listing
	listing="$(python3 - "$project/$module/build/outputs/apk/debug" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1]).resolve()
try:
    metadata = json.loads((root / 'output-metadata.json').read_text())
    if metadata['variantName'] != 'debug' or not metadata['elements']:
        raise ValueError('Expected debug APK metadata')
    for entry in metadata['elements']:
        apk = (root / entry['outputFile']).resolve()
        if not apk.is_relative_to(root) or not apk.is_file() or apk.suffix != '.apk':
            raise ValueError('Invalid APK output path')
        print(apk)
except (OSError, ValueError, KeyError) as exc:
    sys.exit(f'APK metadata error: {exc}')
PY
)"
	mkdir -p "$OUT_DIR/apks/$component"
	while IFS= read -r apk; do
		base="$(basename "$apk" .apk)"
		signed="$OUT_DIR/apks/$component/${base}-signed.apk"
		"$ZIPALIGN" -p -f 4 "$apk" "$OUT_DIR/apks/$component/${base}-aligned.apk"
		(
			export SLOOOPIPE_STORE_PASS="$STORE_PASS" SLOOOPIPE_KEY_PASS="$KEY_PASS"
			"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
				--ks-pass env:SLOOOPIPE_STORE_PASS --key-pass env:SLOOOPIPE_KEY_PASS \
				--v4-signing-enabled false --out "$signed" "$OUT_DIR/apks/$component/${base}-aligned.apk"
		)
		rm -- "$OUT_DIR/apks/$component/${base}-aligned.apk"
		"$APKSIGNER" verify --verbose --print-certs "$signed" > "$OUT_DIR/apks/$component/${base}-signature.txt"
		"$AAPT" dump badging "$signed" > "$OUT_DIR/apks/$component/${base}-metadata.txt"
		printf 'APK: %s\n' "$signed"
	done <<< "$listing"
}

write_system_snapshot() {
	date -u '+UTC: %Y-%m-%d %H:%M:%S'
	java -version 2>&1
	free -m || true
	df -h "$OUT_DIR" || true
	ulimit -a || true
}

finish() {
	local status=$?
	trap - EXIT ERR
	if [[ -n "$OUT_DIR" && -d "$OUT_DIR" ]]; then
		if ! save_reports "$MAIN_DIR" main; then [[ "$status" != 0 ]] || status=1; fi
		if ! save_reports "$PLAYER_DIR" player; then [[ "$status" != 0 ]] || status=1; fi
		{
			printf 'Exit code: %s\nPhase: %s\n' "$status" "$BUILD_PHASE"
			printf 'Device tests: NOT RUN\nPlayback: NOT VERIFIED\n'
		} > "$OUT_DIR/STATUS.txt"
		if [[ "$status" != 0 ]]; then
			write_system_snapshot > "$OUT_DIR/system-after-failure.log" 2>&1 || true
			printf '\nFailed in phase: %s\nLogs: %s\nWork retained under: %s\n' "$BUILD_PHASE" "$OUT_DIR" "$WORK_ROOT" >&2
		fi
	fi
	exit "$status"
}

main() {
	while [[ $# -gt 0 ]]; do
		case "$1" in
			--help|-h) usage; return ;;
			--version) printf '%s\n' "$SCRIPT_REVISION"; return ;;
			--fresh) FRESH=1; shift ;;
			--base-dir|--gradle-user-home|--component|--operation|--newpipe-zip|--newplayer-zip|--auth)
				[[ $# -ge 2 && -n "$2" ]] || die "Missing value for $1"
				case "$1" in
					--base-dir) BASE_DIR="$2" ;; --gradle-user-home) GRADLE_HOME_OVERRIDE="$2" ;;
					--auth) AUTH_MODE="$2" ;;
					--component) COMPONENT="$2" ;; --operation) OPERATION="$2" ;;
					--newpipe-zip) MAIN_ZIP="$2" ;; --newplayer-zip) PLAYER_ZIP="$2" ;;
				esac
				shift 2 ;;
			*) die "Unknown option: $1" ;;
		esac
	done
	[[ "$AUTH_MODE" == with || "$AUTH_MODE" == without ]] || die "--auth must be with or without"
	if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
	local command component
	for command in python3 java javac git flock sha256sum realpath find sort sed tee mktemp; do require_command "$command"; done
	python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 9) else "Python 3.9+ required")'
	local java_version; java_version="$(java -version 2>&1)"
	[[ "$java_version" =~ version\ \"([0-9]+) ]] && ((BASH_REMATCH[1] >= 21)) || die 'Select JDK 21 or newer using JAVA_HOME and PATH.'
	[[ "$(javac -version 2>&1)" =~ ^javac\ ([0-9]+) ]] && ((BASH_REMATCH[1] >= 21)) || die 'javac must also be version 21 or newer.'
	mkdir -p "$BASE_DIR"
	BASE_DIR="$(realpath -e -- "$BASE_DIR")"
	SOURCES_DIR="$BASE_DIR/sources"; WORK_ROOT="$BASE_DIR/work"; SIGNING_DIR="$BASE_DIR/signing"
	mkdir -p "$SOURCES_DIR" "$WORK_ROOT" "$BASE_DIR/out"
	exec 9> "$BASE_DIR/.slooopipe-build.lock"
	flock -n 9 || die "Another SloooPipe build is running in $BASE_DIR"
	# Reuse Slooop's already downloaded Wrapper distributions and dependencies.
	# Gradle project/work/output directories remain specific to each application.
	local gradle_home="${GRADLE_HOME_OVERRIDE:-${GRADLE_USER_HOME:-${HOME}/.gradle}}"
	mkdir -p "$gradle_home"
	export GRADLE_USER_HOME; GRADLE_USER_HOME="$(realpath -e -- "$gradle_home")"
	printf 'SloooPipe WSL builder %s\nBase: %s\nGradle user home: %s\n' "$SCRIPT_REVISION" "$BASE_DIR" "$GRADLE_USER_HOME"
	choose_operation
	choose_component
	for component in "${SELECTED[@]}"; do
		case "$component" in
			main) [[ -n "$MAIN_ZIP" ]] || MAIN_ZIP="$(choose_zip main)" ;;
			player) [[ -n "$PLAYER_ZIP" ]] || PLAYER_ZIP="$(choose_zip player)" ;;
		esac
	done
	OUT_DIR="$(mktemp -d "$BASE_DIR/out/run-$(date -u '+%Y%m%d-%H%M%S').XXXXXXXX")"
	trap finish EXIT
	trap 'printf "Failure at script line %s (phase: %s)\n" "$LINENO" "$BUILD_PHASE" >&2' ERR
	printf 'SloooPipe builder %s\nOperation: %s\nComponent: %s\n' "$SCRIPT_REVISION" "$OPERATION" "$COMPONENT" > "$OUT_DIR/BUILD_INFO.txt"
	printf 'Gradle user home: %s\nAuth mode: %s\n' "$GRADLE_USER_HOME" "$AUTH_MODE" >> "$OUT_DIR/BUILD_INFO.txt"
	for component in "${SELECTED[@]}"; do
		case "$component" in
			main) prepare_archive "$MAIN_ZIP" main; MAIN_DIR="$PREPARED_DIR" ;;
			player) prepare_archive "$PLAYER_ZIP" player; PLAYER_DIR="$PREPARED_DIR" ;;
		esac
	done
	local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" candidate
	if [[ -z "$sdk" ]]; then
		for candidate in "$HOME/android-sdk" "$HOME/Android/Sdk"; do
			if [[ -d "$candidate" ]]; then sdk="$candidate"; break; fi
		done
	fi
	[[ -n "$sdk" && -d "$sdk" ]] || die 'Set ANDROID_HOME to the Linux Android SDK directory.'
	export ANDROID_HOME; ANDROID_HOME="$(realpath -e -- "$sdk")"
	export ANDROID_SDK_ROOT="$ANDROID_HOME"
	printf 'Android SDK: %s\n' "$ANDROID_HOME" | tee -a "$OUT_DIR/BUILD_INFO.txt"
	for candidate in "$MAIN_DIR" "$PLAYER_DIR"; do [[ -z "$candidate" ]] || prepare_project "$candidate"; done
	require_sdk_platforms | tee -a "$OUT_DIR/BUILD_INFO.txt"
	prepare_local_maven
	if [[ "$BUILD_APK" == 1 ]]; then
		require_command keytool
		local tools
		tools="$(python3 - "$ANDROID_HOME/build-tools" <<'PY'
import pathlib, re, sys
root = pathlib.Path(sys.argv[1])
dirs = [p for p in root.glob('*') if re.fullmatch(r'\d+\.\d+\.\d+', p.name)
        and all((p / name).is_file() for name in ('apksigner', 'zipalign', 'aapt'))]
if not dirs:
    sys.exit('No complete stable Android SDK Build Tools installation found')
print(max(dirs, key=lambda p: tuple(map(int, p.name.split('.')))))
PY
)"
		APKSIGNER="$tools/apksigner"; ZIPALIGN="$tools/zipalign"; AAPT="$tools/aapt"
		prepare_signing
	fi
	cat > "$OUT_DIR/fresh-test-results.gradle" <<'GRADLE'
// Re-execute test tasks; retain compilation and dependency caches.
allprojects {
    tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf('Fresh local verification results') { true }
        testLogging {
            events 'failed'
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}
GRADLE
	write_system_snapshot > "$OUT_DIR/system-before.log" 2>&1
	local -a tasks
	for component in "${SELECTED[@]}"; do
		tasks=()
		BUILD_PHASE="$component Gradle ($OPERATION)"
		if [[ "$component" == main ]]; then
			if [[ "$RUN_TESTS" == 1 ]]; then
				clear_test_outputs "$MAIN_DIR"
				MAIN_TESTS_STARTED=1
				tasks+=(:app:testDebugUnitTest)
				[[ ! -d "$MAIN_DIR/youtube-auth-api" ]] || tasks+=(:youtube-auth-api:test)
				[[ ! -f "$MAIN_DIR/shared/build.gradle.kts" ]] || tasks+=(:shared:jvmTest)
			fi
			[[ ! -d "$MAIN_DIR/youtube-auth-api" ]] || tasks+=(:app:verifyAuthIsolation)
			[[ "$BUILD_APK" != 1 ]] || tasks+=(:app:assembleDebug)
			run_gradle "$MAIN_DIR" main "${tasks[@]}"
			[[ "$RUN_TESTS" != 1 ]] || summarize_tests "$MAIN_DIR" main | tee "$OUT_DIR/UNIT_TESTS-main.txt"
			BUILD_PHASE='main APK signing/verification'
			[[ "$BUILD_APK" != 1 ]] || collect_apks "$MAIN_DIR" app main
		else
			if [[ "$RUN_TESTS" == 1 ]]; then
				clear_test_outputs "$PLAYER_DIR"
				PLAYER_TESTS_STARTED=1
				tasks+=(:new-player:testDebugUnitTest :test-app:testDebugUnitTest)
			fi
			[[ "$BUILD_APK" != 1 ]] || tasks+=(:test-app:assembleDebug)
			run_gradle "$PLAYER_DIR" player "${tasks[@]}"
			[[ "$RUN_TESTS" != 1 ]] || summarize_tests "$PLAYER_DIR" player | tee "$OUT_DIR/UNIT_TESTS-player.txt"
			BUILD_PHASE='player APK signing/verification'
			[[ "$BUILD_APK" != 1 ]] || collect_apks "$PLAYER_DIR" test-app player
		fi
	done
	if [[ "$BUILD_APK" == 1 ]]; then
		(cd "$OUT_DIR" && find apks -type f -name '*.apk' -print0 | sort -z | xargs -0 -r sha256sum > SHA256SUMS.txt)
	fi
	BUILD_PHASE=complete
	printf '\nDone. Results: %s\nDevice tests: NOT RUN. Playback: NOT VERIFIED.\n' "$OUT_DIR"
}

# Permit read-only shell syntax inspection and isolated helper checks.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
