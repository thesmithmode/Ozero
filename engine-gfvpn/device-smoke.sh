#!/usr/bin/env bash
# Explicit ARM64 device smoke: no SDK import; grant VPN consent in Ozero beforehand.
set -euo pipefail

quote_arg() {
  printf "'%s'" "${1//\'/\'\\\'\'}"
}

run_test() {
  local output status=0
  output=$(adb shell "$1" 2>&1) || status=$?
  printf '%s\n' "$output"
  (( status == 0 )) && tr -d '\r' <<< "$output" | grep -Fxq 'OK (1 test)'
}

device_arch=$(adb shell uname -m | tr -d '\r')
if [[ "$device_arch" != aarch64 && "$device_arch" != arm64 ]]; then
  printf 'Native ARM64 Android required; kernel architecture is %s. ABI translation is not ARM64 validation.\n' "$device_arch" >&2
  exit 1
fi

runner=ru.ozero.app.test/androidx.test.runner.AndroidJUnitRunner
url=${OZERO_GFVPN_SMOKE_URL:-https://www.gstatic.com/generate_204}
marker=${OZERO_GFVPN_SMOKE_MARKER-}
network_change=${OZERO_GFVPN_SMOKE_NETWORK_CHANGE:-0}
[[ "$network_change" == 0 || "$network_change" == 1 ]]
sdk_status=0
tun_status=0
run_test "am instrument -w -e gfvpnSmoke true -e class ru.ozero.app.gfvpn.GfVpnDeviceSmokeTest $runner" || sdk_status=$?
run_test "am instrument -w -e OZERO_GFVPN_SMOKE 1 -e OZERO_GFVPN_SMOKE_URL $(quote_arg "$url") -e OZERO_GFVPN_SMOKE_MARKER $(quote_arg "$marker") -e OZERO_GFVPN_SMOKE_NETWORK_CHANGE $network_change -e class 'ru.ozero.app.soak.SoakTest#runGfVpnTunSmoke' $runner" || tun_status=$?
printf 'SDK result=%s, external TUN result=%s\n' "$sdk_status" "$tun_status"
(( sdk_status == 0 && tun_status == 0 ))
