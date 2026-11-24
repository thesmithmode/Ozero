#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
revision="6aa03fc97d12848dce34eedbd187fb1077b5d1ea"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

git init -q "$work/usque"
git -C "$work/usque" remote add origin https://github.com/Diniboy1123/usque.git
git -C "$work/usque" fetch --depth 1 origin "$revision"
git -C "$work/usque" checkout --detach FETCH_HEAD
test "$(git -C "$work/usque" rev-parse HEAD)" = "$revision"

python3 "$root/scripts/patch-masque-upstream.py" "$work/usque"

out="$root/engine-masque/build/native/arm64-v8a"
mkdir -p "$out"
(
    cd "$work/usque"
    export GOOS=android GOARCH=arm64 CGO_ENABLED=0 GOTOOLCHAIN=local
    go mod verify
    go build -mod=readonly -trimpath -buildmode=pie -ldflags="-s -w" -o "$out/libusque.so" .
)
cp "$work/usque/LICENSE.md" "$out/usque-LICENSE.md"
sha256sum "$out/libusque.so"
