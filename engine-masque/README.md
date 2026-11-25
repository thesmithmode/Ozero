# MASQUE

Cloudflare WARP CONNECT-IP over HTTP/3 (QUIC) or HTTP/2 (TCP), implemented with [usque](https://github.com/Diniboy1123/usque), MIT, pinned to `6aa03fc97d12848dce34eedbd187fb1077b5d1ea`. Go 1.26.3, Android NDK r27c (API 24) and the upstream go.sum are used by the source-build action. Both ABIs use Android's libc DNS resolver for registration; CI checks its symbol and ELF load-segment alignment for 16 KiB pages. No APK-derived binary or shared account is used.

- Open Settings → MASQUE, review the Cloudflare terms, register a separate WARP profile, then select H3, H2 or Auto (H3 followed by H2, each with assigned IPv4 and available IPv6 endpoints).
- Choose MASQUE on the main screen for VPN or proxy mode. Registered profiles can participate in Auto; disable this in MASQUE settings if desired. AWG/WARP is unchanged.
- Android Keystore protects local profile credentials. The normal one-click portable backup includes a separate MASQUE category and follows the existing backup format; treat exported backups as sensitive. Import re-encrypts the profile on the destination.
- The isolated subprocess exposes loopback SOCKS5 only. Upstream pinning and tunnel DNS remain enabled; missing endpoint certificates are rejected. Both TCP and UDP must bind before the readiness marker, and a TLS payload through the SOCKS endpoint confirms connectivity.
- Child lifetime follows the parent stdin pipe; runtime profile is removed after loading, stale private workspaces are cleaned before reuse, and cancellation terminates the process. Process logs are discarded to avoid exposing credentials.
- The routed watchdog uses payload checks, and the shared TUN/UID statistics and shutdown path are reused. IPv6 is disabled in the transport when disabled in Ozero.
- Build in CI using the pinned source-build action; debug and release verify the exact packaged ELF and MIT notice. ARM64 is the production ABI; x86_64 is available for the existing soak workflow, which checks the packaged ELF and runs the native CLI under the application UID without registering an account.

## Release validation

CI must pass the module and application tests and coverage gates. On a real ARM64 device verify registration (with informed terms acceptance), both H2/H3 and auto fallback, IPv4/IPv6 TCP and UDP through HEV, split routing, proxy mode, kill-switch, Wi-Fi/mobile transitions, repeated stop/start and process death, and cross-device backup restore. Do not claim device validation from compilation or unit tests alone.
