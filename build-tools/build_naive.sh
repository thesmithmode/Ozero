#!/usr/bin/env bash
# build_naive.sh — извлекает libnaive.so из upstream APK plugin (4 ABI).
#
# klzgrad/naiveproxy с v143+ публикует только APK plugin (`naiveproxy-plugin-vX-<abi>.apk`),
# содержащий внутри `lib/<abi>/libnaive.so`. Сборка из исходников невозможна в CI
# (Chromium ~30 ГБ + 1+ часа). Минимальный supply-chain путь — взять подписанный
# upstream APK и распаковать .so.
#
# Источник: github.com/klzgrad/naiveproxy/releases.
# Output: $OUT_DIR/libnaive-<abi>.so + $OUT_DIR/manifest.txt
#
# Переменные окружения:
#   NAIVE_VERSION  — upstream tag (default: v147.0.7727.49-1)
#   OUT_DIR        — куда положить артефакты (default: $REPO_ROOT/out/naive)
#   ABIS           — список ABI через пробел (default: arm64-v8a armeabi-v7a x86_64 x86)

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/out/naive}"
NAIVE_VERSION="${NAIVE_VERSION:-v147.0.7727.49-1}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"

log() { echo "[build_naive] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

for tool in curl unzip sha256sum; do
    command -v "$tool" &>/dev/null || die "$tool required"
done

log "=== NaiveProxy native extract ==="
log "Version : $NAIVE_VERSION"
log "ABIs    : $ABIS"
log "Output  : $OUT_DIR"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up $TMP_DIR..."; rm -rf "$TMP_DIR"' EXIT
log "Work dir: $TMP_DIR"

for ABI in $ABIS; do
    APK_NAME="naiveproxy-plugin-${NAIVE_VERSION}-${ABI}.apk"
    APK_URL="https://github.com/klzgrad/naiveproxy/releases/download/${NAIVE_VERSION}/${APK_NAME}"
    APK_PATH="$TMP_DIR/$APK_NAME"

    log "Downloading $APK_URL"
    curl -fsSL --retry 3 -o "$APK_PATH" "$APK_URL" || die "не удалось скачать $APK_URL"

    EXTRACT_DIR="$TMP_DIR/extract-$ABI"
    mkdir -p "$EXTRACT_DIR"
    unzip -q -o "$APK_PATH" "lib/$ABI/libnaive.so" -d "$EXTRACT_DIR"

    SRC_SO="$EXTRACT_DIR/lib/$ABI/libnaive.so"
    [[ -f "$SRC_SO" ]] || die "libnaive.so не найден в $APK_NAME"

    DEST="$OUT_DIR/libnaive-$ABI.so"
    cp "$SRC_SO" "$DEST"
    chmod +x "$DEST"
    log "  $DEST ($(stat -c%s "$DEST") bytes)"
done

# Manifest в формате regen_lock.py: source_repo + source_commit + SHA256 блок.
# upstream_commit для tag берём через GitHub API (не клонируем репо).
SOURCE_COMMIT="$(curl -fsSL \
    "https://api.github.com/repos/klzgrad/naiveproxy/git/refs/tags/${NAIVE_VERSION}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['object']['sha'])")"

[[ -n "$SOURCE_COMMIT" ]] || die "не удалось получить commit SHA для $NAIVE_VERSION"

{
    echo "# build_naive manifest"
    echo "source_repo=https://github.com/klzgrad/naiveproxy"
    echo "source_commit=$SOURCE_COMMIT"
    echo "naive_version=$NAIVE_VERSION"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    cd "$OUT_DIR"
    for f in libnaive-*.so; do
        sha256sum "$f"
    done
} > "$OUT_DIR/manifest.txt"

log "=== Build complete ==="
ls -la "$OUT_DIR"
