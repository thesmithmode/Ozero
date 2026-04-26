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

# Read passwords
# shellcheck source=/dev/null
source "$PASS_FILE"
GPG_PASS="$(cat "$GPG_PASS_FILE")"

echo "Uploading secrets to $REPO..."

# Keystore (base64-encoded)
base64 -w0 "$KEYSTORE" | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"

# Keystore passwords
gh secret set RELEASE_KEYSTORE_PASSWORD --repo "$REPO" --body "$KS_PASS"
gh secret set RELEASE_KEY_ALIAS         --repo "$REPO" --body "$KEY_ALIAS"
gh secret set RELEASE_KEY_PASSWORD      --repo "$REPO" --body "$KEY_PASS"

# GPG
gh secret set RELEASE_GPG_PRIVATE_KEY --repo "$REPO" < "$PRIVATE_ASC"
gh secret set RELEASE_GPG_PASSPHRASE  --repo "$REPO" --body "$GPG_PASS"

echo "All 6 secrets uploaded."

# Shred local sensitive files
for f in "$PASS_FILE" "$GPG_PASS_FILE" "$PRIVATE_ASC" "$KEYSTORE"; do
  shred -u "$f" 2>/dev/null || rm -f "$f"
done

echo "Local secret files shredded."
