#!/usr/bin/env bash
set -euo pipefail
apk="${1:?APK path required}"
abi="${2:-arm64-v8a}"
root="$(cd "$(dirname "$0")/.." && pwd)"
original="$root/engine-masque/build/native/$abi/libusque.so"
test -s "$original"
extracted="$(mktemp)"
trap 'rm -f "$extracted"' EXIT
unzip -p "$apk" "lib/$abi/libusque.so" > "$extracted"
cmp "$original" "$extracted"
unzip -p "$apk" assets/usque-LICENSE.md | cmp - "$root/engine-masque/third-party/usque-LICENSE.md"
sha256sum "$original"
