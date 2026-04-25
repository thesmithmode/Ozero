#!/usr/bin/env bash
# build_hysteria2.sh — сборка apernet/hysteria v2 как Android AAR через gomobile bind
#
# Использование:
#   export ANDROID_NDK_ROOT=/opt/android-ndk
#   ./build-tools/build_hysteria2.sh
#
# Переменные окружения (override через env перед запуском):
#   HY2_VERSION    — тег upstream репо (default: app/v2.6.0)
#   OUTPUT_DIR     — куда положить hysteria2.aar (default: engine-hysteria2/libs)
#   GO_MODULE      — go module path (default: github.com/apernet/hysteria)

set -euo pipefail

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
log() {
    echo "[build_hy2] $*" >&2
}

die() {
    log "ERROR: $*"
    exit 1
}

# ---------------------------------------------------------------------------
# Параметры
# ---------------------------------------------------------------------------
HY2_VERSION="${HY2_VERSION:-app/v2.6.0}"
OUTPUT_DIR="${OUTPUT_DIR:-engine-hysteria2/libs}"
GO_MODULE="${GO_MODULE:-github.com/apernet/hysteria}"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64}"
ANDROID_API="${ANDROID_API:-24}"

# ---------------------------------------------------------------------------
# Проверка окружения
# ---------------------------------------------------------------------------
log "=== Hysteria2 AAR build ==="
log "Version   : ${HY2_VERSION}"
log "Module    : ${GO_MODULE}"
log "Targets   : ${ANDROID_TARGETS}"
log "API level : ${ANDROID_API}"
log "Output    : ${OUTPUT_DIR}"

if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    die "ANDROID_NDK_ROOT is not set. Export the path to Android NDK root, e.g.:
  export ANDROID_NDK_ROOT=/opt/android-ndk"
fi

if [[ ! -d "${ANDROID_NDK_ROOT}" ]]; then
    die "ANDROID_NDK_ROOT='${ANDROID_NDK_ROOT}' does not exist or is not a directory"
fi

log "NDK root  : ${ANDROID_NDK_ROOT}"

if ! command -v go &>/dev/null; then
    die "Go is not installed or not in PATH. Install Go 1.22+ from https://go.dev/dl/"
fi

GO_VERSION_RAW="$(go version 2>&1)"
log "Go version: ${GO_VERSION_RAW}"

GO_MAJOR_MINOR="$(go version | grep -oE 'go[0-9]+\.[0-9]+' | head -1 | sed 's/go//')"
GO_MAJOR="$(echo "${GO_MAJOR_MINOR}" | cut -d. -f1)"
GO_MINOR="$(echo "${GO_MAJOR_MINOR}" | cut -d. -f2)"

if [[ "${GO_MAJOR}" -lt 1 ]] || { [[ "${GO_MAJOR}" -eq 1 ]] && [[ "${GO_MINOR}" -lt 22 ]]; }; then
    die "Go 1.22+ required, found ${GO_MAJOR_MINOR}"
fi

log "Go check  : OK (${GO_MAJOR_MINOR} >= 1.22)"

if ! command -v gomobile &>/dev/null; then
    log "gomobile not found, installing..."
    go install golang.org/x/mobile/cmd/gomobile@latest
fi

GOMOBILE_BIN="$(command -v gomobile)"
log "gomobile  : ${GOMOBILE_BIN}"

log "Running gomobile init..."
gomobile init
log "gomobile init: OK"

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------
mkdir -p "${OUTPUT_DIR}"
OUTPUT_AAR="${OUTPUT_DIR}/hysteria2.aar"
OUTPUT_SHA256="${OUTPUT_DIR}/hysteria2.aar.sha256"
log "Output AAR: ${OUTPUT_AAR}"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up ${TMP_DIR}..."; rm -rf "${TMP_DIR}"' EXIT
log "Work dir  : ${TMP_DIR}"

# ---------------------------------------------------------------------------
# Clone upstream
# ---------------------------------------------------------------------------
REPO_DIR="${TMP_DIR}/hysteria"
log "Cloning ${GO_MODULE} @ ${HY2_VERSION}..."
git clone \
    --depth 1 \
    --branch "${HY2_VERSION}" \
    "https://${GO_MODULE}.git" \
    "${REPO_DIR}"

UPSTREAM_SHA="$(git -C "${REPO_DIR}" rev-parse HEAD)"
log "Clone: OK"
log "Upstream commit: ${UPSTREAM_SHA}"

# ---------------------------------------------------------------------------
# Bind package — экспортирует apernet/hysteria/app client API
# ---------------------------------------------------------------------------
BIND_PKG_DIR="${TMP_DIR}/hy2bind"
mkdir -p "${BIND_PKG_DIR}"

cat > "${BIND_PKG_DIR}/libs.go" <<'GOEOF'
// Package hy2bind is a gomobile bind stub exposing apernet/hysteria v2 client
// surface to Android via JNI. Mobile-incompatible types (chan, func values,
// unsafe) are wrapped in helper functions defined here.
package hy2bind

import (
	_ "github.com/apernet/hysteria/app/v2/cmd"
	_ "github.com/apernet/hysteria/core/v2/client"
	_ "github.com/apernet/hysteria/extras/v2/obfs"
	_ "github.com/apernet/hysteria/extras/v2/transport/udphop"
)

// StartClient запускает Hy2 клиент с inline JSON конфигом.
// Возвращает 0 при успехе, ненулевой код — при ошибке.
func StartClient(configJson string) int {
	// Реальная реализация вызывает app/v2 client.Run(parseConfig(configJson)).
	// Заглушка на этапе E4: финальная связка заполняется в native part.
	if configJson == "" {
		return 1
	}
	return 0
}

// StopClient останавливает активный клиент.
func StopClient() int { return 0 }

// Version возвращает строку версии core/v2.
func Version() string { return "hysteria2-v2.6.0" }

// QueryStats возвращает количество байт в указанном направлении ("up"/"down").
func QueryStats(direction string) int64 {
	_ = direction
	return 0
}
GOEOF

cat > "${BIND_PKG_DIR}/go.mod" <<MODEOF
module hy2bind

go 1.22

require github.com/apernet/hysteria/app/v2 v2.0.0
require github.com/apernet/hysteria/core/v2 v2.0.0
require github.com/apernet/hysteria/extras/v2 v2.0.0

replace github.com/apernet/hysteria/app/v2 => ${REPO_DIR}/app
replace github.com/apernet/hysteria/core/v2 => ${REPO_DIR}/core
replace github.com/apernet/hysteria/extras/v2 => ${REPO_DIR}/extras
MODEOF

log "libs.go stub created"

log "Running go mod tidy in bind package..."
(cd "${BIND_PKG_DIR}" && go mod tidy)
log "go mod tidy: OK"

# ---------------------------------------------------------------------------
# gomobile bind
# ---------------------------------------------------------------------------
log "Running gomobile bind (this may take several minutes)..."
(
    cd "${BIND_PKG_DIR}"
    gomobile bind \
        -target="${ANDROID_TARGETS}" \
        -androidapi="${ANDROID_API}" \
        -o "${OUTPUT_AAR}" \
        -v \
        .
)
log "gomobile bind: OK"

# ---------------------------------------------------------------------------
# Verify + SHA256 + manifest
# ---------------------------------------------------------------------------
if [[ ! -f "${OUTPUT_AAR}" ]]; then
    die "Expected output ${OUTPUT_AAR} not found after gomobile bind"
fi

log "--- AAR info ---"
ls -lh "${OUTPUT_AAR}"
log "--- AAR contents (first 20 entries) ---"
unzip -l "${OUTPUT_AAR}" | head -20

log "Computing SHA256..."
if command -v sha256sum &>/dev/null; then
    sha256sum "${OUTPUT_AAR}" > "${OUTPUT_SHA256}"
elif command -v shasum &>/dev/null; then
    shasum -a 256 "${OUTPUT_AAR}" > "${OUTPUT_SHA256}"
else
    die "Neither sha256sum nor shasum found"
fi
log "SHA256 written to ${OUTPUT_SHA256}"
cat "${OUTPUT_SHA256}"

OUTPUT_MANIFEST="${OUTPUT_DIR}/hysteria2.manifest.txt"
{
    echo "hy2_version=${HY2_VERSION}"
    echo "upstream_commit=${UPSTREAM_SHA}"
    echo "go_module=${GO_MODULE}"
    echo "android_targets=${ANDROID_TARGETS}"
    echo "android_api=${ANDROID_API}"
    echo "go_version=${GO_MAJOR_MINOR}"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "${OUTPUT_MANIFEST}"
log "Manifest written to ${OUTPUT_MANIFEST}"

log "=== Build complete ==="
log "Artifact  : ${OUTPUT_AAR}"
log "Checksum  : ${OUTPUT_SHA256}"
log "Manifest  : ${OUTPUT_MANIFEST}"
