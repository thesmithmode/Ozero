set -euo pipefail

ensure_adb() {
  local attempt
  for attempt in 1 2 3; do
    if timeout 15s adb wait-for-device >/dev/null 2>&1 && \
       timeout 10s adb shell true >/dev/null 2>&1; then
      return 0
    fi
    adb kill-server >/dev/null 2>&1 || true
    sleep 2
  done
  return 1
}

capture_logcat() {
  local attempt
  for attempt in 1 2 3; do
    if ensure_adb && timeout 20s adb logcat -d > "$RUNNER_TEMP/soak-logcat.txt"; then
      return 0
    fi
    sleep 2
  done
  : > "$RUNNER_TEMP/soak-logcat.txt"
  return 1
}

pull_metrics() {
  local attempt
  rm -f soak-metrics.json
  for attempt in 1 2 3; do
    if ensure_adb && timeout 20s adb pull \
      /sdcard/Android/data/ru.ozero.app/files/soak-metrics.json \
      soak-metrics.json; then
      return 0
    fi
    rm -f soak-metrics.json
    sleep 2
  done
  return 1
}

ensure_adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set ru.ozero.app ACTIVATE_VPN allow
boot_id_before=$(adb shell cat /proc/sys/kernel/random/boot_id | tr -d '\r')
test -n "$boot_id_before"
adb logcat -c
instrument_status=0
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
  > "$RUNNER_TEMP/soak-output.txt" 2>&1 || instrument_status=$?

status=0
if grep -q 'OK (1 test)' "$RUNNER_TEMP/soak-output.txt"; then
  if (( instrument_status != 0 )); then
    echo "Instrumentation transport exited with status $instrument_status after JUnit success; validating device evidence." >&2
  fi
else
  echo "Instrumentation did not report JUnit success (exit=$instrument_status)." >&2
  status=1
fi

if ensure_adb; then
  boot_id_after=$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r' || true)
  if [[ -z "$boot_id_after" || "$boot_id_after" != "$boot_id_before" ]]; then
    echo 'Emulator rebooted or boot identity became unavailable during soak.' >&2
    status=1
  fi
else
  echo 'ADB did not recover after instrumentation.' >&2
  status=1
fi

if ! capture_logcat; then
  echo 'Failed to capture post-soak logcat after ADB recovery attempts.' >&2
  status=1
fi
if grep -E -q 'AndroidRuntime: Process: ru\.ozero\.app([,:]|\.test[, ])' "$RUNNER_TEMP/soak-logcat.txt" || \
   grep -E -q 'Fatal signal.*\(ru\.ozero\.app' "$RUNNER_TEMP/soak-logcat.txt" || \
   grep -E -q '(OzeroVpnService|SingboxEngine(Service)?|SingboxRuntime).*DeadObjectException' "$RUNNER_TEMP/soak-logcat.txt"; then
  status=1
fi
if [[ "$OZERO_SOAK_REQUIRE_REALITY" == '0' ]]; then
  grep -E -q 'SingboxEngine.*autoCount=3' "$RUNNER_TEMP/soak-logcat.txt" || status=1
fi
if ! pull_metrics; then
  echo 'Failed to pull soak metrics after ADB recovery attempts.' >&2
  status=1
fi
test -s soak-metrics.json || status=1
if [[ -s soak-metrics.json ]]; then
  SOAK_EXPECTED_CYCLES="$SOAK_CYCLES" SOAK_EXPECTED_PROTOCOLS="$OZERO_SOAK_EXPECTED_METRICS_PROTOCOLS" python3 -c 'import json, os; data=json.load(open("soak-metrics.json", encoding="utf-8")); expected=int(os.environ["SOAK_EXPECTED_CYCLES"]); protocols=os.environ["SOAK_EXPECTED_PROTOCOLS"].split(","); actual=data["successful_cycles"]; assert data["cycles_per_protocol"] == expected; assert set(actual) == set(protocols); assert all(actual[name] == expected for name in protocols)' || status=1
fi
for protocol in $(printf '%s' "$OZERO_SOAK_EXPECTED_SERVER_INBOUNDS" | tr ',' ' '); do
  hits=$(grep -Ec "\\[${protocol}-in\\].*inbound connection" "$RUNNER_TEMP/singbox-server.log" || true)
  (( hits >= SOAK_CYCLES )) || status=1
done
exit "$status"
