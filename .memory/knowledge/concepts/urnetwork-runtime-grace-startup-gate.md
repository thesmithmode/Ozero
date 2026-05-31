---
title: URnetwork peer grace belongs after startup
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- URnetwork startup must be short and bounded; it must not wait five minutes for `peers=0`.
- The five-minute peer grace belongs in runtime watchdog after `onEngineStarted()`.
- `providerStateAdded`, `CONNECTED`, `tunnelStarted`, and issued connect intent are better readiness signals than peer count alone.
- URnetwork engine and URnetwork relay are separate systems and must not be diagnosed as one module.

## Details
The 2026-05-29 URnetwork investigation found that a startup wait of `300000ms` caused the engine to remain in `CONNECTING peers=0` before lifecycle startup completed. Because `onEngineStarted()` did not fire, watchdog and recovery logic could not run even though the SDK might still be trying to connect.

The accepted fix direction was to use a short startup gate based on attach/connect readiness and move the long `peers=0` tolerance into runtime monitoring. This preserves the user's requested behavior that the engine keeps searching for peers without treating zero peers as immediate failure, while avoiding a blocked startup lifecycle.

## Related Concepts
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]]
- [[concepts/urnetwork-providerstate-peer-grace-contract]]
- [[concepts/urnetwork-engine-relay-separation]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources
- [[daily/2026-05-29]] records the `CONNECTING peers=0 deadline=300000ms` symptom and the decision to move peer grace out of startup.
- [[daily/2026-05-29]] records the chosen readiness signals including `providerStateAdded`, `CONNECTED`, `tunnelStarted`, and connect issuance.
- [[daily/2026-05-29]] records the instruction not to mix URnetwork engine diagnostics with relay diagnostics.
