#!/usr/bin/env python3
"""Download existing pinned JitPack dependencies without running Gradle or a compiler."""

import argparse
import concurrent.futures
import hashlib
import io
import json
import pathlib
import re
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


ORIGIN = "https://jitpack.io/"
# These two upstream multi-module publications retain the parent coordinate in
# their component block. Keep their metadata byte-for-byte as served by JitPack.
UPSTREAM_COMPONENT_ALIASES = {
    ("com.github.lisawray.groupie", "groupie", "2.10.1"):
        ("com.github.lisawray", "groupie", "2.10.1"),
    ("com.github.lisawray.groupie", "groupie-viewbinding", "2.10.1"):
        ("com.github.lisawray", "groupie", "2.10.1"),
}


def fetch(url, optional=False):
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return response.read()
    except urllib.error.HTTPError as exc:
        if optional and exc.code == 404:
            return None
        raise


def prepare_module(coordinate):
    group, name, version = coordinate
    folder = f"{group.replace('.', '/')}/{name}/{version}"
    stem = f"{name}-{version}"
    pom_name = stem + ".pom"
    pom = fetch(ORIGIN + folder + "/" + pom_name)
    root = ET.fromstring(pom)
    metadata_name = stem + ".module"
    metadata = fetch(ORIGIN + folder + "/" + metadata_name, optional=True)
    files = [(folder + "/" + pom_name, pom)]
    binaries = {}
    dependencies = set()
    if metadata is not None:
        module = json.loads(metadata)
        identity = module["component"]
        actual_identity = (identity["group"], identity["module"], identity["version"])
        if actual_identity not in (coordinate, UPSTREAM_COMPONENT_ALIASES.get(coordinate)):
            raise ValueError("Unexpected module identity: " + str(coordinate))
        files.append((folder + "/" + metadata_name, metadata))
        for variant in module.get("variants", []):
            if variant.get("attributes", {}).get("org.gradle.category") == "documentation":
                continue
            if "available-at" in variant:
                raise ValueError("Module redirects need explicit review: " + str(coordinate))
            for artifact in variant.get("files", []):
                filename = artifact["url"]
                if pathlib.PurePosixPath(filename).name != filename or "\\" in filename:
                    raise ValueError("Nonlocal artifact URL: " + filename)
                if filename in binaries and binaries[filename] != artifact:
                    raise ValueError("Conflicting library artifacts: " + filename)
                binaries[filename] = artifact
            for dependency in variant.get("dependencies", []):
                if dependency["group"].startswith("com.github."):
                    constraint = dependency["version"]
                    pinned = constraint.get("strictly") or constraint.get("requires")
                    if not pinned or not re.fullmatch(r"[A-Za-z0-9_.-]+", pinned):
                        raise ValueError("Dependency needs explicit version review")
                    dependencies.add((dependency["group"], dependency["module"], pinned))
    else:
        packaging_node = root.find("{*}packaging")
        packaging = packaging_node.text if packaging_node is not None else "jar"
        if packaging not in ("jar", "aar"):
            raise ValueError("Unexpected artifact packaging: " + packaging)
        binaries[stem + "." + packaging] = {}
        for dependency in root.findall("{*}dependencies/{*}dependency"):
            values = {node.tag.rsplit("}", 1)[-1]: node.text for node in dependency}
            if values.get("scope") in ("test", "provided") or values.get("optional") == "true":
                continue
            if values.get("groupId", "").startswith("com.github."):
                pinned = values.get("version")
                if not pinned or not re.fullmatch(r"[A-Za-z0-9_.-]+", pinned):
                    raise ValueError("Dependency needs explicit version review")
                dependencies.add((values["groupId"], values["artifactId"], pinned))
    for filename, expected in sorted(binaries.items()):
        relative = folder + "/" + filename
        content = fetch(ORIGIN + relative)
        if "size" in expected and len(content) != expected["size"]:
            raise ValueError("Size mismatch: " + relative)
        verified = False
        for algorithm in ("sha512", "sha256", "sha1"):
            if algorithm in expected:
                actual = hashlib.new(algorithm, content).hexdigest()
                if actual != expected[algorithm]:
                    raise ValueError("Hash mismatch: " + relative)
                verified = True
        if not verified:
            sidecar = fetch(ORIGIN + relative + ".sha1")
            digest = sidecar.decode("ascii").strip().split()[0].lower()
            if not re.fullmatch(r"[0-9a-f]{40}", digest) or hashlib.sha1(content).hexdigest() != digest:
                raise ValueError("SHA-1 mismatch: " + relative)
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            if archive.testzip() is not None:
                raise ValueError("Damaged JAR/AAR: " + relative)
        files.append((relative, content))
    return coordinate, files, dependencies


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        parser.error("Output must be a new directory; existing mirrors are not overwritten")
    with args.catalog.open("rb") as source:
        catalog = tomllib.load(source)
    pending = set()
    for library in catalog["libraries"].values():
        group, _, name = library.get("module", "").partition(":")
        if group.startswith("com.github."):
            version = library["version"]
            if isinstance(version, dict):
                version = catalog["versions"][version["ref"]]
            pending.add((group, name, version))
    completed = set()
    records = []
    modules = []
    args.output.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        while pending:
            batch = sorted(pending - completed)
            pending.clear()
            for coordinate, files, dependencies in executor.map(prepare_module, batch):
                completed.add(coordinate)
                group, name, version = coordinate
                modules.append(dict(group=group, name=name, version=version))
                for relative, content in files:
                    target = args.output / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(content)
                    records.append(dict(path=relative, size=len(content),
                                        sha256=hashlib.sha256(content).hexdigest(),
                                        source=ORIGIN + relative))
                pending.update(dependencies - completed)
                print("Prepared " + ":".join(coordinate), flush=True)
    manifest = dict(schema=1, origin=ORIGIN, modules=modules,
                    files=sorted(records, key=lambda entry: entry["path"]))
    (args.output / "mirror-manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Complete: {len(modules)} pinned modules, {len(records)} files. No builds executed.")


if __name__ == "__main__":
    main()
