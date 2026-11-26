#!/usr/bin/env bash
# Installs only the caller-supplied exact SDK in a debuggable, already installed Ozero.
set -euo pipefail
apk="${1:?usage: device-smoke.sh /path/to/local.apk}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
python3 "$root/engine-gfvpn/prepare-sdk.py" "$apk" "$tmp/sdk"
adb shell run-as ru.ozero.app id >/dev/null
adb shell am force-stop ru.ozero.app
adb shell "run-as ru.ozero.app sh -c 'test ! -e files/gfvpn-sdk && mkdir -p files/gfvpn-sdk-stage'" 
for f in classes.dex libgojni.so; do
  adb shell -T "run-as ru.ozero.app sh -c 'umask 077; cat > files/gfvpn-sdk-stage/$f && chmod 400 files/gfvpn-sdk-stage/$f'" < "$tmp/sdk/$f"
done
adb shell "run-as ru.ozero.app sh -c 'mv files/gfvpn-sdk-stage files/gfvpn-sdk'"
adb shell am instrument -w -e gfvpnSmoke true -e class ru.ozero.app.gfvpn.GfVpnDeviceSmokeTest ru.ozero.app.test/androidx.test.runner.AndroidJUnitRunner
