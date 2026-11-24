# Opera Proxy

Source: [Alexey71/opera-proxy v1.30.0](https://github.com/Alexey71/opera-proxy/tree/57205a4bf61b55991c35841198cc27ead5ef6962), MIT (see LICENSE.upstream).

Android ARM64 release asset is pinned by SHA-256 in build-tools/binaries.lock.yaml. An alternative source build recipe is provided in scripts/build-opera-proxy.sh.

The engine runs an isolated subprocess with loopback TCP SOCKS5, EU/AS/AM and first/random/fastest server selection. Startup must pass a routed TLS handshake. No public proxy fallback, custom credentials, or disabled certificate verification.

Select proxy mode in connection settings and then Opera Proxy, or include Opera in proxy-mode automatic selection. Configure the region and selection policy in the engine settings. Settings are included in the general backup category.

The upstream SOCKS implementation supports CONNECT only. Opera is excluded from device-wide TUN selection: ordinary UDP/DNS and QUIC cannot be transported by this implementation. Applications using SOCKS must resolve names remotely. The provider is third-party, may restrict usage and its availability is not guaranteed.

Before merging: verify CI/coverage, actual ARM64 APK packaging and on-device start/stop and connectivity; confirm provider service terms permit intended usage. No third-party APK binaries are copied.
