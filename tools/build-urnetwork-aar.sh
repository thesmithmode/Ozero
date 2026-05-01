#!/usr/bin/env bash
# build-urnetwork-aar.sh — сборка URnetworkSdk.aar из github.com/urnetwork/sdk
# через gomobile bind для Android.
#
# Upstream использует пакет github.com/urnetwork/sdk напрямую (не субпакет).
# Java package: com.bringyour (задан в upstream Makefile через -javapkg com.bringyour)
#
# Запуск:
#   RUNNER_TEMP=/tmp/urnetwork bash tools/build-urnetwork-aar.sh
#
# Переменные окружения:
#   SDK_VERSION       — тег/ветка upstream sdk (default: main)
#   RUNNER_TEMP       — рабочая директория для клона (default: /tmp/urnetwork-build)
#   OUTPUT_DIR        — куда положить AAR (default: engine-urnetwork/libs)
#   ANDROID_API       — minimum API level (default: 24)
#   ANDROID_TARGETS   — gomobile targets (default: android/arm64,android/arm,android/amd64)
#   JAVA_PKG          — java package prefix (default: com.bringyour)
#
# БЛОКЕРЫ (см. README engine-urnetwork/README.md §Blockers):
#   1. Требует Go >= 1.25.0 с GOEXPERIMENT=greenteagc
#   2. Зависимость github.com/urnetwork/connect (публичная, но требует go replace директив)
#   3. github.com/urnetwork/glog — собственная ветка glog
#   Без resolving этих зависимостей gomobile bind завершится ошибкой линковки.

set -euo pipefail

log() { echo "[build-urnetwork-aar] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

SDK_VERSION="${SDK_VERSION:-main}"
RUNNER_TEMP="${RUNNER_TEMP:-/tmp/urnetwork-build}"
OUTPUT_DIR="${OUTPUT_DIR:-engine-urnetwork/libs}"
ANDROID_API="${ANDROID_API:-24}"
ANDROID_TARGETS="${ANDROID_TARGETS:-android/arm64,android/arm,android/amd64}"
JAVA_PKG="${JAVA_PKG:-com.bringyour}"

# Защита от git parameter injection в --branch: SDK_VERSION должен быть только
# tag/branch именем (буквы, цифры, точки, подчёркивания, слэши, дефисы).
[[ "$SDK_VERSION" =~ ^[A-Za-z0-9_./-]+$ ]] || die "Invalid SDK_VERSION: $SDK_VERSION"
[[ "$ANDROID_API" =~ ^[0-9]+$ ]] || die "Invalid ANDROID_API: $ANDROID_API"

log "=== URnetwork AAR build ==="
log "SDK version : $SDK_VERSION"
log "RUNNER_TEMP : $RUNNER_TEMP"
log "Output dir  : $OUTPUT_DIR"
log "Targets     : $ANDROID_TARGETS"
log "API level   : $ANDROID_API"
log "Java pkg    : $JAVA_PKG"

# --- Проверки окружения ---
command -v go &>/dev/null || die "Go не найден в PATH (требуется Go >= 1.25)"
GO_VERSION_RAW="$(go version)"
log "Go          : $GO_VERSION_RAW"

command -v gomobile &>/dev/null || die "gomobile не найден (установить: go install golang.org/x/mobile/cmd/gomobile@latest)"
[[ -n "${ANDROID_NDK_ROOT:-}${ANDROID_NDK_HOME:-}" ]] || die "ANDROID_NDK_ROOT или ANDROID_NDK_HOME не задан"

# --- Клонирование urnetwork/sdk ---
SDK_DIR="$RUNNER_TEMP/urnetwork-sdk"
if [[ -d "$SDK_DIR/.git" ]]; then
    log "SDK уже клонирован, обновляем..."
    git -C "$SDK_DIR" fetch --depth 1 origin "$SDK_VERSION"
    git -C "$SDK_DIR" checkout FETCH_HEAD
else
    log "Клонируем urnetwork/sdk @ $SDK_VERSION..."
    mkdir -p "$RUNNER_TEMP"
    git clone --depth 1 --branch "$SDK_VERSION" \
        https://github.com/urnetwork/sdk.git \
        "$SDK_DIR" 2>/dev/null || \
    git clone --depth 1 \
        https://github.com/urnetwork/sdk.git \
        "$SDK_DIR"
fi

log "SDK клонирован: $(git -C "$SDK_DIR" rev-parse HEAD)"

# --- Подтягиваем зависимости ---
log "Загружаем Go зависимости..."
# urnetwork/connect использует replace-директивы в go.mod upstream
# Клонируем зависимости рядом
CONNECT_DIR="$RUNNER_TEMP/urnetwork-connect"
GLOG_DIR="$RUNNER_TEMP/urnetwork-glog"

if [[ ! -d "$CONNECT_DIR/.git" ]]; then
    git clone --depth 1 https://github.com/urnetwork/connect.git "$CONNECT_DIR"
fi
if [[ ! -d "$GLOG_DIR/.git" ]]; then
    git clone --depth 1 https://github.com/urnetwork/glog.git "$GLOG_DIR" 2>/dev/null || \
        log "WARN: urnetwork/glog не найден, сборка может упасть"
fi

# Инжектируем replace директивы для локальных зависимостей
cd "$SDK_DIR"
log "Добавляем go replace директивы..."
go mod edit -replace="github.com/urnetwork/connect=$CONNECT_DIR"
if [[ -d "$GLOG_DIR" ]]; then
    go mod edit -replace="github.com/urnetwork/glog=$GLOG_DIR"
fi

# Добавляем dep golang.org/x/mobile (без него прямой gomobile bind на sdk
# падает "no Go package in golang.org/x/mobile/bind"). Затем кладём
# bind_dep.go с blank import, чтобы go mod tidy не tidy'нул эту dep.
GOMOBILE_PIN="${GOMOBILE_VERSION:-v0.0.0-20260410095206-2cfb76559b7b}"
go mod edit -require="golang.org/x/mobile@${GOMOBILE_PIN}"
cat > "$SDK_DIR/bind_dep.go" <<'GOEOF'
// Package sdk: bind_dep.go forces dependency on golang.org/x/mobile/bind
// so that `gomobile bind github.com/urnetwork/sdk` resolves it.
//
// Doc comments must remain ASCII: gobind transcribes them into generated Java
// sources and gomobile invokes javac without explicit -encoding UTF-8.
package sdk

import _ "golang.org/x/mobile/bind"
GOEOF

go mod tidy
go mod download

# --- gomobile init ---
log "Запускаем gomobile init..."
export GOEXPERIMENT=greenteagc
export GODEBUG=gotypesalias=0
gomobile init

# --- Сборка AAR ---
mkdir -p "$RUNNER_TEMP/aar-out"
AAR_PATH="$RUNNER_TEMP/aar-out/URnetworkSdk.aar"

log "Запускаем gomobile bind на github.com/urnetwork/sdk..."
# JAVA_TOOL_OPTIONS подхватывается javac (запускается gomobile внутри)
# и устанавливает file.encoding=UTF-8. Без этого javac на Linux fall back
# на US-ASCII и падает на не-ASCII chars в Java doc-comments, которые
# gobind транскрибирует из Go source.
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
gomobile bind \
    -target "$ANDROID_TARGETS" \
    -androidapi "$ANDROID_API" \
    -javapkg "$JAVA_PKG" \
    -trimpath \
    -gcflags "-dwarf=false" \
    -ldflags "-s -w -compressdwarf=false -buildid= -checklinkname=0" \
    -o "$AAR_PATH" \
    "github.com/urnetwork/sdk"

log "AAR собран: $AAR_PATH"

# --- Копирование в output ---
mkdir -p "$OUTPUT_DIR"
cp "$AAR_PATH" "$OUTPUT_DIR/URnetworkSdk.aar"
sha256sum "$OUTPUT_DIR/URnetworkSdk.aar" | cut -d' ' -f1 > "$OUTPUT_DIR/URnetworkSdk.aar.sha256"

SDK_COMMIT="$(git -C "$SDK_DIR" rev-parse HEAD)"
echo "sdk_commit: $SDK_COMMIT" > "$OUTPUT_DIR/manifest.txt"
echo "sdk_version: $SDK_VERSION" >> "$OUTPUT_DIR/manifest.txt"
echo "android_api: $ANDROID_API" >> "$OUTPUT_DIR/manifest.txt"
echo "targets: $ANDROID_TARGETS" >> "$OUTPUT_DIR/manifest.txt"
echo "sha256: $(cat "$OUTPUT_DIR/URnetworkSdk.aar.sha256")" >> "$OUTPUT_DIR/manifest.txt"

log "=== URnetwork AAR build DONE ==="
log "AAR  : $OUTPUT_DIR/URnetworkSdk.aar"
log "SHA  : $(cat "$OUTPUT_DIR/URnetworkSdk.aar.sha256")"
