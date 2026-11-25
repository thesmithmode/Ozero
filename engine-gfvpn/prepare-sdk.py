#!/usr/bin/env python3
"""Prepare a locally supplied, exact APK for an ARM64 development device."""
import argparse
import hashlib
import os
import pathlib
import tempfile
import zipfile

APK_SHA = "9309781c889578388c250651a9fa3c5c1d4a6e9a0b432dec7e7cc1f93abf57d6"
ENTRIES = {
    "classes.dex": ("classes.dex", "103f8155e3bd33b8f13d8ff7d1f63cb5ec1fe7acc1aa1c395eaedb83d336c829"),
    "lib/arm64-v8a/libgojni.so": ("libgojni.so", "7fdffc628230ce188c474c42bfc5fef5b802ace74bd9e9b249f0272f9090e404"),
}

def digest(stream):
    sha = hashlib.sha256()
    for block in iter(lambda: stream.read(65536), b""):
        sha.update(block)
    return sha.hexdigest()

def prepare(apk, destination):
    apk, destination = pathlib.Path(apk), pathlib.Path(destination)
    if destination.exists():
        raise ValueError("Destination must not already exist")
    with apk.open("rb") as source:
        if digest(source) != APK_SHA:
            raise ValueError("APK SHA-256 mismatch")
        source.seek(0)
        with zipfile.ZipFile(source) as archive:
            destination.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=destination.parent) as tmp:
                stage = pathlib.Path(tmp) / "sdk"
                stage.mkdir(mode=0o700)
                for entry, (name, expected) in ENTRIES.items():
                    if archive.namelist().count(entry) != 1:
                        raise ValueError("Missing or duplicate SDK entry")
                    with archive.open(entry) as src, (stage / name).open("xb") as out:
                        for chunk in iter(lambda: src.read(65536), b""):
                            out.write(chunk)
                    with (stage / name).open("rb") as out:
                        if digest(out) != expected:
                            raise ValueError("SDK entry SHA-256 mismatch")
                    (stage / name).chmod(0o400)
                os.rename(stage, destination)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("destination")
    args = parser.parse_args()
    prepare(args.apk, args.destination)
