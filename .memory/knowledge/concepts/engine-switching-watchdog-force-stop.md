---
title: "Engine Switching Watchdog Must Force Stop"
sources:
  - "daily/2026-05-22.md"
created: 2026-06-12
updated: 2026-06-12
---

# Engine Switching Watchdog Must Force Stop

The engine-switching watchdog must not only clear visual switching state. If the previous engine fails to stop, clearing `_switching` while leaving the engine alive can allow the next engine to start in parallel, producing a VPN slot or traffic conflict that looks like a WARP failure.

## Key Points

- A switching watchdog that only resets UI state is insufficient when `chainOrchestrator.stop()` hangs.
- The previous engine can continue running after the UI considers switching timed out.
- Starting WARP while ByeDPI is still alive creates parallel-engine conflict and broken traffic.
- Watchdog behavior must be tied to actual stop/teardown ownership, not only button color.
- The user clarified this bug was engine-switch watchdog behavior, not the earlier network-change restart fix.

## Details

The observed failure mode was a WARP switch after a previous engine failed to stop promptly. `TunnelController` watchdog timeout cleared `_switching`, but did not force the old chain to stop. If `chainOrchestrator.stop()` exceeded the warning timeout, the old engine could remain alive while the new WARP attempt started, so WARP appeared connected or starting but traffic did not pass reliably.

This is a lifecycle ownership bug, not a cosmetic UI issue. A visual watchdog can prevent an eternal yellow button, but it cannot prove resource cleanup. Switching correctness requires a terminal stop outcome or a force-stop path before the next engine is allowed to own the VPN pipeline.

## Related Concepts

- [[concepts/visual-connected-switching-state]] - UI switching state must track the intended target and not clear on wrong-engine `Connected`.
- [[concepts/vpn-switch-confirm-stop-before-start]] - Engine switches must wait for confirmed stop before starting the next VPN.
- [[concepts/engine-switch-failure-containment]] - Failed engine stop/start state must not poison neighboring engine transitions.

## Sources

- [[daily/2026-05-22]] - Session 23:11: WARP watchdog root cause was `TunnelController` timeout clearing `_switching` without stopping the engine; ByeDPI could survive and WARP could start in parallel; user clarified the task was switching watchdog behavior, not network-change switching.
