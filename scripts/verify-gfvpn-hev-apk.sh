#!/usr/bin/env bash
# Verify the isolated open-source HEV library is packaged for the intended ABI.
set -euo pipefail
apk="${1:?usage: verify-gfvpn-hev-apk.sh APK}"
test -f "$apk"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
unzip -p "$apk" lib/arm64-v8a/libhev-gfvpn-socks5-tunnel.so > "$tmp/hev.so"
test -s "$tmp/hev.so"
readelf -h "$tmp/hev.so" > "$tmp/header"
grep -E 'Machine:.*AArch64' "$tmp/header"
grep -E 'Type:.*DYN' "$tmp/header"
strings "$tmp/hev.so" > "$tmp/strings"
for symbol in hev/htproxy/TProxyService TProxyStartService TProxyStopService TProxyIsRunning TProxyGetStats mapdns; do
    grep -Fx "$symbol" "$tmp/strings" >/dev/null
done
echo "gfVPN HEV JNI contract present"
