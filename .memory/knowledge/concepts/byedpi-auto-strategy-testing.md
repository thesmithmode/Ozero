---
title: "ByeDPI Auto-Strategy Testing Architecture"
aliases: [auto-strategy-picker, strategy-test-mode, byedpi-test-mode]
tags: [byedpi, architecture, testing, future-feature]
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# ByeDPI Auto-Strategy Testing Architecture

ByeByeDPI includes a test mode (TestActivity) that iterates over a list of DPI bypass strategies, probes target sites through each one via SOCKS5, and ranks strategies by success rate. This architecture is documented as a reference for porting an equivalent feature to Ozero.

## Key Points

- Test mode runs SOCKS5-only (no VPN/TUN) — `ServiceManager.start(Mode.Proxy)` with byedpi listening on 127.0.0.1:1080
- 75 preset strategies stored in `assets/proxytest_strategies.list` as TAB-separated `<index>\t<args>` with `{sni}` placeholder
- HTTP probe uses `URL.openConnection(Proxy(SOCKS, 127.0.0.1:1080))` with success criteria: `actualLength >= declaredLength`
- Strategy cycle: write args to SharedPrefs → start service → wait for Running (3s timeout) → probe sites in parallel → stop → next
- UI shows RecyclerView sorted by success percentage with Apply button to persist winning args

## Details

### Test Cycle Architecture

The test mode operates as a sequential loop over the strategy list. For each strategy:

1. `updateCmdArgs(strategy.command)` writes the strategy's ByeDPI arguments to SharedPreferences
2. `ServiceManager.start(Mode.Proxy)` launches byedpi in SOCKS5-only mode (no TUN, no VPN permission needed)
3. `waitForProxyStatus(Running, 3s)` polls until the SOCKS5 proxy is accepting connections
4. `siteChecker.checkSitesAsync` runs parallel HTTP requests through the SOCKS5 proxy to test sites
5. `ServiceManager.stop` shuts down the proxy
6. Results (success/failure per site) are recorded for this strategy

The SOCKS5-only approach is critical: it avoids VPN permission dialogs, TUN setup overhead, and Android's VPN always-on restrictions. The test can run entirely in userspace without system privileges.

### HTTP Probe Design

The probe connects to target URLs through the local SOCKS5 proxy and checks whether the full response was received. The success criterion `actualLength >= declaredLength` detects DPI block patterns where the ISP terminates the connection mid-stream, resulting in a truncated response body. This is more reliable than checking HTTP status codes, since TSPU-style blocking often returns a valid HTTP 200 with a shortened body or RST after partial transfer.

### Site Lists

Test sites are bundled as assets: `proxytest_general.sites`, `proxytest_youtube.sites`, etc. Each contains URLs of known blocked resources. The `{sni}` placeholder in strategy args is replaced with a user-configurable SNI value (default `google.com`), allowing strategy testing against different target domains.

### Ozero Port Requirements

Porting this feature to Ozero requires:

- A SOCKS5-only service mode (mode flag in OzeroVpnService or a separate lightweight service)
- HTTP probe utility with SOCKS5 proxy support
- Strategy list management (bundled presets + user-defined)
- Persistent test results (JSON storage for strategy rankings)
- UI for strategy list display, sorting, and one-tap application

## Related Concepts

- [[concepts/byedpi-args-parsing]] - Strategy args use the same flag syntax; argv[0] and -K traps apply to test strategies too
- [[concepts/v001-dpi-bypass-fix-chain]] - The working preset (fix #5) could serve as a baseline strategy in the test list
- [[concepts/vpn-engine-pipeline]] - Auto-strategy results feed into the engine pipeline's strategy selection

## Sources

- [[daily/2026-04-30.md]] - ByeByeDPI TestActivity architecture documented from source code analysis; Ozero port requirements identified
