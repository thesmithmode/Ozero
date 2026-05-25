#!/usr/bin/env bash
# Builds libsingboxgojni.so from SagerNet sing-box-for-android gomobile AAR.
#
# Why hex-patch classes.jar:
#   gomobile generates go/Seq.class with static { System.loadLibrary("gojni"); }
#   hard-coded. Renaming the .so alone causes UnsatisfiedLinkError on JNI_OnLoad
#   because the static initializer still looks for "gojni". Hex-replace in the
#   classes.jar bytecode is the only fix without patching gomobile source.
set -euo pipefail

SINGBOX_REPO="${SINGBOX_REPO:-https://github.com/sagernet/sing-box-for-android.git}"
SINGBOX_REF="${SINGBOX_REF:-main}"
OUT_DIR="${OUT_DIR:-$(pwd)/build/singbox-binaries}"

mkdir -p "$OUT_DIR"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Cloning sing-box-for-android at $SINGBOX_REF"
git init -q "$TMP_DIR/src"
cd "$TMP_DIR/src"
git remote add origin "$SINGBOX_REPO"
git fetch --depth 1 origin "$SINGBOX_REF"
git checkout -q FETCH_HEAD
COMMIT="$(git rev-parse HEAD)"
COMMIT_SHORT="$(git rev-parse --short HEAD)"
echo "Source commit: $COMMIT"

echo "Building gomobile AAR (arm64-v8a only)"
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init

AAR_OUT="$TMP_DIR/singboxgojni.aar"
gomobile bind \
    -target=android/arm64 \
    -androidapi 21 \
    -o "$AAR_OUT" \
    github.com/sagernet/sing-box-for-android/libcore

echo "Verifying loadLibrary name in classes.jar"
PATCH_NEEDED=0
cd "$TMP_DIR"
unzip -q "$AAR_OUT" classes.jar -d aar_extract
cd aar_extract
unzip -q classes.jar go/Seq.class -d seq_extract 2>/dev/null || true
GOJNI_COUNT=$(javap -c seq_extract/go/Seq.class 2>/dev/null | grep -c 'loadLibrary.*gojni' || true)
echo "  loadLibrary(\"gojni\") occurrences in Seq.class: $GOJNI_COUNT"
if [ "$GOJNI_COUNT" -gt 0 ]; then
    PATCH_NEEDED=1
fi
if [ "$GOJNI_COUNT" -gt 1 ]; then
    echo "ERROR: >1 gojni occurrence in Seq.class — hex-replace is unreliable." >&2
    echo "  Use ASM/javassist bytecode rewriter instead." >&2
    exit 1
fi
cd "$TMP_DIR"

if [ "$PATCH_NEEDED" -eq 1 ]; then
    echo "Patching classes.jar: gojni -> singboxgojni"
    cp "aar_extract/classes.jar" classes_orig.jar
    python3 - <<'PYEOF'
import shutil, sys
with open("classes_orig.jar", "rb") as f:
    data = f.read()
old = b"gojni\x00"
new = b"singboxgojni\x00"
if old not in data:
    print("WARNING: 'gojni\\x00' not found in classes.jar binary", file=sys.stderr)
    sys.exit(0)
patched = data.replace(old, new, 1)
with open("classes_patched.jar", "wb") as f:
    f.write(patched)
print("Patch applied: gojni -> singboxgojni")
PYEOF
    mv classes_patched.jar aar_extract/classes.jar
fi

echo "Renaming libgojni.so -> libsingboxgojni.so in AAR"
unzip -q "$AAR_OUT" "jni/arm64-v8a/libgojni.so" -d aar_extract 2>/dev/null || true
SO_SRC="aar_extract/jni/arm64-v8a/libgojni.so"
SO_DST="aar_extract/jni/arm64-v8a/libsingboxgojni.so"
if [ -f "$SO_SRC" ]; then
    mv "$SO_SRC" "$SO_DST"
    echo "  libgojni.so -> libsingboxgojni.so"
else
    echo "WARNING: libgojni.so not found in AAR — SagerNet may have already renamed it" >&2
    # Check if they already named it singboxgojni or libcore
    unzip -q "$AAR_OUT" "jni/arm64-v8a/" -d aar_extract 2>/dev/null || true
    ls aar_extract/jni/arm64-v8a/ 2>/dev/null || true
fi

echo "Repacking AAR"
cp "$AAR_OUT" "$TMP_DIR/singboxgojni_patched.aar"
cd aar_extract
zip -q "../singboxgojni_patched.aar" "classes.jar"
if [ -f "jni/arm64-v8a/libsingboxgojni.so" ]; then
    zip -q "../singboxgojni_patched.aar" "jni/arm64-v8a/libsingboxgojni.so"
fi

echo "Writing outputs to $OUT_DIR"
SO_FINAL="$OUT_DIR/libsingboxgojni-arm64-v8a.so"
AAR_FINAL="$OUT_DIR/libsingboxgojni.aar"

if [ -f "aar_extract/jni/arm64-v8a/libsingboxgojni.so" ]; then
    cp "aar_extract/jni/arm64-v8a/libsingboxgojni.so" "$SO_FINAL"
fi
cp "$TMP_DIR/singboxgojni_patched.aar" "$AAR_FINAL"

cd "$OUT_DIR"
sha256sum libsingboxgojni.aar libsingboxgojni-arm64-v8a.so > sha256sums.txt 2>/dev/null || \
    shasum -a 256 libsingboxgojni.aar libsingboxgojni-arm64-v8a.so > sha256sums.txt
cat sha256sums.txt

cat > meta.txt <<META
commit=${COMMIT}
commit_short=${COMMIT_SHORT}
META

echo "Done. Outputs in $OUT_DIR"
