#!/usr/bin/env python3
"""Patch go/Seq.class in go-stubs.jar to remove System.loadLibrary("box") call.

Without this patch, go.Seq static initializer loads libbox.so in every process
that touches gomobile bindings. This causes dual Go runtime (libbox + libgojni)
in the main process -> SIGSEGV.

After patch: libraries are loaded explicitly per-process by OzeroApp (gojni)
and SingboxEngineService (box). go.Seq.init() JNI works with whichever
runtime is pre-loaded.
"""

import sys
import zipfile

def patch(jar_path):
    patched = False
    entries = {}
    with zipfile.ZipFile(jar_path, "r") as zin:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "go/Seq.class":
                bdata = bytearray(data)
                for i in range(len(bdata) - 5):
                    if bdata[i] == 0x12 and bdata[i + 2] == 0xB8:
                        cp_idx = bdata[i + 1]
                        if cp_idx == 0x5C:
                            bdata[i:i + 5] = b"\x00" * 5
                            patched = True
                            break
                data = bytes(bdata)
            entries[item] = data

    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as zout:
        for item, data in entries.items():
            zout.writestr(item, data)

    if patched:
        print(f"go.Seq patched: removed loadLibrary(\"box\") from {jar_path}")
    else:
        print(f"WARNING: loadLibrary pattern not found in {jar_path}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    patch(sys.argv[1] if len(sys.argv) > 1 else "singbox-core/libs-stubs/go-stubs.jar")
