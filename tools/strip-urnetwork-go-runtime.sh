#!/usr/bin/env bash
# gomobile bake go-runtime (go.Seq, go.Universe, go.error) внутрь classes.jar
# КАЖДОГО выходного AAR. URnetworkSdk.aar и userwireguard.aar содержат
# идентичные классы → checkDebugDuplicateClasses падает. Удаляем go/*
# из URnetworkSdk; userwireguard остаётся как единственный источник.

set -euo pipefail

LIBS_DIR="${1:-engine-urnetwork/libs}"
TARGET="$LIBS_DIR/URnetworkSdk.aar"

if [ ! -f "$TARGET" ]; then
    echo "::error::$TARGET not found"
    exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cp "$TARGET" "$WORK/u.aar"
( cd "$WORK" && unzip -o -q u.aar classes.jar )
( cd "$WORK" && zip -q -d classes.jar 'go/*' || true )
( cd "$WORK" && zip -q u.aar classes.jar )
( cd "$WORK" && zip -q -d u.aar 'jni/*/libgojni.so' || true )
mv "$WORK/u.aar" "$TARGET"

echo "stripped go/* + jni/*/libgojni.so from $TARGET"
