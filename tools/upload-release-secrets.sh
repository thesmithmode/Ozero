#!/usr/bin/env bash
set -euo pipefail

DIR="${1:?Usage: upload-release-secrets.sh <secrets-dir>}"
REPO="${2:-thesmithmode/Ozero}"

PASS_FILE="$DIR/.passwords"
GPG_PASS_FILE="$DIR/.gpg-pass"
PRIVATE_ASC="$DIR/private.asc"
KEYSTORE="$DIR/release.keystore"

for f in "$PASS_FILE" "$GPG_PASS_FILE" "$PRIVATE_ASC" "$KEYSTORE"; do
  [[ -f "$f" ]] || { echo "Missing: $f"; exit 1; }
done

# Read passwords БЕЗ source: source выполняет произвольный shell-код из файла,
# что = RCE если файл подделан. Парсим строки вида KEY=value через grep+cut.
parse_kv() {
  local key="$1"
  grep -E "^${key}=" "$PASS_FILE" | head -n1 | cut -d= -f2-
}
KS_PASS="$(parse_kv KS_PASS)"
KEY_PASS="$(parse_kv KEY_PASS)"
KEY_ALIAS="$(parse_kv KEY_ALIAS)"
GPG_PASS="$(cat "$GPG_PASS_FILE")"
[[ -n "$KS_PASS" && -n "$KEY_PASS" && -n "$KEY_ALIAS" ]] || {
  echo "Missing keys in $PASS_FILE"; exit 1
}

echo "Uploading secrets to $REPO..."

# Keystore (base64-encoded)
base64 -w0 "$KEYSTORE" | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"

# Keystore passwords — через stdin (--body передаёт через argv, видно в `ps aux`).
printf '%s' "$KS_PASS"   | gh secret set RELEASE_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_ALIAS" | gh secret set RELEASE_KEY_ALIAS         --repo "$REPO"
printf '%s' "$KEY_PASS"  | gh secret set RELEASE_KEY_PASSWORD      --repo "$REPO"

# GPG
gh secret set RELEASE_GPG_PRIVATE_KEY --repo "$REPO" < "$PRIVATE_ASC"
printf '%s' "$GPG_PASS" | gh secret set RELEASE_GPG_PASSPHRASE  --repo "$REPO"

echo "All 6 secrets uploaded."

# Shred local sensitive files
for f in "$PASS_FILE" "$GPG_PASS_FILE" "$PRIVATE_ASC" "$KEYSTORE"; do
  shred -u "$f" 2>/dev/null || rm -f "$f"
done

echo "Local secret files shredded."
