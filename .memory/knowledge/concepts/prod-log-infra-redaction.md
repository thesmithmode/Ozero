---
title: "Production Log Infrastructure Redaction"
aliases: [prod-log-security, log-redaction, boot-log-privacy]
tags: [security, logging, privacy, warp, urnetwork]
sources:
  - "daily/2026-05-20.md"
created: 2026-05-20
updated: 2026-05-20
---

# Production Log Infrastructure Redaction

The Ozero boot.log is accessible to users on-device (Settings → Boot log). Any string that reveals infrastructure topology, financial addresses, or development methodology must be redacted before it reaches a log statement. Three categories of leakage were identified and fixed: WARP mirror URLs, Solana wallet addresses, and dev-language instructions embedded in log messages.

## Key Points

- WARP mirror URLs must be hashed to `mXXXXXXXX` tag — full domain reveals infra for competitor enumeration/DDoS/reporting
- Solana wallet address must never appear even as prefix — `take(6)` is sufficient for blockchain search to reveal the owner
- Dev-language instructions (e.g. "нужен tombstone+sentinel", "собрать tombstone") must be stripped from prod log entries — reveals decompiling methodology to APK analysts
- Rule: `PersistentLoggers` and `Log.*` calls must contain no raw URLs, addresses, or methodology strings
- Safe format for WARP mirror: log the hash tag only; safe format for wallet: log "payout wallet set" with no address

## Details

### WARP Mirror URL Leakage

`WarpConfigFetcher` (or equivalent) logged the full mirror domain when fetching WARP config, e.g. `cyberportal-x.vercel.app` or `kiber.cyberportal.workers.dev`. These domains are proprietary infrastructure. A competitor reading boot.log from a user's device could enumerate all mirrors, trigger DDoS, or report the domains to Cloudflare/Vercel for takedown. Fix: replace the URL string in log output with a deterministic hash tag `mXXXXXXXX` derived from the domain. The hash is stable across runs for correlation but non-reversible.

### Solana Wallet Address Leakage

A log entry for "payout wallet set" included the wallet address or its prefix via `address.take(6)`. Six characters of a Solana Base58 address is sufficient to perform a prefix search on Solana explorers and identify the wallet owner. Fix: log only the event "payout wallet set" without any address component.

### Dev-Language Instruction Leakage

`RealUrnetworkSdkBridge.kt` and `UrnetworkRuntime.kt` contained log messages phrased as developer instructions: "нужен tombstone+sentinel", "URnetwork-app собрать tombstone". These strings were written during debugging but left in production code. An APK analyst reading boot.log sees the decompilation/investigation methodology. Fix: strip all instruction-language strings from production log calls; replace with neutral status messages if logging is needed.

## Related Concepts

- [[concepts/persistent-logger-accumulation-trap]] - PersistentLoggers overuse causes boot.log bloat; reserve for critical one-shot events
- [[concepts/warp-config-generator-api]] - WARP mirror API architecture; the URLs being protected
- [[concepts/urnetwork-walletauth-per-device-registration]] - Solana keypair and wallet address handling

## Sources

- [[daily/2026-05-20.md]] - Session 15:59: three leak categories found in boot.log audit; mirror URL hashing fix; wallet address redaction fix; dev-instruction stripping in RealUrnetworkSdkBridge.kt + UrnetworkRuntime.kt
