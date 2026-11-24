#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
revision="6aa03fc97d12848dce34eedbd187fb1077b5d1ea"
abi="${1:-arm64-v8a}"
case "$abi" in
    arm64-v8a) arch=arm64; machine="AArch64" ;;
    x86_64) arch=amd64; machine="Advanced Micro Devices X86-64" ;;
    *) echo "Unsupported MASQUE ABI: $abi" >&2; exit 1 ;;
esac
export LC_ALL=C
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

git init -q "$work/usque"
git -C "$work/usque" remote add origin https://github.com/Diniboy1123/usque.git
git -C "$work/usque" fetch --depth 1 origin "$revision"
git -C "$work/usque" checkout --detach FETCH_HEAD
test "$(git -C "$work/usque" rev-parse HEAD)" = "$revision"

python3 "$root/scripts/patch-masque-upstream.py" "$work/usque"

out="$root/engine-masque/build/native/$abi"
mkdir -p "$out"
(
    cd "$work/usque"
    export GOOS=android GOARCH="$arch" CGO_ENABLED=0 GOTOOLCHAIN=local
    gofmt -w main.go internal/socks5.go cmd/socks.go
    go mod download
    go mod verify
    go build -mod=readonly -trimpath -buildmode=pie -ldflags="-s -w" -o "$out/libusque.so" .
)
readelf -hW "$out/libusque.so" | grep -F "$machine"
readelf -hW "$out/libusque.so" | grep -E "Type:[[:space:]]+DYN"
go version -m "$out/libusque.so"
sha256sum "$out/libusque.so"
