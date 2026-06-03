---
title: Auto preflight all fail stop contract
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Auto preflight all fail stop contract

## Key Points
- Auto mode must stop VPN when preflight rejects every candidate engine.
- UI target state must not keep the service stuck in `Probing` after all candidates fail.
- Lockdown/startup TUN must be released through the normal stop path when auto-preflight cannot select an engine.
- The fix belongs to lifecycle state ownership, not timeout padding.
- This connects [[concepts/auto-candidate-terminal-status-invariant]] and [[concepts/fail-closed-watchdog-startup-lockdown-contract]].

## Details

The 2026-06-02 review found that auto mode could leave VPN in `Probing` when preflight rejected every engine. Even if a `targetForUi` value existed, there was no valid candidate left to start, so retaining probing state and startup lockdown resources was incorrect.

The contract is that all-fail preflight is terminal for the current start attempt. The service should issue a stop request and release startup resources rather than waiting for a nonexistent engine to become active. This preserves fail-closed semantics while avoiding a stuck state.

The issue is related to stale terminal status handling in auto-candidate selection: UI labels and target hints are not authoritative when the actual candidate set is empty. Attempt state must drive lifecycle cleanup.

## Related Concepts

- [[concepts/auto-candidate-terminal-status-invariant]]
- [[concepts/fail-closed-watchdog-startup-lockdown-contract]]
- [[concepts/chain-start-timeout-stale-engine-failure-cascade]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources

- [[daily/2026-06-02]]: Session 21:12 recorded a review finding that auto mode should stop VPN if preflight rejects all engines.
- [[daily/2026-06-02]]: Session 21:12 decided all-fail preflight should stop even when `targetForUi` exists.
- [[daily/2026-06-02]]: Session 21:12 tied the fix to avoiding stuck `Probing` state with held lockdown TUN.
