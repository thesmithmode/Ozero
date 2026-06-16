---
title: onTaskRemoved VPN swipe standard
sources:
  - daily/2026-05-21.md
created: 2026-05-21
updated: 2026-06-12
---
# onTaskRemoved VPN swipe standard

## Key Points
- Swiping the app from Android recents is not a user intent to stop the VPN.
- `OzeroVpnService.onTaskRemoved` must stay empty or `super`-only; it must not call `stopVpn()`.
- VPN shutdown belongs to the explicit UI toggle, system VPN revoke, or other service-owned stop paths.
- Stopping VPN on swipe also breaks URnetwork relay because relay depends on the background tunnel remaining alive.

## Details

The 2026-05-21 session added `onTaskRemoved -> stopVpn()` to remove the Android VPN key icon after swiping Ozero from recents. That was classified as a critical UX regression because Android VPN foreground services are expected to survive app-recents removal. The key icon is the correct system signal that the VPN is still active.

The fix was to revert the `stopVpn()` call and add a lifecycle sentinel around `OzeroVpnService.onTaskRemoved`. This also restored URnetwork relay reliability after swipe because the relay is coupled to the VPN tunnel, not to the app task being visible. Future changes must treat `onTaskRemoved` separately from `onRevoke`, where teardown is valid when Android revokes the VPN slot.

## Related Concepts
- [[concepts/urnetwork-relay-always]]
- [[concepts/engine-ownership-boundary]]
- [[concepts/vpn-slot-conflict-detection]]
- [[concepts/fail-closed-watchdog-startup-lockdown-contract]]

## Sources
- [[daily/2026-05-21.md]] records that `onTaskRemoved -> stopVpn` was reverted because swipe from recents is not VPN shutdown intent.
- [[daily/2026-05-21.md]] records that the revert restored VPN-in-background behavior and improved URnetwork relay continuity.
