#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <aar-path> <libbox-version>" >&2
  exit 2
fi

AAR_PATH="$1"
VERSION="$2"
CHECKSUM_FILE=".github/checksums/libbox-${VERSION}.aar.sha256"

if [ ! -f "$CHECKSUM_FILE" ]; then
  echo "::error::missing pinned checksum for libbox ${VERSION}: ${CHECKSUM_FILE}"
  exit 1
fi

EXPECTED=$(awk '{print $1}' "$CHECKSUM_FILE")
ACTUAL=$(sha256sum "$AAR_PATH" | awk '{print $1}')

if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "::error::libbox.aar SHA256 mismatch for ${VERSION}: ${ACTUAL} != ${EXPECTED}"
  exit 1
fi

echo "libbox.aar SHA256 OK: ${ACTUAL}"
