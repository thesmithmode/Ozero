#!/usr/bin/env bash
# Generate Ed25519 keypair for signing release artifacts:
#   - bootstrap-servers.json (Этап 1 snapshots)
#   - self-update payloads (in-app updater)
#
# Private key → GitHub Secret ED25519_UPDATE_PRIVATE_KEY (NEVER commit!)
# Public key  → app/src/main/assets/update-pubkey.pem (committed; verified at runtime)
#
# Usage:
#   tools/keygen-ed25519.sh <out-dir>
#   tools/keygen-ed25519.sh /tmp/ozero-keys
#
set -euo pipefail
umask 077
OUT="${1:-./out-keys}"
mkdir -p "$OUT"
PRIV="$OUT/private.pem"
PUB="$OUT/public.pem"

if [[ -f "$PRIV" ]]; then
  echo "[ERROR] $PRIV already exists — remove it before regenerating" >&2
  exit 1
fi

openssl genpkey -algorithm ed25519 -out "$PRIV"
openssl pkey -in "$PRIV" -pubout -out "$PUB"
chmod 600 "$PRIV"

echo "[OK] private: $PRIV (chmod 600 — DO NOT commit)"
echo "[OK] public:  $PUB"
echo
echo "Next steps:"
echo "  1. Upload private key to GitHub Secret:"
echo "     gh secret set ED25519_UPDATE_PRIVATE_KEY < $PRIV"
echo "  2. Copy public key into APK assets:"
echo "     cp $PUB app/src/main/assets/update-pubkey.pem"
echo "  3. Commit ONLY the public key. Verify with:"
echo "     git status   # private.pem must NOT appear"
