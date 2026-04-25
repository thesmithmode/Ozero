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
# Xray-core version. Можно override: XRAY_VERSION=v25.12.8 ./build-tools/build_xray.sh
# v25.10.1 (старый default) никогда не выпускался upstream — checkout failed.
# v25.12.8 — latest stable на 2026-04 (см. https://github.com/XTLS/Xray-core/releases).
XRAY_VERSION="${XRAY_VERSION:-v25.12.8}"

# Куда сохранить артефакт. Default `out/xray` (CI binaries.yml).
# Локально для drop-in в engine: OUTPUT_DIR=engine-xray/libs ./build-tools/build_xray.sh
OUTPUT_DIR="${OUTPUT_DIR:-out/xray}"

# Go module upstream
GO_MODULE="${GO_MODULE:-github.com/xtls/xray-core}"

# Целевые ABI. Default — все 4 ABI для production AAR (используется F-Droid + универсальный APK).
# Локально для dev: ANDROID_TARGETS=android/arm64 ./build-tools/build_xray.sh — быстрее.
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64,android/arm,android/386,android/amd64}"

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

UPSTREAM_SHA="$(git -C "${REPO_DIR}" rev-parse HEAD)"
log "Clone: OK"
log "Upstream commit: ${UPSTREAM_SHA}"

# ---------------------------------------------------------------------------
# Создание libs.go — stub для gomobile bind
#
# gomobile bind требует отдельный package, который импортирует нужные
# пакеты xray-core. Экспортируем публичный API основных подсистем.
# ---------------------------------------------------------------------------
BIND_PKG_DIR="${TMP_DIR}/xraybind"
mkdir -p "${BIND_PKG_DIR}"

cat > "${BIND_PKG_DIR}/libs.go" <<'GOEOF'
// Package xraybind is a gomobile bind stub that exposes Xray-core to Android
// via JNI. gomobile bind requires at least one exported symbol — we expose
// Version() so the AAR is non-trivial; the side-effect imports register all
// proxy/transport/app subsystems with the global registry so a runtime config
// (built in Kotlin via Xray JSON) finds them.
package xraybind

import (
	"github.com/xtls/xray-core/core"

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

	// gomobile bind generator dependency: gobind импортирует этот пакет
	// программно, без blank import `go mod tidy` выбросит его из go.sum.
	_ "golang.org/x/mobile/bind"
)

// Version returns the Xray-core version string baked into the AAR.
func Version() string {
	return core.Version()
}
GOEOF

# Инициализируем go module для bind-пакета. Xray-core имеет major version >=2
# (v25.x) без /vN суффикса в module path, поэтому Go modules отказывается
# принимать "v25.x.x" в require ("should be v0 or v1, not v25"). Стандартный
# воркэраунд при локальном replace — placeholder v0.0.0; настоящая версия
# фиксируется через git checkout (см. UPSTREAM_SHA + manifest source_commit).
#
# golang.org/x/mobile в require обязателен — gobind импортирует подпакет
# golang.org/x/mobile/bind во время генерации Java/Go wrappers.
GOMOBILE_PIN="${GOMOBILE_VERSION:-v0.0.0-20260410095206-2cfb76559b7b}"
cat > "${BIND_PKG_DIR}/go.mod" <<MODEOF
module xraybind

go 1.22

require (
	github.com/xtls/xray-core v0.0.0
	golang.org/x/mobile ${GOMOBILE_PIN}
)

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
# Manifest — метаданные сборки для regen_lock.py + аудита reproducibility
# Формат совместим с build-tools/regen_lock.py (key=value + блок "# SHA256:").
# ---------------------------------------------------------------------------
OUTPUT_MANIFEST="${OUTPUT_DIR}/manifest.txt"
SHA_VALUE="$(awk '{print $1}' "${OUTPUT_SHA256}")"
{
    echo "# build_xray manifest"
    echo "source_repo=https://${GO_MODULE}"
    echo "source_commit=${UPSTREAM_SHA}"
    echo "xray_version=${XRAY_VERSION}"
    echo "android_targets=${ANDROID_TARGETS}"
    echo "android_api=${ANDROID_API}"
    echo "go_version=${GO_MAJOR_MINOR}"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    echo "${SHA_VALUE}  libxray.aar"
} > "${OUTPUT_MANIFEST}"
log "Manifest written to ${OUTPUT_MANIFEST}"

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
log "=== Build complete ==="
log "Artifact  : ${OUTPUT_AAR}"
log "Checksum  : ${OUTPUT_SHA256}"
log "Manifest  : ${OUTPUT_MANIFEST}"
