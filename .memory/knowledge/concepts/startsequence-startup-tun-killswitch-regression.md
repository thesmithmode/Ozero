---
title: StartSequenceCoordinator must preserve startup TUN and killswitch ordering
sources:
  - daily/2026-06-03.md
created: 2026-06-13
updated: 2026-06-13
---
# StartSequenceCoordinator must preserve startup TUN and killswitch ordering
## Key Points
- A failing `StartSequenceCoordinatorBehaviorTest` should not be weakened when it exposes startup TUN or killswitch ordering regression.
- Clearing state with `getAndSet(null)` can erase information still needed by the startup branch.
- The fix belongs in coordinator logic when the regression case describes real lifecycle behavior.
- Startup TUN, killswitch, and engine readiness must be proven as one sequence, not as independent flags.
## Details
The 2026-06-03 log identifies a confirmed `common-vpn` failure around `StartSequenceCoordinator`. The assistant concluded that the test exposed a real coordinator bug: a `getAndSet(null)` path broke a startup TUN/killswitch regression case. That made the test an ownership signal rather than a brittle assertion to relax.

This belongs to the broader Ozero rule that shared VPN lifecycle code has to preserve the exact ordering between TUN establishment, engine start, readiness, and fail-closed state. A local pass in one branch is not enough if the coordinator loses state needed by a different startup path. The concept extends [[startsequence-branch-specific-sentinels]] and [[fail-closed-watchdog-startup-lockdown-contract]].
## Related Concepts
- [[startsequence-branch-specific-sentinels]]
- [[fail-closed-watchdog-startup-lockdown-contract]]
- [[common-vpn-split-start-and-shutdown-branch-coverage]]
- [[vpn-switch-confirm-stop-before-start]]
## Sources
- `daily/2026-06-03.md`: stated that the `StartSequenceCoordinator` failure was confirmed in coordinator logic, not in the test.
- `daily/2026-06-03.md`: recorded that `getAndSet(null)` broke the startup TUN/killswitch regression case.
