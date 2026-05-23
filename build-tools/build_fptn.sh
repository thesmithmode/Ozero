#!/usr/bin/env bash
# build_fptn.sh — собирает libfptn_native_lib.so для arm64-v8a через Conan2 + NDK.
# Запуск: внутри Docker контейнера build-tools/Dockerfile.fptn.
# Источник: fptn-project/FptnClient-Android (git clone с рекурсивными submodules).
# JNI имена переименованы под пакет ru.ozero.enginefptn (sed-патч применён к src).
# Output: $OUT_DIR/libfptn_native_lib-arm64-v8a.so + $OUT_DIR/manifest.txt

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
CPP_SRC="$REPO_ROOT/engine-fptn/src/main/cpp"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/out/fptn}"
NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must be set}"
ABI="arm64-v8a"
API_LEVEL="${ANDROID_API:-24}"
BUILD_DIR="$REPO_ROOT/build/fptn-$ABI"

FPTN_REPO="https://github.com/fptn-project/FptnClient-Android.git"
FPTN_CLONE_DIR="$REPO_ROOT/build/fptn-android-src"

# ---------------------------------------------------------------------------
# 1. Clone FptnClient-Android (source of truth for fptn C++ lib + deps)
# ---------------------------------------------------------------------------
if [[ ! -d "$FPTN_CLONE_DIR/.git" ]]; then
    git clone --depth 1 --recurse-submodules --shallow-submodules \
        "$FPTN_REPO" "$FPTN_CLONE_DIR"
else
    echo "Using cached clone at $FPTN_CLONE_DIR"
fi

FPTN_LIB_DIR="$FPTN_CLONE_DIR/app/src/main/cpp/libs/fptn"
if [[ ! -d "$FPTN_LIB_DIR" ]] || [[ -z "$(ls -A "$FPTN_LIB_DIR" 2>/dev/null)" ]]; then
    echo "ERROR: fptn submodule empty at $FPTN_LIB_DIR" >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. Sync fptn submodule into our CPP source tree (symlink-free copy)
# ---------------------------------------------------------------------------
LIBS_DIR="$CPP_SRC/libs"
mkdir -p "$LIBS_DIR"
if [[ ! -d "$LIBS_DIR/fptn" ]]; then
    cp -r "$FPTN_LIB_DIR" "$LIBS_DIR/fptn"
fi

# ---------------------------------------------------------------------------
# 3. Create Conan Android arm64 host profile
# ---------------------------------------------------------------------------
CLANG_VERSION=$("$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version \
    2>&1 | grep -oP 'clang version \K[0-9]+' | head -1)
CLANG_VERSION="${CLANG_VERSION:-18}"

CONAN_PROFILE_DIR="$HOME/.conan2/profiles"
mkdir -p "$CONAN_PROFILE_DIR"

cat > "$CONAN_PROFILE_DIR/android-arm64" << EOF
[settings]
os=Android
os.api_level=${API_LEVEL}
arch=armv8
compiler=clang
compiler.version=${CLANG_VERSION}
compiler.libcxx=c++_shared
compiler.cppstd=17
build_type=Release

[conf]
tools.android:ndk_path=${NDK_HOME}
tools.cmake.cmaketoolchain:generator=Ninja

[buildenv]
ANDROID_NDK_HOME=${NDK_HOME}
CC=${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${API_LEVEL}-clang
CXX=${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${API_LEVEL}-clang++
AR=${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar
RANLIB=${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ranlib
STRIP=${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
EOF

# Ensure build profile (for host-machine packages like protobuf) also uses Ninja.
if ! grep -q "tools.cmake.cmaketoolchain:generator" "$CONAN_PROFILE_DIR/default" 2>/dev/null; then
    echo -e "\n[conf]\ntools.cmake.cmaketoolchain:generator=Ninja" >> "$CONAN_PROFILE_DIR/default"
fi

# ---------------------------------------------------------------------------
# 4. Install Conan deps (nlohmann_json + fptn via local recipe)
# ---------------------------------------------------------------------------
CONAN_INSTALL_DIR="$BUILD_DIR/conan-deps"
rm -rf "$CONAN_INSTALL_DIR"
mkdir -p "$CONAN_INSTALL_DIR"

conan install "$CPP_SRC" \
    --build=missing \
    --profile:host="android-arm64" \
    --profile:build="default" \
    --output-folder="$CONAN_INSTALL_DIR"

# ---------------------------------------------------------------------------
# 5. CMake configure + build
# ---------------------------------------------------------------------------
rm -rf "$BUILD_DIR/cmake-build"
mkdir -p "$BUILD_DIR/cmake-build"

cmake \
    -S "$CPP_SRC" \
    -B "$BUILD_DIR/cmake-build" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_FIND_ROOT_PATH="$CONAN_INSTALL_DIR" \
    -DCMAKE_PREFIX_PATH="$CONAN_INSTALL_DIR" \
    -G Ninja

cmake --build "$BUILD_DIR/cmake-build" --parallel

# ---------------------------------------------------------------------------
# 6. Copy output
# ---------------------------------------------------------------------------
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "$BUILD_DIR/cmake-build/libfptn_native_lib.so" "$OUT_DIR/libfptn_native_lib-${ABI}.so"

# ---------------------------------------------------------------------------
# 7. Manifest
# ---------------------------------------------------------------------------
FPTN_COMMIT="$(cd "$FPTN_CLONE_DIR" && git rev-parse HEAD)"
{
    echo "# build_fptn manifest"
    echo "source_repo=$FPTN_REPO"
    echo "source_commit=$FPTN_COMMIT"
    echo "ndk=$(basename "$NDK_HOME")"
    echo "api_level=$API_LEVEL"
    echo "abi=$ABI"
    echo
    echo "# SHA256:"
    cd "$OUT_DIR"
    for f in libfptn_native_lib-*.so; do
        sha256sum "$f"
    done
} > "$OUT_DIR/manifest.txt"

echo "=== Build complete ==="
cat "$OUT_DIR/manifest.txt"
