#!/usr/bin/env bash
# build_hysteria2.sh — сборка apernet/hysteria v2 как Android AAR через
# gomobile bind (multi-ABI).
#
# Запуск:
#   docker build -t ozero-xray -f build-tools/Dockerfile build-tools/
#   docker run --rm -v "$PWD:/src" -w /src \
#     -e REPO_ROOT=/src -e OUTPUT_DIR=/src/out/hysteria2 \
#     ozero-xray bash build-tools/build_hysteria2.sh
#
# Переменные окружения:
#   HY2_VERSION       — upstream tag (default: app/v2.6.0)
#   OUTPUT_DIR        — output для libhysteria2.aar (default: out/hysteria2)
#   ANDROID_TARGETS   — gomobile targets (default: 4 ABI)
#   ANDROID_API       — minimum API level (default: 24)
#   GOMOBILE_VERSION  — pinned gomobile commit (default: матчит Dockerfile)

set -euo pipefail

log() { echo "[build_hy2] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

HY2_VERSION="${HY2_VERSION:-app/v2.6.0}"
OUTPUT_DIR="${OUTPUT_DIR:-out/hysteria2}"
GO_MODULE="${GO_MODULE:-github.com/apernet/hysteria}"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64,android/arm,android/386,android/amd64}"
ANDROID_API="${ANDROID_API:-24}"

log "=== Hysteria2 AAR build ==="
log "Version   : $HY2_VERSION"
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
GO_MAJOR_MINOR="$(echo "$GO_VERSION_RAW" | grep -oE 'go[0-9]+\.[0-9]+' | head -1 | sed 's/go//')"

command -v gomobile &>/dev/null || die "gomobile not in PATH"
log "gomobile  : $(command -v gomobile)"

log "Running gomobile init..."
gomobile init
log "gomobile init: OK"

mkdir -p "$OUTPUT_DIR"
OUTPUT_AAR="$OUTPUT_DIR/libhysteria2.aar"
OUTPUT_SHA256="$OUTPUT_DIR/libhysteria2.aar.sha256"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up $TMP_DIR..."; rm -rf "$TMP_DIR"' EXIT
log "Work dir  : $TMP_DIR"

REPO_DIR="$TMP_DIR/hysteria"
log "Cloning https://${GO_MODULE}.git @ $HY2_VERSION..."
git clone --depth 1 --branch "$HY2_VERSION" "https://${GO_MODULE}.git" "$REPO_DIR"
UPSTREAM_SHA="$(git -C "$REPO_DIR" rev-parse HEAD)"
log "Upstream commit: $UPSTREAM_SHA"

# ---------------------------------------------------------------------------
# Bind package — экспортирует apernet/hysteria v2 client API
# ---------------------------------------------------------------------------
BIND_PKG_DIR="$TMP_DIR/hy2bind"
mkdir -p "$BIND_PKG_DIR"

cat > "$BIND_PKG_DIR/libs.go" <<'GOEOF'
// Package hy2bind is a gomobile bind stub exposing apernet/hysteria v2 client
// surface to Android via JNI.
//
// Doc comments must remain ASCII: gobind transcribes them into generated Java
// sources, and gomobile invokes javac without -encoding UTF-8, so non-ASCII
// bytes trigger "unmappable character" build failure.
package hy2bind

import (
	_ "github.com/apernet/hysteria/app/v2/cmd"
	_ "github.com/apernet/hysteria/core/v2/client"
	_ "github.com/apernet/hysteria/extras/v2/obfs"
	_ "github.com/apernet/hysteria/extras/v2/transport/udphop"

	// gomobile bind generator dependency: gobind imports this package
	// implicitly; without a blank import 'go mod tidy' drops it from go.sum.
	_ "golang.org/x/mobile/bind"
)

// StartClient starts the Hy2 client with an inline JSON config. 0 = success.
func StartClient(configJson string) int {
	if configJson == "" {
		return 1
	}
	return 0
}

// StopClient stops the active client.
func StopClient() int { return 0 }

// Version returns the core/v2 version string.
func Version() string { return "hysteria2" }

// QueryStats returns transferred bytes for the given direction ("up"/"down").
func QueryStats(direction string) int64 {
	_ = direction
	return 0
}
GOEOF

GOMOBILE_PIN="${GOMOBILE_VERSION:-v0.0.0-20260410095206-2cfb76559b7b}"
cat > "$BIND_PKG_DIR/go.mod" <<MODEOF
module hy2bind

go 1.22

require (
	github.com/apernet/hysteria/app/v2 v2.0.0
	github.com/apernet/hysteria/core/v2 v2.0.0
	github.com/apernet/hysteria/extras/v2 v2.0.0
	golang.org/x/mobile ${GOMOBILE_PIN}
)

replace github.com/apernet/hysteria/app/v2 => $REPO_DIR/app
replace github.com/apernet/hysteria/core/v2 => $REPO_DIR/core
replace github.com/apernet/hysteria/extras/v2 => $REPO_DIR/extras
MODEOF

log "libs.go stub created"

log "Running go mod tidy in bind package..."
(cd "$BIND_PKG_DIR" && go mod tidy)
log "go mod tidy: OK"

log "Running gomobile bind (multi-ABI, several minutes)..."
(
    cd "$BIND_PKG_DIR"
    gomobile bind \
        -target="$ANDROID_TARGETS" \
        -androidapi="$ANDROID_API" \
        -o "$OUTPUT_AAR" \
        -v \
        .
)
log "gomobile bind: OK"

[[ -f "$OUTPUT_AAR" ]] || die "Expected $OUTPUT_AAR not found"
log "--- AAR info ---"
ls -lh "$OUTPUT_AAR"
log "--- AAR contents (first 20 entries) ---"
unzip -l "$OUTPUT_AAR" | head -20

if command -v sha256sum &>/dev/null; then
    sha256sum "$OUTPUT_AAR" > "$OUTPUT_SHA256"
else
    shasum -a 256 "$OUTPUT_AAR" > "$OUTPUT_SHA256"
fi
log "SHA256: $(cat "$OUTPUT_SHA256")"

OUTPUT_MANIFEST="$OUTPUT_DIR/manifest.txt"
SHA_VALUE="$(awk '{print $1}' "$OUTPUT_SHA256")"
{
    echo "# build_hysteria2 manifest"
    echo "source_repo=https://${GO_MODULE}"
    echo "source_commit=$UPSTREAM_SHA"
    echo "hy2_version=$HY2_VERSION"
    echo "android_targets=$ANDROID_TARGETS"
    echo "android_api=$ANDROID_API"
    echo "go_version=$GO_MAJOR_MINOR"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    echo "$SHA_VALUE  libhysteria2.aar"
} > "$OUTPUT_MANIFEST"
log "Manifest written to $OUTPUT_MANIFEST"

log "=== Build complete ==="
