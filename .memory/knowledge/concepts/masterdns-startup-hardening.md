---
title: MasterDNS startup hardening review fixes
sources:
  - daily/2026-05-21.md
created: 2026-05-28
updated: 2026-05-28
---
# MasterDNS startup hardening review fixes

## Key Points
- `MasterDnsEngine.start` needs a bounded startup wait; `state.first { ... }` without timeout can hang.
- `upstream` is intentionally ignored when `supportsUpstreamSocks=false`; mark the override with `@Suppress("UNUSED_PARAMETER")`.
- Resolver snapshots should be eager state, not `runBlocking + withTimeoutOrNull` inside Hilt providers.
- Subprocess stdout readers must cap disk logging and route overflow through the persistent logger contract.

## Details

The MasterDNS review found startup and DI hazards after the module was merged as a subprocess-pattern `EnginePlugin`. The startup path was hardened with `withTimeoutOrNull(startTimeoutMs=10s)` and `service.stop()` on timeout. This keeps a stuck subprocess from leaving the engine startup coroutine suspended indefinitely.

The provider path was also changed from blocking DI-time waits to a `MasterDnsResolversCache` backed by `stateIn(SharingStarted.Eagerly)`. Config generation gained case-insensitive filtering for reserved TOML keys, preflight parsing learned `host:port` and `[ipv6]:port`, and stdout capture gained a `MAX_LOG_LINES=200` cap.

## Related Concepts
- [[concepts/engine-masterdns]]
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/hilt-cross-process-injection]]
- [[concepts/persistent-logger-accumulation-trap]]

## Sources
- [[daily/2026-05-21.md]] records the seven MasterDNS review fixes: startup timeout, intentional upstream ignore, case-insensitive config filtering, eager resolver cache, host-port parsing, stdout cap, and DataStore error logging.
- [[daily/2026-05-21.md]] records that `runBlocking + withTimeoutOrNull` in a Hilt provider was treated as a DI anti-pattern and replaced with an eager snapshot cache.
