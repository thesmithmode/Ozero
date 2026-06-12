---
title: "URnetwork Peer Column Must Use Visible Engine, Not Manual Engine"
aliases: [urnetwork-peer-column-auto-mode, ipinfocard-auto-mode-peers, urnetwork-status-strip-split]
tags: [urnetwork, ui, compose, status, gotcha]
sources:
  - "daily/2026-05-19.md"
created: 2026-06-12
updated: 2026-06-12
---

# URnetwork Peer Column Must Use Visible Engine, Not Manual Engine

URnetwork peer count in the main status strip must be gated by the engine currently visible in `TunnelState`, not by `manualEngine`. In auto-mode `manualEngine` can be `null` while the active candidate is URnetwork, so a call-site that checks only `manualEngine == EngineId.URNETWORK` hides the right peer column and makes the strip look centered as one block.

## Key Points

- `IpInfoCard` is a 50/50 status strip: left side is exit node title plus IP/flag/loading/error, right side is URnetwork-only peer count.
- Auto-mode can show URnetwork as active while `manualEngine == null`.
- The call-site must use `isUrnetworkVisibleInMain(tunnelState, manualEngine)` before passing `urnetworkPeerCount`.
- `ExpertStatusBadges` needs `tunnelState` context so the UI can distinguish manual mode from auto-mode.
- Sentinel coverage should verify that peer column visibility works in both manual URnetwork mode and auto-mode URnetwork candidate state.

## Details

The user-visible regression was a status strip that stayed centered in one visual block when URnetwork was active through auto-mode. The layout itself had already been split into a left column for "Exit node" and a right URnetwork peer column. The remaining bug was at the call-site: peer data was passed only when `manualEngine == EngineId.URNETWORK`.

Auto-mode breaks that assumption. In auto-mode, the manually selected engine is absent, while the active tunnel state can still identify URnetwork as the visible engine. The fix was to route the decision through the existing helper `isUrnetworkVisibleInMain(tunnelState, manualEngine)` and pass `urnetworkPeerCount` only when that helper returns true.

This keeps ownership aligned with [[concepts/urnetwork-main-toggle-settings-ownership]]: main screen status belongs to runtime state, while engine settings own configuration toggles. It also extends [[connections/engine-ui-state-ownership-feedback-loop]] because visible status components must be driven by the same source of truth as status labels and runtime summaries.

## Related Concepts

- [[concepts/urnetwork-main-toggle-settings-ownership]] - Separates URnetwork settings controls from runtime status surfaces.
- [[connections/engine-ui-state-ownership-feedback-loop]] - Explains why UI correctness depends on runtime/status ownership boundaries.
- [[concepts/per-engine-ui]] - Requires per-engine UI behavior instead of generic one-size-fits-all rendering.

## Sources

- [[daily/2026-05-19.md]] - Session v0.1.6: `IpInfoCard` call-site used `manualEngine == EngineId.URNETWORK`; in auto-mode `manualEngine = null`, so the peer column was hidden; fix uses `isUrnetworkVisibleInMain(tunnelState, manualEngine)` and adds a sentinel for auto-mode peer column visibility.
