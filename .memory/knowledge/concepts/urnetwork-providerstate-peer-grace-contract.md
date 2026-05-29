---
title: URnetwork providerState peer grace contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# URnetwork providerState peer grace contract

## Key Points
- URnetwork startup readiness must be short and must not wait five minutes for `peers > 0`.
- `providerStateAdded > 0`, `CONNECTED`, `tunnelStarted`, and issued connect signals are stronger readiness inputs than `peers` alone.
- `peers=0` belongs to runtime grace/recovery, not pre-start blocking.
- The relay subsystem must not be mixed into consumer-engine readiness diagnosis.

## Details
The URnetwork regression was traced to startup readiness rather than relay. The observed log pattern was `CONNECTING peers=0 deadline=300000ms` after `attachTun`, which blocked `onEngineStarted()` and prevented watchdog/recovery from taking over. The user clarified that a five-minute window should let the engine keep looking for peers, not block startup or mark the engine dead.

The chosen contract is short startup readiness followed by runtime peer grace. Startup can succeed once attach/connect activity and official SDK-like signals are present, while a lack of peers is handled by `EngineWatchdogCoordinator` after the engine enters the started lifecycle.

## Related Concepts
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]]
- [[concepts/urnetwork-startup-readiness-vs-runtime-peer-grace]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources
- [[daily/2026-05-29]] records that the 300000 ms startup wait caused URnetwork to remain in `CONNECTING peers=0`.
- [[daily/2026-05-29]] records the decision to move five-minute `peers=0` tolerance into runtime watchdog after `onEngineStarted()`.
