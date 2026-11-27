#!/usr/bin/env bash
# Explicit ARM64 device smoke: no SDK import; grant VPN consent in Ozero beforehand.
set -euo pipefail

quote_arg() {
  printf "'%s'" "${1//\'/\'\\\'\'}"
}

run_test() {
  local output
  output=$(adb shell "$1")
  printf '%s\n' "$output"
  tr -d '\r' <<< "$output" | grep -Fxq 'OK (1 test)'
}

runner=ru.ozero.app.test/androidx.test.runner.AndroidJUnitRunner
url=${OZERO_GFVPN_SMOKE_URL:-https://www.gstatic.com/generate_204}
marker=${OZERO_GFVPN_SMOKE_MARKER-}
network_change=${OZERO_GFVPN_SMOKE_NETWORK_CHANGE:-0}
[[ "$network_change" == 0 || "$network_change" == 1 ]]
run_test "am instrument -w -e gfvpnSmoke true -e class ru.ozero.app.gfvpn.GfVpnDeviceSmokeTest $runner"
run_test "am instrument -w -e OZERO_GFVPN_SMOKE 1 -e OZERO_GFVPN_SMOKE_URL $(quote_arg "$url") -e OZERO_GFVPN_SMOKE_MARKER $(quote_arg "$marker") -e OZERO_GFVPN_SMOKE_NETWORK_CHANGE $network_change -e class 'ru.ozero.app.soak.SoakTest#runGfVpnTunSmoke' $runner"
