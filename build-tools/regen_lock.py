#!/usr/bin/env python3
"""Generate or update binaries.lock.yaml from a per-engine manifest.txt.

Each engine produces its own release tag (e.g. ``byedpi-<sha8>``, ``xray-<sha8>``)
and a manifest file with the SHA256 of every published artifact. Re-running the
script for one engine replaces only that engine's entries in the existing lock —
artifacts of other engines stay untouched (per-engine isolation, RT.1.7.1).

Manifest format (produced by build-tools/build_<engine>.sh):

    # build_<engine> manifest
    source_repo=...
    source_commit=...
    <other key=value metadata>

    # SHA256:
    <sha256>  <filename>
    ...

Schema of binaries.lock.yaml — see buildSrc/src/main/kotlin/binaries/LockFile.kt.
"""
import argparse
import datetime
import os
import re
import sys
from pathlib import Path

import yaml


# Engine config: filename regex (with optional 'abi' named group) + destination.
ENGINE_CONFIGS = {
    "byedpi": {
        "filename_re": re.compile(r"^libbyedpi-(?P<abi>[a-z0-9_-]+)\.so$"),
        "destination": "jniLibs",
    },
    "xray": {
        "filename_re": re.compile(r"^libxray\.aar$"),
        "destination": "libs",
    },
    "naive": {
        "filename_re": re.compile(r"^libnaive-(?P<abi>[a-z0-9_-]+)\.so$"),
        "destination": "jniLibs",
    },
    "amneziawg": {
        "filename_re": re.compile(r"^libamneziawg\.aar$"),
        "destination": "libs",
    },
    "hysteria2": {
        "filename_re": re.compile(r"^libhysteria2\.aar$"),
        "destination": "libs",
    },
    "tor": {
        "filename_re": re.compile(r"^libtor-(?P<abi>[a-z0-9_-]+)\.so$"),
        "destination": "jniLibs",
    },
    "iptproxy": {
        "filename_re": re.compile(r"^libiptproxy-(?P<abi>[a-z0-9_-]+)\.so$"),
        "destination": "jniLibs",
    },
}


# Order of keys when writing artifact entries — keeps lock human-diffable.
_KEY_ORDER = (
    "name",
    "engine",
    "abi",
    "destination",
    "download_url",
    "sha256",
    "size_bytes",
    "source_repo",
    "source_commit",
)


def parse_manifest(path):
    """Return ``(meta_dict, [(filename, sha256), ...])`` from a manifest file."""
    meta = {}
    artifacts = []
    in_sha = False
    with open(path) as f:
        for raw in f:
            line = raw.strip()
            if not line:
                continue
            if line.startswith("#"):
                if "sha256" in line.lower():
                    in_sha = True
                continue
            if "=" in line and not in_sha:
                k, v = line.split("=", 1)
                meta[k.strip()] = v.strip()
                continue
            if in_sha:
                parts = line.split()
                if len(parts) == 2:
                    artifacts.append((parts[1], parts[0]))
    return meta, artifacts


def build_artifact_entries(engine, tag, repo, manifest_path, meta, artifacts):
    """Convert raw manifest rows into lock-file artifact dicts for ``engine``.

    Files that don't match the engine's filename pattern are skipped with a
    warning to stderr. Raises ``KeyError`` for unknown engines.
    """
    cfg = ENGINE_CONFIGS[engine]
    name_re = cfg["filename_re"]
    destination = cfg["destination"]
    source_repo = meta.get("source_repo", "")
    source_commit = meta.get("source_commit", "")
    base_dir = os.path.dirname(os.path.abspath(manifest_path))

    entries = []
    for fname, sha in artifacts:
        m = name_re.match(fname)
        if not m:
            print(f"skip non-{engine} artifact: {fname}", file=sys.stderr)
            continue
        abi = m.groupdict().get("abi")
        size = os.path.getsize(os.path.join(base_dir, fname))
        url = f"https://github.com/{repo}/releases/download/{tag}/{fname}"
        entry = {
            "name": fname,
            "engine": engine,
            "destination": destination,
            "download_url": url,
            "sha256": sha,
            "size_bytes": size,
            "source_repo": source_repo,
            "source_commit": source_commit,
        }
        if abi is not None:
            entry["abi"] = abi
        entries.append(entry)
    return entries


def load_existing_lock(path):
    """Return a ``{tag, generated_at, artifacts}`` dict from disk, or empty stub."""
    if not os.path.exists(path):
        return {"tag": "", "generated_at": "", "artifacts": []}
    with open(path) as f:
        data = yaml.safe_load(f) or {}
    if not isinstance(data, dict):
        raise ValueError(f"Existing lock {path}: root is not a mapping")
    data.setdefault("tag", "")
    data.setdefault("generated_at", "")
    data.setdefault("artifacts", [])
    if not isinstance(data["artifacts"], list):
        raise ValueError(f"Existing lock {path}: artifacts must be a list")
    return data


def merge_engine_artifacts(existing_lock, engine, new_entries, tag):
    """Replace entries with ``engine`` matching while keeping other engines intact."""
    other = [a for a in existing_lock.get("artifacts", []) if a.get("engine") != engine]
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    return {
        "tag": tag,
        "generated_at": now.isoformat().replace("+00:00", "Z"),
        "artifacts": other + new_entries,
    }


def write_lock(path, lock):
    """Serialize ``lock`` to YAML with stable, human-readable layout."""
    lines = [
        f"tag: {lock['tag']}",
        f"generated_at: {lock['generated_at']}",
        "artifacts:",
    ]
    for art in lock["artifacts"]:
        first = True
        for key in _KEY_ORDER:
            if key not in art:
                continue
            value = art[key]
            indent = "  - " if first else "    "
            lines.append(f"{indent}{key}: {value}")
            first = False
    Path(path).write_text("\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--engine", required=True, choices=sorted(ENGINE_CONFIGS.keys()))
    ap.add_argument("--tag", required=True, help="Release tag, e.g. xray-deadbeef")
    ap.add_argument("--repo", required=True, help="GitHub owner/repo, e.g. thesmithmode/Ozero")
    ap.add_argument("--manifest", required=True, help="Path to manifest.txt produced by build_<engine>.sh")
    ap.add_argument("--out", required=True, help="Path to binaries.lock.yaml (will be updated in-place)")
    args = ap.parse_args()

    meta, artifacts = parse_manifest(args.manifest)
    new_entries = build_artifact_entries(
        engine=args.engine, tag=args.tag, repo=args.repo,
        manifest_path=args.manifest, meta=meta, artifacts=artifacts,
    )
    existing = load_existing_lock(args.out)
    merged = merge_engine_artifacts(existing, args.engine, new_entries, args.tag)
    write_lock(args.out, merged)


if __name__ == "__main__":
    main()
