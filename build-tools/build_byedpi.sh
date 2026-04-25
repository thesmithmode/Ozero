#!/usr/bin/env bash
# build_byedpi.sh — собирает libbyedpi.so для 4 ABI через NDK CMake.
# Запуск: внутри Docker контейнера build-tools/Dockerfile.
# Источник: hufrea/byedpi (git submodule в engine-byedpi/src/main/cpp/byedpi).
# Output: $OUT_DIR/libbyedpi-<abi>.so + $OUT_DIR/manifest.txt

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SRC_DIR="$REPO_ROOT/engine-byedpi/src/main/cpp"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/out/byedpi}"
NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must be set}"
CMAKE_BIN="${CMAKE_BIN:-cmake}"
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
API_LEVEL="${ANDROID_API:-24}"

if [[ ! -d "$SRC_DIR/byedpi" ]] || [[ -z "$(ls -A "$SRC_DIR/byedpi" 2>/dev/null)" ]]; then
    echo "ERROR: byedpi submodule missing at $SRC_DIR/byedpi" >&2
    echo "Run: git submodule update --init --recursive" >&2
    exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

for ABI in "${ABIS[@]}"; do
    BUILD_DIR="$REPO_ROOT/build/byedpi-$ABI"
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"

    "$CMAKE_BIN" \
        -S "$SRC_DIR" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$API_LEVEL" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="-ffile-prefix-map=$REPO_ROOT=. -fdebug-prefix-map=$REPO_ROOT=." \
        -G "Unix Makefiles"

    "$CMAKE_BIN" --build "$BUILD_DIR" --parallel
    cp "$BUILD_DIR/libbyedpi.so" "$OUT_DIR/libbyedpi-$ABI.so"
    rm -rf "$BUILD_DIR"
done

# Manifest with SHA256 + source commit
git config --global --add safe.directory "$SRC_DIR/byedpi"
SOURCE_COMMIT="$(cd "$SRC_DIR/byedpi" && git rev-parse HEAD)"
{
    echo "# build_byedpi manifest"
    echo "source_repo=https://github.com/hufrea/byedpi"
    echo "source_commit=$SOURCE_COMMIT"
    echo "ndk=$(basename "$NDK_HOME")"
    echo "api_level=$API_LEVEL"
    echo
    echo "# SHA256:"
    cd "$OUT_DIR"
    for f in libbyedpi-*.so; do
        sha256sum "$f"
    done
} > "$OUT_DIR/manifest.txt"

echo "Done. Artefacts in $OUT_DIR:"
ls -la "$OUT_DIR"
