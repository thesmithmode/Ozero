#!/usr/bin/env bash
# build_xray.sh — сборка Xray-core как Android AAR через gomobile bind
#
# Использование:
#   export ANDROID_NDK_ROOT=/opt/android-ndk
#   ./build-tools/build_xray.sh
#
# Переменные окружения (override через env перед запуском):
#   XRAY_VERSION   — тег upstream репо (default: v25.10.1)
#   OUTPUT_DIR     — куда положить libxray.aar (default: engine-xray/libs)
#   GO_MODULE      — go module path (default: github.com/xtls/xray-core)

set -euo pipefail

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
log() {
    echo "[build_xray] $*" >&2
}

die() {
    log "ERROR: $*"
    exit 1
}

# ---------------------------------------------------------------------------
# Параметры (можно переопределить через env)
# ---------------------------------------------------------------------------
# Xray-core version. Можно override: XRAY_VERSION=v25.11.0 ./build-tools/build_xray.sh
XRAY_VERSION="${XRAY_VERSION:-v25.10.1}"

# Куда сохранить артефакт (относительно корня проекта или абсолютный путь)
OUTPUT_DIR="${OUTPUT_DIR:-engine-xray/libs}"

# Go module upstream
GO_MODULE="${GO_MODULE:-github.com/xtls/xray-core}"

# Целевые ABI. Default — только arm64 (достаточно для 95%+ устройств, быстрее).
# Для полного релиза: ANDROID_TARGETS="android/arm64,android/arm,android/386,android/amd64"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64}"

# Android API level minimum
ANDROID_API="${ANDROID_API:-24}"

# ---------------------------------------------------------------------------
# Проверка окружения
# ---------------------------------------------------------------------------
log "=== Xray-core AAR build ==="
log "Version   : ${XRAY_VERSION}"
log "Module    : ${GO_MODULE}"
log "Targets   : ${ANDROID_TARGETS}"
log "API level : ${ANDROID_API}"
log "Output    : ${OUTPUT_DIR}"

# 1. ANDROID_NDK_ROOT должен быть задан
if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    die "ANDROID_NDK_ROOT is not set. Export the path to Android NDK root, e.g.:
  export ANDROID_NDK_ROOT=/opt/android-ndk"
fi

if [[ ! -d "${ANDROID_NDK_ROOT}" ]]; then
    die "ANDROID_NDK_ROOT='${ANDROID_NDK_ROOT}' does not exist or is not a directory"
fi

log "NDK root  : ${ANDROID_NDK_ROOT}"

# 2. Проверка Go >= 1.22
if ! command -v go &>/dev/null; then
    die "Go is not installed or not in PATH. Install Go 1.22+ from https://go.dev/dl/"
fi

GO_VERSION_RAW="$(go version 2>&1)"
log "Go version: ${GO_VERSION_RAW}"

GO_MAJOR_MINOR="$(go version | grep -oE 'go[0-9]+\.[0-9]+' | head -1 | sed 's/go//')"
GO_MAJOR="$(echo "${GO_MAJOR_MINOR}" | cut -d. -f1)"
GO_MINOR="$(echo "${GO_MAJOR_MINOR}" | cut -d. -f2)"

if [[ "${GO_MAJOR}" -lt 1 ]] || { [[ "${GO_MAJOR}" -eq 1 ]] && [[ "${GO_MINOR}" -lt 22 ]]; }; then
    die "Go 1.22+ required, found ${GO_MAJOR_MINOR}. Upgrade from https://go.dev/dl/ or use:
  go install golang.org/dl/go1.22.12@latest && go1.22.12 download"
fi

log "Go check  : OK (${GO_MAJOR_MINOR} >= 1.22)"

# 3. gomobile — установить если отсутствует
if ! command -v gomobile &>/dev/null; then
    log "gomobile not found, installing..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    log "gomobile installed"
fi

GOMOBILE_BIN="$(command -v gomobile)"
log "gomobile  : ${GOMOBILE_BIN}"

# 4. gomobile init
log "Running gomobile init..."
gomobile init
log "gomobile init: OK"

# ---------------------------------------------------------------------------
# Подготовка output директории
# ---------------------------------------------------------------------------
mkdir -p "${OUTPUT_DIR}"
OUTPUT_AAR="${OUTPUT_DIR}/libxray.aar"
OUTPUT_SHA256="${OUTPUT_DIR}/libxray.aar.sha256"
log "Output AAR: ${OUTPUT_AAR}"

# ---------------------------------------------------------------------------
# Временная директория для работы
# ---------------------------------------------------------------------------
TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up ${TMP_DIR}..."; rm -rf "${TMP_DIR}"' EXIT

log "Work dir  : ${TMP_DIR}"

# ---------------------------------------------------------------------------
# Клон upstream репо и checkout тега
# ---------------------------------------------------------------------------
REPO_DIR="${TMP_DIR}/xray-core"
log "Cloning ${GO_MODULE} @ ${XRAY_VERSION}..."
git clone \
    --depth 1 \
    --branch "${XRAY_VERSION}" \
    "https://${GO_MODULE}.git" \
    "${REPO_DIR}"
log "Clone: OK"

# ---------------------------------------------------------------------------
# Создание libs.go — stub для gomobile bind
#
# gomobile bind требует отдельный package, который импортирует нужные
# пакеты xray-core. Экспортируем публичный API основных подсистем.
# ---------------------------------------------------------------------------
BIND_PKG_DIR="${TMP_DIR}/xraybind"
mkdir -p "${BIND_PKG_DIR}"

cat > "${BIND_PKG_DIR}/libs.go" <<'GOEOF'
// Package xraybind is a gomobile bind stub that exposes Xray-core public API
// to Android via JNI. Only packages with exported (uppercase) symbols that
// are gomobile-compatible (no chan, no func values, no unsafe) are listed.
//
// Add or remove imports here to control which subsystems appear in the AAR.
package xraybind

import (
	// Core runtime and version info
	_ "github.com/xtls/xray-core/core"

	// Configuration infrastructure (JSON/YAML parsing)
	_ "github.com/xtls/xray-core/infra/conf"

	// Inbound/outbound dispatcher
	_ "github.com/xtls/xray-core/app/dispatcher"

	// DNS subsystem
	_ "github.com/xtls/xray-core/app/dns"

	// Proxy implementations (VLESS, VMess, Trojan, Shadowsocks)
	_ "github.com/xtls/xray-core/proxy/vless/outbound"
	_ "github.com/xtls/xray-core/proxy/vmess/outbound"
	_ "github.com/xtls/xray-core/proxy/trojan"
	_ "github.com/xtls/xray-core/proxy/shadowsocks"

	// Transport (XTLS/TLS, WebSocket, gRPC, HTTP)
	_ "github.com/xtls/xray-core/transport/internet/tls"
	_ "github.com/xtls/xray-core/transport/internet/websocket"
	_ "github.com/xtls/xray-core/transport/internet/grpc"

	// Stats and logging
	_ "github.com/xtls/xray-core/app/stats"
	_ "github.com/xtls/xray-core/app/log"
)
GOEOF

# Инициализируем go module для bind-пакета, указываем зависимость на клон
cat > "${BIND_PKG_DIR}/go.mod" <<MODEOF
module xraybind

go 1.22

require github.com/xtls/xray-core ${XRAY_VERSION}

replace github.com/xtls/xray-core => ${REPO_DIR}
MODEOF

log "libs.go stub created"

# ---------------------------------------------------------------------------
# Разрешение зависимостей
# ---------------------------------------------------------------------------
log "Running go mod tidy in bind package..."
(cd "${BIND_PKG_DIR}" && go mod tidy)
log "go mod tidy: OK"

# ---------------------------------------------------------------------------
# gomobile bind
#
# Флаги:
#   -target       — ABI(s); default arm64. Полный список в комментарии выше.
#   -androidapi   — минимальный API level (24 = Android 7.0)
#   -o            — путь к output .aar
#   -v            — verbose для диагностики
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
# Верификация артефакта
# ---------------------------------------------------------------------------
if [[ ! -f "${OUTPUT_AAR}" ]]; then
    die "Expected output ${OUTPUT_AAR} not found after gomobile bind"
fi

log "--- AAR info ---"
ls -lh "${OUTPUT_AAR}"
log "--- AAR contents (first 20 entries) ---"
unzip -l "${OUTPUT_AAR}" | head -20

# ---------------------------------------------------------------------------
# SHA256
# ---------------------------------------------------------------------------
log "Computing SHA256..."
if command -v sha256sum &>/dev/null; then
    sha256sum "${OUTPUT_AAR}" > "${OUTPUT_SHA256}"
elif command -v shasum &>/dev/null; then
    shasum -a 256 "${OUTPUT_AAR}" > "${OUTPUT_SHA256}"
else
    die "Neither sha256sum nor shasum found; cannot compute hash"
fi

log "SHA256 written to ${OUTPUT_SHA256}"
cat "${OUTPUT_SHA256}"

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
log "=== Build complete ==="
log "Artifact  : ${OUTPUT_AAR}"
log "Checksum  : ${OUTPUT_SHA256}"
