#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
revision=57205a4bf61b55991c35841198cc27ead5ef6962
ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
: "${ndk:?Set ANDROID_NDK_HOME or ANDROID_NDK_ROOT}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git init -q "$work/source"
git -C "$work/source" remote add origin https://github.com/Alexey71/opera-proxy.git
git -C "$work/source" fetch --depth=1 origin "$revision"
git -C "$work/source" checkout --detach FETCH_HEAD
test "$(git -C "$work/source" rev-parse HEAD)" = "$revision"
out="${1:-$root/engine-opera/src/main/jniLibs/arm64-v8a/libopera_proxy.so}"
out="$(realpath -m "$out")"
mkdir -p "$(dirname "$out")"
cd "$work/source"
go mod verify
GOOS=android GOARCH=arm64 CGO_ENABLED=1 \
  CC="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang" \
  go build -mod=readonly -trimpath -buildmode=pie -ldflags='-s -w' -o "$out" .
file "$out"
sha256sum "$out"
