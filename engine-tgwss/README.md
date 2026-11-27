# Telegram WSS + WARP

A regular Ozero VPN engine: select Telegram WSS and connect using TUN mode.
No Telegram configuration is necessary. Ozero's existing split-tunnel rules,
DNS, IPv6 option, VPN lifecycle and killswitch apply. Proxy mode also exists.

Known Telegram DC TCP connections use TLS WebSocket to Telegram. Other TCP,
UDP (including DNS and calls), and unsupported Telegram transports go through
the existing WARP SOCKS runtime, not directly to the destination. Fallback
from WSS is allowed only before the encrypted MTProto header is queued.
The WARP configuration is the currently selected WARP slot. A failed WARP
startup or routed TCP/UDP probe prevents a connected state. WSS upgrade is
also required at initial readiness. Once connected, a WSS outage does not report
engine failure while the WARP fallback remains healthy. Endpoint candidates
are raced across the available WebSocket transports; losing sockets are closed. Standalone manual selection avoids
concurrent use of the shared WARP runtime in chains and redundant auto picks.

## Release acceptance

- Required PR CI: style, compilation, tests and unchanged coverage gates.
- Android ARM64: verify native WARP SOCKS UDP association with actual DNS
  datagrams, IPv4/IPv6 (when enabled), ordinary HTTPS, Telegram messages,
  files and calls, split tunnel allowlist/blocklist, stop and network changes.
- Verify WARP failure blocks the route rather than leaking traffic; verify
  the WSS fallback never replays a committed MTProto session.
- Verify backup round trip, runtime slot changes and switching to/from WARP.
- A fake SOCKS server or a WSS upgrade alone cannot prove compatibility with
  the pinned native binary or end-to-end Telegram delivery.

## ARM64 device smoke test

With a working WARP slot and VPN consent on a dedicated test device, run the
existing instrumentation target `SoakTest#runTelegramWssTunSmoke` with
`OZERO_TGWSS_SMOKE=1`, `OZERO_TGWSS_SMOKE_URL` (a reachable HTTPS URL), and
`OZERO_TGWSS_SMOKE_MARKER` (its exact response body after trimming). The test
starts Telegram WSS, WARP, then Telegram WSS again in TUN mode and checks VPN
transport and routed HTTPS from the separate test APK UID. Telegram WSS startup
also requires its internal routed TCP/UDP and WSS readiness probes. The test
stops the VPN and restores the original settings; it is opt-in and does not
replace the Telegram delivery, split-tunnel or network-transition checks above.
