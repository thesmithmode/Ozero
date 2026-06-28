#!/usr/bin/env bash
# build_xray.sh — сборка xtls/Xray-core как Android AAR через gomobile bind
# (multi-ABI). Pattern идентичен build_amneziawg.sh / build_hysteria2.sh.
#
# Запуск:
#   docker build -t ozero-xray -f build-tools/Dockerfile build-tools/
#   docker run --rm -v "$PWD:/src" -w /src \
#     -e REPO_ROOT=/src -e OUTPUT_DIR=/src/out/xray \
#     ozero-xray bash build-tools/build_xray.sh
#
# Переменные окружения:
#   XRAY_VERSION      — upstream tag (default: v25.10.5 — последний stable на 2026-04)
#   OUTPUT_DIR        — output для libxray.aar (default: out/xray)
#   ANDROID_TARGETS   — gomobile targets (default: 3 ABI без x86)
#   ANDROID_API       — minimum API level (default: 24)
#
# Output:
#   $OUTPUT_DIR/libxray.aar
#   $OUTPUT_DIR/libxray-sources.jar
#   $OUTPUT_DIR/libxray.aar.sha256

set -euo pipefail

log() { echo "[build_xray] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

XRAY_VERSION="${XRAY_VERSION:-v25.10.5}"
OUTPUT_DIR="${OUTPUT_DIR:-out/xray}"
GO_MODULE="${GO_MODULE:-github.com/xtls/Xray-core}"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64,android/arm,android/amd64}"
ANDROID_API="${ANDROID_API:-24}"

log "=== Xray-core AAR build ==="
log "Version   : $XRAY_VERSION"
log "Module    : $GO_MODULE"
log "Targets   : $ANDROID_TARGETS"
log "API level : $ANDROID_API"
log "Output    : $OUTPUT_DIR"

[[ -n "${ANDROID_NDK_ROOT:-}" ]] || die "ANDROID_NDK_ROOT not set"
[[ -d "$ANDROID_NDK_ROOT" ]] || die "ANDROID_NDK_ROOT='$ANDROID_NDK_ROOT' missing"
log "NDK root  : $ANDROID_NDK_ROOT"

command -v go &>/dev/null || die "Go not in PATH"
GO_VERSION_RAW="$(go version)"
log "Go        : $GO_VERSION_RAW"

command -v gomobile &>/dev/null || die "gomobile not in PATH"
log "gomobile  : $(command -v gomobile)"

log "Running gomobile init..."
gomobile init
log "gomobile init: OK"

WORKDIR="$(mktemp -d -t xray-build-XXXXXX)"
trap 'rm -rf "$WORKDIR"' EXIT
log "Workdir   : $WORKDIR"

cd "$WORKDIR"
log "Cloning $GO_MODULE @ $XRAY_VERSION..."
git clone --depth 1 --branch "$XRAY_VERSION" "https://$GO_MODULE.git" xray-src
cd xray-src

log "Tidying go modules..."
go mod tidy

mkdir -p "$REPO_ROOT/$OUTPUT_DIR"
OUTPUT_ABS="$REPO_ROOT/$OUTPUT_DIR/libxray.aar"

log "Running gomobile bind (this takes 5-10 min)..."
gomobile bind \
    -target="$ANDROID_TARGETS" \
    -androidapi "$ANDROID_API" \
    -ldflags="-s -w" \
    -o "$OUTPUT_ABS" \
    ./...

[[ -f "$OUTPUT_ABS" ]] || die "AAR не создан: $OUTPUT_ABS"
log "AAR built : $OUTPUT_ABS"
log "Size      : $(du -h "$OUTPUT_ABS" | cut -f1)"

SOURCE_COMMIT="$(git rev-parse HEAD)"

cd "$REPO_ROOT/$OUTPUT_DIR"
sha256sum libxray.aar > libxray.aar.sha256
cat > manifest.txt <<EOF
source_repo=https://$GO_MODULE
source_commit=$SOURCE_COMMIT
xray_version=$XRAY_VERSION

# SHA256:
$(cat libxray.aar.sha256)
EOF
log "SHA256    : $(cat libxray.aar.sha256)"

log "=== build_xray.sh COMPLETE ==="
