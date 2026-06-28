#!/usr/bin/env bash
set -euo pipefail
# umask 077 ДО любого создания файлов — закрывает race между mktemp/echo и chmod 600.
umask 077

OUT="${1:?Usage: gpg-gen.sh <out-dir>}"
GIT_ROOT="$(git -C "$(dirname "$0")/.." rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -n "$GIT_ROOT" ]]; then
  OUT_ABS="$(realpath -m "$OUT")"
  GIT_ROOT_ABS="$(realpath -m "$GIT_ROOT")"
  if [[ "$OUT_ABS" == "$GIT_ROOT_ABS" || "$OUT_ABS" == "$GIT_ROOT_ABS"/* ]]; then
    echo "Refusing to write release secrets inside the git worktree: $OUT_ABS" >&2
    exit 1
  fi
fi
mkdir -p "$OUT"
chmod 700 "$OUT"

GPG_PASS="$(openssl rand -base64 18 | tr -d '\n')"
GPG_PASS_FILE="$OUT/.gpg-pass"
echo "$GPG_PASS" > "$GPG_PASS_FILE"
chmod 600 "$GPG_PASS_FILE"

# Use isolated GNUPGHOME so we don't pollute system keyring
GNUPGHOME="$(mktemp -d)"
export GNUPGHOME
chmod 700 "$GNUPGHOME"

# Batch key generation config
GPG_BATCH_FILE="$(mktemp)"
cat > "$GPG_BATCH_FILE" <<EOF
%echo Generating Ozero release GPG key
Key-Type: eddsa
Key-Curve: ed25519
Key-Usage: sign
Name-Real: Ozero Release
Name-Email: release@ozero.app
Expire-Date: 0
Passphrase: ${GPG_PASS}
%commit
%echo Done
EOF

gpg --batch --gen-key "$GPG_BATCH_FILE"
rm -f "$GPG_BATCH_FILE"

# Export private key (armored)
PRIVATE_ASC="$OUT/private.asc"
gpg --batch --yes \
    --pinentry-mode loopback \
    --passphrase "$GPG_PASS" \
    --export-secret-keys --armor \
    release@ozero.app > "$PRIVATE_ASC"
chmod 600 "$PRIVATE_ASC"

# Export public key for reference
gpg --export --armor release@ozero.app > "$OUT/public.asc"

# Cleanup isolated keyring
rm -rf "$GNUPGHOME"

echo "GPG private key:  $PRIVATE_ASC"
echo "GPG passphrase:   $GPG_PASS_FILE"
