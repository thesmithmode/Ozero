# Opera Proxy

Source: [Alexey71/opera-proxy v1.30.0](https://github.com/Alexey71/opera-proxy/tree/57205a4bf61b55991c35841198cc27ead5ef6962), MIT (see LICENSE.upstream).

Android ARM64 release asset is pinned by SHA-256 in build-tools/binaries.lock.yaml. A source build recipe is provided in scripts/build-opera-proxy.sh.

The engine runs an isolated subprocess with loopback TCP SOCKS5, EU/AS/AM and first/random/fastest server selection. Both proxy and TUN modes participate in automatic engine selection. The TUN adapter reuses libbox in its own Android process, separate from the normal sing-box engine. DNS is intercepted and sent using Cloudflare DoH through the Opera SOCKS endpoint; other UDP is rejected. No direct outbound or insecure TLS is configured. The general custom DNS setting is not applied by Opera. DNS resolution by the upstream Opera server and bootstrap API traffic remain part of its transport behavior.

Opera settings are included in general backup. TCP-only is an upstream limitation, so UDP-dependent apps may not work; QUIC-capable apps must fall back to TCP. The provider is third-party and availability is not guaranteed.

Before merging: verify full CI/coverage, actual ARM64 APK packaging, device startup/stop, both traffic modes, DNS/IPv6 and absence of traffic fallback on failure. Confirm provider service terms permit intended usage.

## ARM64 device smoke test

Install the CI-built debug APK and its instrumentation APK on an ARM64 Android device with VPN permission already granted. Run `ru.ozero.app.soak.SoakTest#runOperaTunSmoke` with instrumentation arguments `OZERO_OPERA_SMOKE=1`, `OZERO_OPERA_SMOKE_URL` (controlled HTTPS hostname), and `OZERO_OPERA_SMOKE_MARKER` (expected response body). This repeats connection and disconnection three times and makes a DNS + HTTPS request from the instrumentation APK's separate UID while checking Android VPN transport. It is opt-in because it uses a real third-party service. A passing test does not establish UDP support, IPv6 reachability, or kill-switch behavior during a remote crash; verify those separately.
