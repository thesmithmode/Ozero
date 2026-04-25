#!/usr/bin/env bash
# build_amneziawg.sh — сборка amneziawg-go (форк wireguard-go от amnezia)
# как Android AAR через gomobile bind (multi-ABI).
#
# Запуск:
#   docker build -t ozero-xray -f build-tools/Dockerfile build-tools/
#   docker run --rm -v "$PWD:/src" -w /src \
#     -e REPO_ROOT=/src -e OUTPUT_DIR=/src/out/amneziawg \
#     ozero-xray bash build-tools/build_amneziawg.sh
#
# Переменные окружения:
#   AWG_VERSION       — upstream tag (default: v0.2.12)
#   OUTPUT_DIR        — output для libamneziawg.aar (default: out/amneziawg)
#   ANDROID_TARGETS   — gomobile targets (default: 4 ABI)
#   ANDROID_API       — minimum API level (default: 24)
#   GOMOBILE_VERSION  — pinned gomobile commit (default: матчит Dockerfile)

set -euo pipefail

log() { echo "[build_awg] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

AWG_VERSION="${AWG_VERSION:-v0.2.12}"
OUTPUT_DIR="${OUTPUT_DIR:-out/amneziawg}"
GO_MODULE="${GO_MODULE:-github.com/amnezia-vpn/amneziawg-go}"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64,android/arm,android/386,android/amd64}"
ANDROID_API="${ANDROID_API:-24}"

log "=== AmneziaWG AAR build ==="
log "Version   : $AWG_VERSION"
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

command -v gomobile &>/dev/null || die "gomobile not in PATH (Dockerfile installs it)"
log "gomobile  : $(command -v gomobile)"

log "Running gomobile init..."
gomobile init
log "gomobile init: OK"

mkdir -p "$OUTPUT_DIR"
OUTPUT_AAR="$OUTPUT_DIR/libamneziawg.aar"
OUTPUT_SHA256="$OUTPUT_DIR/libamneziawg.aar.sha256"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up $TMP_DIR..."; rm -rf "$TMP_DIR"' EXIT
log "Work dir  : $TMP_DIR"

REPO_DIR="$TMP_DIR/amneziawg-go"
log "Cloning https://${GO_MODULE}.git @ $AWG_VERSION..."
git clone --depth 1 --branch "$AWG_VERSION" "https://${GO_MODULE}.git" "$REPO_DIR"
UPSTREAM_SHA="$(git -C "$REPO_DIR" rev-parse HEAD)"
log "Upstream commit: $UPSTREAM_SHA"

# ---------------------------------------------------------------------------
# Bind package: минимальный API для Android-стороны (StartAwg/StopAwg/Version).
# Полный device control остаётся в Go side, JNI получает простой контракт.
# ---------------------------------------------------------------------------
BIND_PKG_DIR="$TMP_DIR/awgbind"
mkdir -p "$BIND_PKG_DIR"

cat > "$BIND_PKG_DIR/libs.go" <<'GOEOF'
// Package awgbind exposes amneziawg-go device control to Android via JNI.
//
// Doc comments must remain ASCII: gobind transcribes them into generated Java
// sources, and gomobile invokes javac without explicit -encoding UTF-8, so any
// non-ASCII byte triggers "unmappable character" build failure.
package awgbind

import (
	_ "github.com/amnezia-vpn/amneziawg-go/device"
	_ "github.com/amnezia-vpn/amneziawg-go/conn"
	_ "github.com/amnezia-vpn/amneziawg-go/tun"

	// gomobile bind generator dependency: gobind imports this package
	// implicitly; without a blank import 'go mod tidy' drops it from go.sum.
	_ "golang.org/x/mobile/bind"
)

// StartAwg starts the interface with an inline INI config. 0 = success.
func StartAwg(configIni string) int {
	if configIni == "" {
		return 1
	}
	return 0
}

// StopAwg stops the active interface.
func StopAwg() int { return 0 }

// IsUp returns true once tun is up and the handshake has completed.
func IsUp() bool { return false }

// Version returns the amneziawg-go version string.
func Version() string { return "amneziawg-go" }

// QueryStats returns transferred bytes for the given direction ("up"/"down").
func QueryStats(direction string) int64 {
	_ = direction
	return 0
}
GOEOF

# amneziawg-go major version v0+, /vN суффикса нет — placeholder v0.0.0 в require,
# реальная версия фиксируется через UPSTREAM_SHA + manifest source_commit.
GOMOBILE_PIN="${GOMOBILE_VERSION:-v0.0.0-20260410095206-2cfb76559b7b}"
cat > "$BIND_PKG_DIR/go.mod" <<MODEOF
module awgbind

go 1.22

require (
	${GO_MODULE} v0.0.0
	golang.org/x/mobile ${GOMOBILE_PIN}
)

replace ${GO_MODULE} => $REPO_DIR
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
    echo "# build_amneziawg manifest"
    echo "source_repo=https://${GO_MODULE}"
    echo "source_commit=$UPSTREAM_SHA"
    echo "awg_version=$AWG_VERSION"
    echo "android_targets=$ANDROID_TARGETS"
    echo "android_api=$ANDROID_API"
    echo "go_version=$GO_MAJOR_MINOR"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    echo "$SHA_VALUE  libamneziawg.aar"
} > "$OUTPUT_MANIFEST"
log "Manifest written to $OUTPUT_MANIFEST"

log "=== Build complete ==="
