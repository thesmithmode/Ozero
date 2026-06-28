#!/usr/bin/env bash
set -euo pipefail
umask 077

OUT="${1:?Usage: keystore-gen.sh <out-dir>}"
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

ALIAS="ozero"
DNAME="CN=Ozero, OU=Release, O=Ozero, L=Internet, ST=Internet, C=RU"
VALIDITY=10000
KEYSIZE=4096

# Generate random 32-char alphanumeric password (ASCII-safe for keytool PKCS12)
KS_PASS="$(openssl rand -hex 16)"
KEY_PASS="$KS_PASS"

KEYSTORE="$OUT/release.keystore"

# Передаём пароли через env, не через -storepass/-keypass argv (видны в `ps aux`).
export KS_PASS_ENV="$KS_PASS"
export KEY_PASS_ENV="$KEY_PASS"
keytool -genkey \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize "$KEYSIZE" \
  -validity "$VALIDITY" \
  -keystore "$KEYSTORE" \
  -storepass:env KS_PASS_ENV \
  -keypass:env KEY_PASS_ENV \
  -dname "$DNAME" \
  -storetype PKCS12 \
  -noprompt
unset KS_PASS_ENV KEY_PASS_ENV

chmod 600 "$KEYSTORE"

PASS_FILE="$OUT/.passwords"
cat > "$PASS_FILE" <<EOF
KS_PASS=$KS_PASS
KEY_PASS=$KEY_PASS
KEY_ALIAS=$ALIAS
EOF
chmod 600 "$PASS_FILE"

echo "Keystore generated: $KEYSTORE"
echo "Passwords saved:    $PASS_FILE"
