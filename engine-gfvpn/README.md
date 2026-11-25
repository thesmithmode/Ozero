# gfVPN integration experiment

This module is intentionally not connected to the app or release packaging.
It contains only original bridge code, not the proprietary SDK, configurations
or credentials. The locally supplied APK is pinned to the exact analyzed version.

`python3 engine-gfvpn/prepare-sdk.py /path/to/local.apk /tmp/gfvpn-sdk`

For an explicitly authorized development build, copy the two read-only files
into the app's private files/gfvpn-sdk directory. Do not add them to Git or
release assets. The bridge independently validates the hashes before loading.
It loads the matching gomobile classes using a separate classloader in the
private :engine_gfvpn process.

Remaining gates before a selectable engine:
- Test on ARM64 Android; validate JNI classloader compatibility and native
  startup/stop/restart, including bounded shutdown and process death.
- Implement authenticated SOCKS5 credentials handoff and routed readiness.
- Connect Ozero's TUN/HEV, DNS and split routing, fail-closed lifecycle, network
  change callbacks and engine UI. No other engine may load this runtime.
- Verify live configuration updates and transport behavior under real network
  conditions, and establish authorization to distribute the SDK if desired.

No production support or runtime success is claimed.
