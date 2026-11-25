# gfVPN integration experiment

The bridge is registered as a standalone selectable VPN engine. No third-party SDK is bundled; the user must explicitly import the exact authorized local APK in engine settings. Proxy-only, automatic chain selection and IPv6 are disabled.
It contains only original bridge code, not the proprietary SDK, configurations
or credentials. The locally supplied APK is pinned to the exact analyzed version.

`python3 engine-gfvpn/prepare-sdk.py /path/to/local.apk /tmp/gfvpn-sdk`

For an explicitly authorized development build, copy the two read-only files
into the app's private files/gfvpn-sdk directory. Do not add them to Git or
release assets. The bridge independently validates the hashes before loading.
It loads the matching gomobile classes using a separate classloader in the
private :engine_gfvpn process. GfVpnRuntime provides the asynchronous host-side
session API, request deadlines, owner-death cancellation and hard process reset.

To run the real SDK smoke test on an attached ARM64 device, install the Ozero debug APK and its androidTest APK, then run `bash engine-gfvpn/device-smoke.sh /path/to/local.apk`. The test is opt-in; normal CI does not certify the closed native SDK. The script refuses to overwrite an existing SDK directory.

Remaining gates before a selectable engine:
- Test on ARM64 Android; validate JNI classloader compatibility and native
  startup/stop/restart, including bounded shutdown and process death.
- The private IPC now passes SOCKS credentials only to the same app UID and implements a routed HTTPS probe. Validate both with the actual SDK, including native timeout recovery.
- The isolated service now accepts a duplicated TUN descriptor and starts Ozero's existing HEV with authenticated SOCKS5 settings. Device-test UDP-over-TCP, DNS and IPv6; verify the now-wired Ozero VPN lifecycle, split routing and engine UI on-device. No other engine may load this runtime.
- Verify live configuration updates and transport behavior under real network
  conditions, and establish authorization to distribute the SDK if desired.

No production support or runtime success is claimed.

Full VPN device acceptance: run `ru.ozero.app.soak.SoakTest#runGfVpnTunSmoke` with `OZERO_GFVPN_SMOKE=1`, `OZERO_GFVPN_SMOKE_URL` and `OZERO_GFVPN_SMOKE_MARKER` (a controlled HTTPS test page and expected content). This verifies three starts/stops and external test-UID HTTPS routing, not merely SOCKS readiness. The user must grant Android VPN consent first. A separate process is not a security sandbox: the imported closed-source SDK runs with the application UID. The bridge remains experimental until ARM64 tests and provider authorization are verified.
