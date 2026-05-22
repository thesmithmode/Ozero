---
title: "onTaskRemoved — VPN Swipe Standard (Never stopVpn)"
aliases: [ontaskremoved-vpn, vpn-swipe-foreground, swipe-close-vpn-standard]
tags: [vpnservice, android, lifecycle, sentinel, gotcha, ux]
sources:
  - "daily/2026-05-21.md"
created: 2026-05-21
updated: 2026-05-21
---

# onTaskRemoved — VPN Swipe Standard (Never stopVpn)

Android VPN standard: swiping an app from the recents screen does NOT stop the VPN. `VpnService.onTaskRemoved` must be empty (or call `super` only). Calling `stopVpn()` from `onTaskRemoved` breaks the standard UX expectation and also kills any relay/provider processes that depend on the VPN tunnel remaining active in the background.

## Key Points

- `onTaskRemoved` fires when user swipes the app from recents — this is NOT "stop VPN" intent
- Standard Android behavior: VPN foreground service survives swipe; user turns off VPN via the dedicated UI toggle
- `OzeroVpnService.onTaskRemoved` must remain empty/super-only; sentinel `OzeroVpnServiceLifecycleTest` enforces this
- Calling `stopVpn()` from `onTaskRemoved` also killed URnetwork relay — relay is coupled to the VPN tunnel being alive (correct behavior, not a bug)
- Revert of `onTaskRemoved→stopVpn` restored VPN-in-background behavior; URnetwork relay continued working after swipe

## Details

### The Mistake

A session added `onTaskRemoved { stopVpn() }` to fix a user complaint that the VPN key icon persisted in the status bar after swipe. This is a misunderstanding of Android VPN semantics: the key icon is the correct indicator that a VPN is active. Removing the app from recents is not a signal to stop the VPN — many users expect VPN to keep running even when the app is not visible.

The immediate consequence was that swiping Ozero from recents disconnected all traffic and killed URnetwork relay income. Since relay is intentionally coupled to the tunnel being `Connected` (not a bug, documented in [[concepts/urnetwork-relay-always]]), this was a double regression.

### Sentinel

`OzeroVpnServiceLifecycleTest` contains a sentinel that verifies `onTaskRemoved` does NOT contain `stopVpn()`. This prevents future accidental re-introduction. The sentinel reads the source file and asserts the absence of `stopVpn` in the `onTaskRemoved` body, or asserts the body is empty/super-only.

### Related onRevoke Behavior

`onRevoke` is the correct place for forced teardown — it fires when another VPN app takes the slot or the user explicitly disables the VPN through system settings. `onRevoke` does call `postDelayed { Process.killProcess(pid) }` with a 1000ms delay to release the Go runtime and free the VPN slot for the next app. This is intentional and unrelated to `onTaskRemoved`.

## Related Concepts

- [[concepts/vpn-slot-conflict-detection]] — onRevoke → postDelayed killProcess to release VPN slot; EXTERNAL_VPN_RELEASE_DELAY_MS
- [[concepts/urnetwork-relay-always]] — relay coupled to tunnel.Connected; relay correctly stops when VPN stops
- [[concepts/engine-ownership-boundary]] — VpnService is sole engine lifecycle owner; lifecycle changes must be intentional

## Sources

- [[daily/2026-05-21.md]] — Session 03:07: `onTaskRemoved→stopVpn` detected as breaking standard Android VPN UX; revert committed; sentinel `OzeroVpnServiceLifecycleTest` added; URnetwork relay restored as side-effect of revert
