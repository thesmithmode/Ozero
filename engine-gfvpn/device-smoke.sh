#!/usr/bin/env bash
# Exercise the SDK already included in the installed Ozero APK.
set -euo pipefail
adb shell am instrument -w -e gfvpnSmoke true -e class ru.ozero.app.gfvpn.GfVpnDeviceSmokeTest ru.ozero.app.test/androidx.test.runner.AndroidJUnitRunner
