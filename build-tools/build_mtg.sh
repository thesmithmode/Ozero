#!/usr/bin/env bash
# build_mtg.sh — собирает libmtg.so (MTG v2 MTProxy binary) для 3 ABI.
# Запуск: внутри Docker контейнера build-tools/Dockerfile.mtg.
# Output: $OUT_DIR/libmtg-<abi>.so + $OUT_DIR/manifest.txt

set -euo pipefail

MTG_REPO="${MTG_REPO:-https://github.com/9seconds/mtg}"
MTG_VERSION="${MTG_VERSION:-v2.1.7}"
OUT_DIR="${OUT_DIR:-/src/out/mtg}"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

git clone --depth 1 --branch "$MTG_VERSION" "$MTG_REPO" /tmp/mtg-src
cd /tmp/mtg-src
SOURCE_COMMIT="$(git rev-parse HEAD)"

declare -A ABI_MAP=(
    ["arm64-v8a"]="arm64"
    ["armeabi-v7a"]="arm"
    ["x86_64"]="amd64"
)
declare -A GOARM_MAP=(
    ["armeabi-v7a"]="7"
)

for ABI in "arm64-v8a" "armeabi-v7a" "x86_64"; do
    GOARCH="${ABI_MAP[$ABI]}"
    GOARM="${GOARM_MAP[$ABI]:-}"

    echo "Building $ABI (GOARCH=$GOARCH GOARM=$GOARM)..."
    CGO_ENABLED=0 GOOS=linux GOARCH="$GOARCH" GOARM="$GOARM" \
        go build -ldflags="-s -w" \
        -o "$OUT_DIR/libmtg-${ABI}.so" \
        .
    echo "  -> $OUT_DIR/libmtg-${ABI}.so ($(du -h "$OUT_DIR/libmtg-${ABI}.so" | cut -f1))"
done

{
    echo "# build_mtg manifest"
    echo "source_repo=$MTG_REPO"
    echo "source_version=$MTG_VERSION"
    echo "source_commit=$SOURCE_COMMIT"
    echo "go_version=$(go version | awk '{print $3}')"
    echo
    echo "# SHA256:"
    cd "$OUT_DIR"
    for f in libmtg-*.so; do
        sha256sum "$f"
    done
} > "$OUT_DIR/manifest.txt"

echo "Done. Artefacts in $OUT_DIR:"
ls -la "$OUT_DIR"
