# Opera Proxy (draft)

Source: [Alexey71/opera-proxy v1.30.0](https://github.com/Alexey71/opera-proxy/tree/57205a4bf61b55991c35841198cc27ead5ef6962), MIT (see LICENSE.upstream).

Isolated Android library for subprocess lifecycle, loopback SOCKS5, EU/AS/AM selection and a routed TLS readiness check. No public proxy fallback, custom credentials, or TLS verification bypass. The provider is third-party and availability is not guaranteed. SOCKS CONNECT is TCP-only; UDP and DNS routing must be handled or rejected explicitly by integration.

Build the pinned native source on Linux with Android NDK and the upstream Go toolchain: `bash scripts/build-opera-proxy.sh`. No APK artifacts from other apps are reused.

This module is not yet registered as an EnginePlugin, not included in the app dependency graph, and not selectable by users. Before enabling it, complete EngineId/config, Hilt, settings and backup, auto-mode, HEV DNS/TCP-only routing, release native packaging and license/third-party notices, CI coverage and device smoke testing. Confirm that the upstream service permits the intended usage. Do not merge as a completed feature before these checks.
