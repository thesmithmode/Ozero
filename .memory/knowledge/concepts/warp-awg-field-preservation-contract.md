---
title: WARP AWG field preservation contract
sources:
  - daily/2026-06-01.md
created: 2026-06-01
updated: 2026-06-01
---
# WARP AWG field preservation contract

## Summary
A WARP config that works in PORTAL WG may contain AWG extension fields and must not be treated as vanilla WireGuard. Ozero must preserve and pass AWG fields through import, storage, INI building, and native startup.

## Key Points
- The user-provided working PORTAL WG config included `Jc`, `Jmin`, `Jmax`, `H*`, `S*`, and `I1`, so it was not vanilla WireGuard.
- Investigation must follow `import/parse -> stored config -> built INI -> EngineWarp/start -> TUN routes/DNS`.
- Changing `amQuick`/`wgQuick` precedence is unsafe without runtime evidence because PORTAL decompiled code prefers `amQuick` when present.
- Ozero's default AWG preset remains an invariant because vanilla WireGuard may be blocked in the target environment.
- With `ipv6Enabled=false`, Ozero's `stripIpv6FromIni` can remove IPv6 `Address`, `DNS`, and `AllowedIPs` before native startup, creating another parity difference to test.

## Details
The daily log records a concrete WARP config that worked in PORTAL WG but not Ozero. It contained AWG-specific fields plus full-tunnel `AllowedIPs = 0.0.0.0/0, ::/0`, custom IPv4 and IPv6 DNS, and hostname endpoint `engage.cloudflareclient.com:4500`. That shifted the investigation away from generic WireGuard parsing and toward whether Ozero preserves every AWG extension field and sends the same effective INI to native code.

The log also corrected an early risky hypothesis about `wgQuick` versus `amQuick`. Decompiled PORTAL code preferred `amQuick` when non-blank and used `wgQuick` only as fallback. Therefore changing Ozero to prefer `wgQuick` would violate evidence and could remove intentional AWG obfuscation. The correct regression surface is preservation and effective native input, including how IPv6 stripping and endpoint resolution modify the raw imported config.

This concept complements [[concepts/warp-proxy-config-wgquick-precedence]] and [[concepts/warp-ipv6-fail-closed-blackhole-route]]. It also relates to [[concepts/intentional-tradeoff-sentinel-documentation]] because the default AWG preset is an intentional contract that should not be "fixed" away during parity work.

## Related Concepts
- [[concepts/warp-proxy-config-wgquick-precedence]]
- [[concepts/warp-ipv6-fail-closed-blackhole-route]]
- [[concepts/intentional-tradeoff-sentinel-documentation]]
- [[concepts/warp-config-import-naming-dedup]]

## Sources
- [[daily/2026-06-01]]: The user supplied a PORTAL-working WARP config with AWG fields, full-tunnel IPv4/IPv6 AllowedIPs, dual-stack DNS, and hostname endpoint.
- [[daily/2026-06-01]]: The log records that PORTAL decompiled code prefers `amQuick` when present, so Ozero should not switch priority to `wgQuick` without stronger evidence.
