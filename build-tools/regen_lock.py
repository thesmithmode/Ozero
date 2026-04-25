#!/usr/bin/env python3
"""Generate binaries.lock.yaml from manifest.txt produced by build_*.sh."""
import argparse
import datetime
import os
import re
import sys

ABI_RE = re.compile(r"^libbyedpi-(?P<abi>[a-z0-9_-]+)\.so$")


def parse_manifest(path: str):
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--repo", required=True, help="owner/Repo")
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    meta, artifacts = parse_manifest(args.manifest)
    source_repo = meta.get("source_repo", "")
    source_commit = meta.get("source_commit", "")

    lines = [
        f"tag: {args.tag}",
        f"generated_at: {datetime.datetime.utcnow().isoformat()}Z",
        "artifacts:",
    ]
    for fname, sha in artifacts:
        m = ABI_RE.match(fname)
        if not m:
            print(f"skip non-byedpi artifact: {fname}", file=sys.stderr)
            continue
        abi = m.group("abi")
        size = os.path.getsize(os.path.join(os.path.dirname(args.manifest), fname))
        url = f"https://github.com/{args.repo}/releases/download/{args.tag}/{fname}"
        lines.extend([
            f"  - name: {fname}",
            f"    engine: byedpi",
            f"    abi: {abi}",
            f"    destination: jniLibs",
            f"    download_url: {url}",
            f"    sha256: {sha}",
            f"    size_bytes: {size}",
            f"    source_repo: {source_repo}",
            f"    source_commit: {source_commit}",
        ])

    with open(args.out, "w") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
