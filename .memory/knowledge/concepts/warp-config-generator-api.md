---
title: "WARP Config Generator API Contract"
aliases: [warp-mirror-api, warp-gen1, nellimonix-warp-generator]
tags: [warp, api, integration, networking]
sources:
  - "daily/2026-05-02.md"
  - "daily/2026-05-07.md"
  - "daily/2026-05-22.md"
created: 2026-05-02
updated: 2026-06-12
---

# WARP Config Generator API Contract

Ozero's WARP engine obtains WireGuard configurations from Cloudflare WARP mirrors via a JSON API. The original implementation failed because it parsed the response incorrectly — searching for `[Interface]` in raw JSON where the actual WireGuard config was Base64-encoded inside a nested `content.configBase64` field. Additionally, the per-mirror timeout of 20 seconds was insufficient for Cloudflare's API which performs two synchronous round-trips.

## Key Points

- API response format: `{"success":true,"content":{"configBase64":"<base64 WireGuard conf>"}}` — not raw WireGuard INI
- Request body must include an `endpoint` field — omitting it produces incomplete config
- Per-mirror timeout increased from 20s to 45s because Cloudflare API does 2 sync round-trips (registration + config generation)
- The 78/80 hardcoded mirrors were actually live — "all Read timed out" was a secondary symptom of wrong timeout + wrong parsing
- Reference implementation: `nellimonix/warp-config-generator-vercel` (discovered via live mirror `warp-gen1.vercel.app`)

## Details

### Root Cause: Wrong Response Parsing

The initial WARP implementation in `WarpEngine` expected the mirror API to return a raw WireGuard configuration in INI format (sections like `[Interface]`, `[Peer]`). It scanned the HTTP response body for these section headers. The actual API returns a JSON object with the WireGuard configuration Base64-encoded inside `content.configBase64`. Since the JSON body never contained `[Interface]` as a literal string, every mirror was classified as failed regardless of whether it responded successfully.

This parsing error was compounded by an insufficient timeout. Cloudflare's WARP registration API performs two synchronous operations: (1) register a new WARP identity and (2) generate the WireGuard configuration for that identity. Each operation takes 5-15 seconds depending on load, putting the total response time at 10-30 seconds. With a 20-second timeout, roughly half of successful API calls were timing out before the second round-trip completed.

### Correct API Contract

The `nellimonix/warp-config-generator-vercel` repository defines the API contract. The generator is deployed as a Vercel/Netlify serverless function across 78+ mirrors (Cloudflare edge locations). The request-response cycle:

1. POST to mirror URL with JSON body including `endpoint` field
2. Mirror calls Cloudflare WARP API (identity registration + config generation)
3. Response: `{"success": true, "content": {"configBase64": "<base64-encoded WG conf>"}}`
4. Client decodes Base64 → standard WireGuard config with `[Interface]` and `[Peer]` sections

The `endpoint` field in the request body specifies which Cloudflare endpoint to use. Without it, the generated config may lack the endpoint address, making it unusable for WireGuard tunnel establishment.

### Misidentification of PortalConnect

During debugging, the user provided a decompiled `PortalConnect-1.1.6` APK (CYBERPORTAL_X/KIBERPORTAL) as potential WARP reference. Analysis revealed PortalConnect is not a WARP client — it implements VLESS+SOCKS5 proxy protocol, unrelated to Cloudflare WARP. The actual API reference was found in `nellimonix/warp-config-generator-vercel` GitHub repository.

### Mirror Endpoint Returns IP, Not Hostname (2026-05-22)

`validateCloudflarePeer()` originally validated that the mirror response's peer endpoint matched `engage.cloudflareclient.com`. In practice, the `REQUEST_BODY` path (Cloudflare's direct API) returns an IP address, not the canonical hostname. Two IP ranges are used:

- `162.159.192.0/22` – `162.159.192.x` through `162.159.195.x`
- `188.114.96.0/22` – `188.114.96.x` through `188.114.99.x`

`validateCloudflarePeer` was expanded to accept these ranges via regex or range check alongside the hostname. Without this fix, configs generated via the direct Cloudflare API path were rejected as invalid by the validator, causing WARP to fall back unnecessarily to the mirror pool.

### Suspicious Mirror DNS Servers (2026-05-07)

A real working WARP config obtained via the mirror auto-config API from a Russian ISP environment contained DNS servers `176.99.11.77` and `80.78.247.254`. These IPs do not belong to Cloudflare (which uses `1.1.1.1`, `1.0.0.1`, and the DoH endpoint `cloudflare-dns.com`). The origin of these DNS servers is unknown — they may be injected by the mirror generator, set by the TSPU environment, or belong to a third-party resolver. Using unknown DNS servers in a privacy-sensitive VPN application is a security concern.

This was identified during session 15:11 as a follow-up item: verify which mirrors inject non-Cloudflare DNS and determine whether `WarpIniBuilder` should override the DNS field to a known-safe resolver.

### Mirror Hardcoding Concern

The 78 mirrors are hardcoded as a Kotlin list in `WarpEngine.kt`. This was flagged in both code review (High: move to config file) and security review (P3: no certificate pinning, MITM risk with TSPU trust injection). The mirrors cannot be updated without an APK release. These findings are tracked in AUDIT.md as SEC-P1-02 (key warning UI) and P3 (certificate pinning).

## Related Concepts

- [[concepts/vpnservice-builder-traps]] - WARP uses VpnService.Builder for tunnel setup, sharing the same configuration surface
- [[concepts/nubia-rom-permission-enforcement]] - Nubia ROM was initially suspected of blocking serverless endpoints (ruled out — mirrors were actually accessible)
- [[concepts/urnetwork-sdk-integration]] - Another engine integration with similar API discovery challenges

## Sources

- [[daily/2026-05-02.md]] - WARP root cause identified: wrong JSON parsing + insufficient timeout; nellimonix/warp-config-generator-vercel as reference; PortalConnect ≠ WARP
- [[daily/2026-05-07.md]] - Session 15:11: mirror auto-config returned DNS servers 176.99.11.77 + 80.78.247.254 (not Cloudflare); origin unknown — may be mirror-injected or ISP-specific; identified as security follow-up
- [[daily/2026-05-22.md]] - Session 17:51: validateCloudflarePeer expanded to IP ranges 162.159.192-195.* and 188.114.96-99.* — REQUEST_BODY path returns IP not hostname; validator was rejecting valid Cloudflare configs
