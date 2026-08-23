set -euo pipefail

adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set ru.ozero.app ACTIVATE_VPN allow
adb logcat -c
status=0
adb shell am instrument \
  -w \
  -e OZERO_SOAK 1 \
  -e OZERO_SOAK_CYCLES "$SOAK_CYCLES" \
  -e OZERO_SOAK_REQUIRE_REALITY "$OZERO_SOAK_REQUIRE_REALITY" \
  -e OZERO_SOAK_REALITY_ONLY "$OZERO_SOAK_REQUIRE_REALITY" \
  -e OZERO_SOAK_VLESS_REALITY "'$OZERO_SOAK_VLESS_REALITY'" \
  -e OZERO_SOAK_REALITY_TARGET "$OZERO_SOAK_REALITY_TARGET" \
  -e OZERO_SOAK_REALITY_MARKER "$OZERO_SOAK_REALITY_MARKER" \
  -e class ru.ozero.app.soak.SoakTest \
  ru.ozero.app.test/androidx.test.runner.AndroidJUnitRunner \
  > "$RUNNER_TEMP/soak-output.txt" 2>&1 || status=$?
adb logcat -d > "$RUNNER_TEMP/soak-logcat.txt"
if grep -E -q 'FATAL EXCEPTION|Fatal signal|DeadObjectException' "$RUNNER_TEMP/soak-logcat.txt"; then
  status=1
fi
if [[ "$OZERO_SOAK_REQUIRE_REALITY" == '0' ]]; then
  grep -E -q 'SingboxEngine.*autoCount=3' "$RUNNER_TEMP/soak-logcat.txt" || status=1
fi
adb pull /sdcard/Android/data/ru.ozero.app/files/soak-metrics.json soak-metrics.json || status=1
test -s soak-metrics.json || status=1
grep -q 'OK (1 test)' "$RUNNER_TEMP/soak-output.txt" || status=1
SOAK_EXPECTED_CYCLES="$SOAK_CYCLES" SOAK_EXPECTED_PROTOCOLS="$OZERO_SOAK_EXPECTED_METRICS_PROTOCOLS" python3 -c 'import json, os; data=json.load(open("soak-metrics.json", encoding="utf-8")); expected=int(os.environ["SOAK_EXPECTED_CYCLES"]); protocols=os.environ["SOAK_EXPECTED_PROTOCOLS"].split(","); actual=data["successful_cycles"]; assert data["cycles_per_protocol"] == expected; assert set(actual) == set(protocols); assert all(actual[name] == expected for name in protocols)' || status=1
for protocol in $(printf '%s' "$OZERO_SOAK_EXPECTED_SERVER_INBOUNDS" | tr ',' ' '); do
  hits=$(grep -Ec "\\[${protocol}-in\\].*inbound connection" "$RUNNER_TEMP/singbox-server.log" || true)
  (( hits >= SOAK_CYCLES )) || status=1
done
exit "$status"
