#!/usr/bin/env bash
# build_tor.sh — собирает tor + IPtProxy (lyrebird+snowflake+dnstt) для Android
# через download+extract из Maven Central AAR'ов.
#
# Источники:
#   info.guardianproject:tor-android:0.4.9.6  → libtor.so 4 ABI (BSD-3)
#   com.netzarchitekten:IPtProxy:5.4.1        → libgojni.so 4 ABI
#                                                (lyrebird 0.8.1 + snowflake 2.13.1
#                                                + dnstt 1.20260311.0)
#                                                MIT + GPL3 + BSD-3
#
# Сборка из исходников требует NDK + Go + gomobile + 30+ минут CI;
# Maven Central — стабильный канал с подписанными AAR. Извлекаем .so оттуда
# (как для naive из APK), пинуем sha256 в lock.
#
# Conjure отложен (нет стабильного pre-built; gotapdance Android помечен
# CURRENTLY NOT MAINTAINED в upstream).
#
# Запуск:
#   REPO_ROOT=$PWD OUT_DIR=$PWD/out/tor bash build-tools/build_tor.sh
#
# Переменные:
#   TOR_VERSION       — tor-android Maven version (default: 0.4.9.6)
#   IPTPROXY_VERSION  — IPtProxy Maven version (default: 5.4.1)
#   OUT_DIR           — output (default: $REPO_ROOT/out/tor)
#   ABIS              — список ABI (default: arm64-v8a armeabi-v7a x86_64 x86)

set -euo pipefail

log() { echo "[build_tor] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }
require_regular_file() {
    local path="$1"
    local name="$2"
    [[ ! -L "$path" ]] || die "$name is a symlink"
    [[ -f "$path" ]] || die "$name missing or not a regular file"
}

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/out/tor}"
TOR_VERSION="${TOR_VERSION:-0.4.9.6}"
IPTPROXY_VERSION="${IPTPROXY_VERSION:-5.4.1}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"

for tool in curl unzip sha256sum; do
    command -v "$tool" &>/dev/null || die "$tool required"
done

log "=== Tor + IPtProxy native extract ==="
log "tor-android version : $TOR_VERSION"
log "IPtProxy version    : $IPTPROXY_VERSION"
log "ABIs                : $ABIS"
log "Output              : $OUT_DIR"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up $TMP_DIR..."; rm -rf "$TMP_DIR"' EXIT
log "Work dir            : $TMP_DIR"

# ---------------------------------------------------------------------------
# 1. tor-android AAR → libtor.so per ABI
# ---------------------------------------------------------------------------
TOR_AAR_NAME="tor-android-${TOR_VERSION}.aar"
TOR_AAR_URL="https://repo1.maven.org/maven2/info/guardianproject/tor-android/${TOR_VERSION}/${TOR_AAR_NAME}"
TOR_AAR_PATH="$TMP_DIR/$TOR_AAR_NAME"

log "Downloading $TOR_AAR_URL"
curl -fsSL --retry 3 -o "$TOR_AAR_PATH" "$TOR_AAR_URL" || die "failed: $TOR_AAR_URL"

TOR_EXTRACT="$TMP_DIR/tor-extract"
mkdir -p "$TOR_EXTRACT"
unzip -q -o "$TOR_AAR_PATH" -d "$TOR_EXTRACT"

for ABI in $ABIS; do
    SRC="$TOR_EXTRACT/jni/$ABI/libtor.so"
    require_regular_file "$SRC" "libtor.so for $ABI in tor-android AAR"
    DEST="$OUT_DIR/libtor-$ABI.so"
    cp --no-dereference "$SRC" "$DEST"
    chmod +x "$DEST"
    log "  $DEST ($(stat -c%s "$DEST") bytes)"
done

# ---------------------------------------------------------------------------
# 2. IPtProxy AAR → libgojni.so per ABI (rename → libiptproxy-<abi>.so)
# ---------------------------------------------------------------------------
IPT_AAR_NAME="IPtProxy-${IPTPROXY_VERSION}.aar"
IPT_AAR_URL="https://repo1.maven.org/maven2/com/netzarchitekten/IPtProxy/${IPTPROXY_VERSION}/${IPT_AAR_NAME}"
IPT_AAR_PATH="$TMP_DIR/$IPT_AAR_NAME"

log "Downloading $IPT_AAR_URL"
curl -fsSL --retry 3 -o "$IPT_AAR_PATH" "$IPT_AAR_URL" || die "failed: $IPT_AAR_URL"

IPT_EXTRACT="$TMP_DIR/iptproxy-extract"
mkdir -p "$IPT_EXTRACT"
unzip -q -o "$IPT_AAR_PATH" -d "$IPT_EXTRACT"

for ABI in $ABIS; do
    SRC="$IPT_EXTRACT/jni/$ABI/libgojni.so"
    require_regular_file "$SRC" "libgojni.so for $ABI in IPtProxy AAR"
    DEST="$OUT_DIR/libiptproxy-$ABI.so"
    cp --no-dereference "$SRC" "$DEST"
    chmod +x "$DEST"
    log "  $DEST ($(stat -c%s "$DEST") bytes)"
done

# ---------------------------------------------------------------------------
# 3. Два manifest'а — tor и iptproxy отдельно, чтобы regen_lock.py получал
# правильный source_repo / source_commit для каждого engine. Один Release tag
# (tor-<sha8>) несёт оба набора .so и оба manifest'а.
# ---------------------------------------------------------------------------
{
    echo "# build_tor manifest (tor-android only)"
    echo "source_repo=https://github.com/guardianproject/tor-android"
    echo "source_commit=tor-android-${TOR_VERSION}"
    echo "tor_android_version=$TOR_VERSION"
    echo "abis=$ABIS"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    cd "$OUT_DIR"
    for f in libtor-*.so; do
        sha256sum "$f"
    done
} > "$OUT_DIR/manifest-tor.txt"

{
    echo "# build_iptproxy manifest (lyrebird + snowflake + dnstt bundled)"
    echo "source_repo=https://github.com/tladesignz/IPtProxy"
    echo "source_commit=v${IPTPROXY_VERSION}"
    echo "iptproxy_version=$IPTPROXY_VERSION"
    echo "iptproxy_components=lyrebird-0.8.1+snowflake-2.13.1+dnstt-1.20260311.0"
    echo "abis=$ABIS"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "# SHA256:"
    cd "$OUT_DIR"
    for f in libiptproxy-*.so; do
        sha256sum "$f"
    done
} > "$OUT_DIR/manifest-iptproxy.txt"

log "=== Build complete ==="
ls -la "$OUT_DIR"
