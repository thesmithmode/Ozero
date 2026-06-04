---
title: "Auto preflight must stop VPN when all candidates fail"
sources:
  - daily/2026-06-02.md
created: 2026-06-04
updated: 2026-06-04
---
# Auto preflight must stop VPN when all candidates fail
## Key Points
- Auto mode must not leave the VPN running when preflight rejects every engine candidate.
- The stop path should release startup resources instead of leaving the tunnel in a half-started state.
- The contract applies even when the UI still has a target engine or pending selection state.
- This is a fail-closed behavior, not a presentation choice.
## Details
The daily log captured a startup edge case where every candidate engine fails preflight. In that case, auto mode must not keep the system in a `Probing` or otherwise transitional state with a held lockdown tunnel. Instead, it should stop the VPN and release resources so the user does not get a false impression that a retry is still progressing.

This behavior is tightly coupled to runtime ownership: preflight failure is a terminal startup outcome, and the service should unwind startup state explicitly. That places this article close to [[concepts/fail-closed-watchdog-startup-lockdown-contract]] and [[connections/runtime-restart-watchdog-preflight-state-ownership]].
## Related Concepts
- [[concepts/fail-closed-watchdog-startup-lockdown-contract]]
- [[connections/runtime-restart-watchdog-preflight-state-ownership]]
- [[concepts/runtime-restart-service-owned-action]]
## Sources
- `daily/2026-06-02.md`: the log states that auto mode should stop VPN and release startup resources when preflight rejects every engine candidate.
