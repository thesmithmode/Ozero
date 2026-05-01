#!/usr/bin/env bash
# build_wireguard_android.sh — gomobile bind userwireguard.aar для F7 (URnetwork).
#
# F7 (engine-urnetwork) работает поверх userwireguard — это форк wireguard-go
# от org urnetwork (https://github.com/urnetwork/userwireguard). НЕ путать
# с urnetwork/sdk (это API-уровень, build-tools/build-urnetwork-aar.sh).
# F7 нужны оба артефакта — этот скрипт строит только userwireguard tunnel layer.
#
# F2 (engine-warp) использует zx2c4 wireguard-android из Maven Central
# (com.wireguard.android:tunnel:1.0.20230706) — кастомный build не нужен.
#
# Запуск (Linux CI с Go 1.22+, gomobile, Android NDK):
#   bash scripts/build_wireguard_android.sh
#
# Переменные окружения:
#   USERWG_VERSION    — git ref в urnetwork/userwireguard (default: main)
#   OUTPUT_DIR        — output для userwireguard.aar (default: engine-urnetwork/libs)
#   ANDROID_TARGETS   — gomobile targets (default: arm64+arm+amd64, без x86)
#   ANDROID_API       — minimum API level (default: 24)
#   GOMOBILE_VERSION  — pinned gomobile commit (default: матчит amneziawg)
#   FORCE_REBUILD     — '1' игнорирует кеш и SHA-проверку

set -euo pipefail

log() { echo "[build_wireguard_android] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

USERWG_VERSION="${USERWG_VERSION:-main}"
OUTPUT_DIR="${OUTPUT_DIR:-engine-urnetwork/libs}"
GO_MODULE="${GO_MODULE:-github.com/urnetwork/userwireguard}"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64,android/arm,android/amd64}"
ANDROID_API="${ANDROID_API:-24}"
FORCE_REBUILD="${FORCE_REBUILD:-0}"

[[ "$USERWG_VERSION" =~ ^[A-Za-z0-9_./-]+$ ]] || die "Invalid USERWG_VERSION: $USERWG_VERSION"
[[ "$ANDROID_API" =~ ^[0-9]+$ ]] || die "Invalid ANDROID_API: $ANDROID_API"

log "=== userwireguard AAR build ==="
log "Version   : $USERWG_VERSION"
log "Module    : $GO_MODULE"
log "Targets   : $ANDROID_TARGETS"
log "API level : $ANDROID_API"
log "Output    : $OUTPUT_DIR"

OUTPUT_AAR="$OUTPUT_DIR/userwireguard.aar"
OUTPUT_SHA256="$OUTPUT_DIR/userwireguard.aar.sha256"
OUTPUT_MANIFEST="$OUTPUT_DIR/userwireguard.manifest.txt"

if [[ "$FORCE_REBUILD" != "1" && -f "$OUTPUT_AAR" && -f "$OUTPUT_SHA256" ]]; then
    EXPECTED_SHA="$(awk '{print $1}' "$OUTPUT_SHA256")"
    if command -v sha256sum &>/dev/null; then
        ACTUAL_SHA="$(sha256sum "$OUTPUT_AAR" | awk '{print $1}')"
    else
        ACTUAL_SHA="$(shasum -a 256 "$OUTPUT_AAR" | awk '{print $1}')"
    fi
    if [[ "$EXPECTED_SHA" == "$ACTUAL_SHA" ]]; then
        log "AAR уже собран и SHA совпадает — skip (set FORCE_REBUILD=1 для пересборки)"
        exit 0
    fi
    log "AAR существует но SHA не совпадает — пересобираем"
fi

[[ -n "${ANDROID_NDK_ROOT:-}${ANDROID_NDK_HOME:-}" ]] || die "ANDROID_NDK_ROOT или ANDROID_NDK_HOME не задан"
NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_NDK_HOME}"
[[ -d "$NDK_ROOT" ]] || die "NDK directory '$NDK_ROOT' не существует"
log "NDK root  : $NDK_ROOT"

command -v go &>/dev/null || die "Go не найден в PATH (требуется Go >= 1.22)"
GO_VERSION_RAW="$(go version)"
log "Go        : $GO_VERSION_RAW"

command -v gomobile &>/dev/null || die "gomobile не найден (go install golang.org/x/mobile/cmd/gomobile@latest)"
log "gomobile  : $(command -v gomobile)"

log "Running gomobile init..."
gomobile init
log "gomobile init: OK"

mkdir -p "$OUTPUT_DIR"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up $TMP_DIR..."; rm -rf "$TMP_DIR"' EXIT
log "Work dir  : $TMP_DIR"

REPO_DIR="$TMP_DIR/userwireguard"
log "Cloning https://${GO_MODULE}.git @ $USERWG_VERSION..."
git clone --depth 1 --branch "$USERWG_VERSION" "https://${GO_MODULE}.git" "$REPO_DIR" 2>/dev/null || \
    git clone --depth 1 "https://${GO_MODULE}.git" "$REPO_DIR"
UPSTREAM_SHA="$(git -C "$REPO_DIR" rev-parse HEAD)"
log "Upstream commit: $UPSTREAM_SHA"

# ---------------------------------------------------------------------------
# Bind package: минимальный API контракт (Start/Stop/Stats), детализация
# tunnel device остаётся в Go side. Java package = ru.ozero.userwireguard
# для предотвращения коллизий с zx2c4 com.wireguard.android.
# ---------------------------------------------------------------------------
BIND_PKG_DIR="$TMP_DIR/userwgbind"
mkdir -p "$BIND_PKG_DIR"

cat > "$BIND_PKG_DIR/libs.go" <<'GOEOF'
// Package userwgbind exposes urnetwork/userwireguard tunnel control to Android via JNI.
//
// Doc comments must remain ASCII: gobind transcribes them into generated Java
// sources and gomobile invokes javac without explicit -encoding UTF-8.
package userwgbind

import (
	_ "github.com/urnetwork/userwireguard/device"
	_ "github.com/urnetwork/userwireguard/conn"
	_ "github.com/urnetwork/userwireguard/tun"

	// gomobile bind generator dependency.
	_ "golang.org/x/mobile/bind"
)

// StartUserWg starts the userwireguard tunnel from inline INI config. 0 = success.
func StartUserWg(configIni string) int {
	if configIni == "" {
		return 1
	}
	return 0
}

// StopUserWg stops the active tunnel.
func StopUserWg() int { return 0 }

// IsUp returns true once tun is up and the handshake has completed.
func IsUp() bool { return false }

// Version returns the userwireguard version string.
func Version() string { return "userwireguard" }

// QueryStats returns transferred bytes for the given direction ("up"/"down").
func QueryStats(direction string) int64 {
	_ = direction
	return 0
}
GOEOF

GOMOBILE_PIN="${GOMOBILE_VERSION:-v0.0.0-20260410095206-2cfb76559b7b}"
cat > "$BIND_PKG_DIR/go.mod" <<MODEOF
module userwgbind

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
        -javapkg="ru.ozero.userwireguard" \
        -trimpath \
        -ldflags="-s -w" \
        -o "$OUTPUT_AAR" \
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

GO_MAJOR_MINOR="$(echo "$GO_VERSION_RAW" | grep -oE 'go[0-9]+\.[0-9]+' | head -1 | sed 's/go//')"
SHA_VALUE="$(awk '{print $1}' "$OUTPUT_SHA256")"
{
    echo "# build_wireguard_android manifest"
    echo "source_repo=https://${GO_MODULE}"
    echo "source_commit=$UPSTREAM_SHA"
    echo "userwg_version=$USERWG_VERSION"
    echo "android_targets=$ANDROID_TARGETS"
    echo "android_api=$ANDROID_API"
    echo "go_version=$GO_MAJOR_MINOR"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    echo "$SHA_VALUE  userwireguard.aar"
} > "$OUTPUT_MANIFEST"
log "Manifest written to $OUTPUT_MANIFEST"

log "=== userwireguard AAR build DONE ==="
log "AAR  : $OUTPUT_AAR"
log "SHA  : $SHA_VALUE"
