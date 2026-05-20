#!/usr/bin/env bash
set -euo pipefail

MASTERDNS_REPO="${MASTERDNS_REPO:-https://github.com/masterking32/MasterDnsVPN.git}"
MASTERDNS_REF="${MASTERDNS_REF:-main}"
OUT_DIR="${OUT_DIR:-$(pwd)/build/masterdns-binaries}"

mkdir -p "$OUT_DIR"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Cloning MasterDnsVPN at $MASTERDNS_REF"
git clone --depth 1 --branch "$MASTERDNS_REF" "$MASTERDNS_REPO" "$TMP_DIR/src"
cd "$TMP_DIR/src"
COMMIT="$(git rev-parse HEAD)"
COMMIT_SHORT="$(git rev-parse --short HEAD)"
echo "Source commit: $COMMIT"

go mod download

for ABI in arm64-v8a; do
    case "$ABI" in
        arm64-v8a) GOARCH=arm64 ;;
        armeabi-v7a) GOARCH=arm GOARM=7 ;;
        x86_64) GOARCH=amd64 ;;
        *) echo "Unsupported ABI: $ABI"; exit 1 ;;
    esac

    OUT_FILE="$OUT_DIR/libmdnsvpn-$ABI.so"
    echo "Building $OUT_FILE (GOARCH=$GOARCH)"

    GOOS=linux GOARCH="$GOARCH" CGO_ENABLED=0 \
        go build \
            -ldflags="-s -w" \
            -trimpath \
            -o "$OUT_FILE" \
            ./cmd/client
done

cd "$OUT_DIR"
sha256sum libmdnsvpn-*.so > sha256sums.txt
{
    echo "commit=$COMMIT"
    echo "commit_short=$COMMIT_SHORT"
} > meta.txt

echo "---"
echo "Build complete: $OUT_DIR"
cat meta.txt
cat sha256sums.txt
