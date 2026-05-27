#!/usr/bin/env bash
set -euo pipefail
umask 077

OUT="${1:?Usage: keystore-gen.sh <out-dir>}"
mkdir -p "$OUT"
chmod 700 "$OUT"

ALIAS="ozero"
DNAME="CN=Ozero, OU=Release, O=Ozero, L=Internet, ST=Internet, C=RU"
VALIDITY=10000
KEYSIZE=4096

# Generate random 24-char base64 password
# PKCS12 uses a single password for both store and key
KS_PASS="$(openssl rand -base64 18 | tr -d '\n')"
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
