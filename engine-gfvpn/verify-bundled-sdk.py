#!/usr/bin/env python3
"""Check that the built APK contains exactly the pinned gfVPN runtime."""
import hashlib
import sys
import zipfile

EXPECTED = {
    "assets/gfvpn-sdk/classes.dex": (
        5377536, "103f8155e3bd33b8f13d8ff7d1f63cb5ec1fe7acc1aa1c395eaedb83d336c829",
    ),
    "assets/gfvpn-sdk/libgojni.so": (
        75485264, "7fdffc628230ce188c474c42bfc5fef5b802ace74bd9e9b249f0272f9090e404",
    ),
}

with zipfile.ZipFile(sys.argv[1]) as apk:
    for name, (size, expected) in EXPECTED.items():
        assert apk.namelist().count(name) == 1, f"Missing or duplicate {name}"
        assert apk.getinfo(name).file_size == size, f"Wrong size: {name}"
        with apk.open(name) as stream:
            digest = hashlib.sha256()
            for chunk in iter(lambda: stream.read(65536), b""):
                digest.update(chunk)
        assert digest.hexdigest() == expected, f"Wrong hash: {name}"
print("Verified bundled gfVPN runtime")
