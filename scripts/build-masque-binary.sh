#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
revision="6aa03fc97d12848dce34eedbd187fb1077b5d1ea"
abi="${1:-arm64-v8a}"
case "$abi" in
    arm64-v8a) arch=arm64; machine="AArch64"; triple=aarch64-linux-android ;;
    x86_64) arch=amd64; machine="Advanced Micro Devices X86-64"; triple=x86_64-linux-android ;;
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

# Test the registration transport on the build host without Android or live credentials.
(
    cd "$work/usque"
    GOTOOLCHAIN=local go test -count=1 api/ozero_registration.go api/ozero_registration_test.go
)

out="$root/engine-masque/build/native/$abi"
mkdir -p "$out"
(
    cd "$work/usque"
    export GOOS=android GOARCH="$arch" CGO_ENABLED=1 GOTOOLCHAIN=local
    ndk="${MASQUE_NDK_PATH:?Android NDK r27c is required}"
    grep -Eq '^Pkg.Revision[[:space:]]*=[[:space:]]*27\.2\.12479018[[:space:]]*$' "$ndk/source.properties"
    export CC="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/${triple}24-clang"
    test -x "$CC"
    gofmt -w main.go internal/socks5.go cmd/socks.go config/config.go api/masque.go api/cloudflare.go cmd/register.go
    go mod download
    go mod verify
    go build -mod=readonly -trimpath -buildmode=pie -tags netcgo \
        -ldflags="-s -w -linkmode=external -extldflags=-Wl,-z,max-page-size=16384" -o "$out/libusque.so" .
)
readelf -hW "$out/libusque.so" | grep -F "$machine"
readelf -hW "$out/libusque.so" | grep -E "Type:[[:space:]]+DYN"
readelf -Ws "$out/libusque.so" | grep -E '[[:space:]]getaddrinfo(@|[[:space:]]|$)'
readelf -lW "$out/libusque.so" | python3 -c '
import sys
loads = [line.split() for line in sys.stdin if line.lstrip().startswith("LOAD ")]
if not loads or any(int(row[-1], 16) < 16384 for row in loads):
    sys.exit("MASQUE ELF is not 16 KiB page compatible")
'
buildinfo="$(go version -m "$out/libusque.so")"
printf '%s\n' "$buildinfo"
grep -F 'CGO_ENABLED=1' <<< "$buildinfo"
sha256sum "$out/libusque.so"
