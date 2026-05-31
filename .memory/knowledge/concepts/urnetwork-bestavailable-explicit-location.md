---
title: URnetwork explicit bestAvailable location
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# URnetwork explicit bestAvailable location

## Key Points
- URnetwork default "Best available" must be represented as an explicit SDK `bestAvailable` location, not as persistent `null`.
- Settings UI should display a fast fallback from store or cache before SDK state is ready.
- `bestAvailable` is a selection token, not a concrete country for exit-node display.
- Engine startup and settings state must share the same default-location contract.

## Details

On 2026-05-31, URnetwork failed to connect until the user manually selected "Best available", and settings did not show the selected country reliably. The root cause recorded in the daily log was that the default choice was represented as implicit `null`, leaving `DeviceLocal.connectLocation` or default location empty until manual interaction.

The fix direction was to persist and send an explicit SDK `ConnectLocationId.bestAvailable=true` token and to let the settings UI read a quick fallback from `UrnetworkConfigStore` or cache. This prevents the UI from depending solely on delayed SDK `selectedLocation()` state and keeps auto-connect behavior consistent with what the user sees in settings.

## Related Concepts
- [[concepts/urnetwork-location-token-best-available]]
- [[concepts/urnetwork-runtime-grace-startup-gate]]
- [[concepts/urnetwork-connectstatus-mr-mapping]]
- [[concepts/exit-node-strategy-ui-unification]]

## Sources
- [[daily/2026-05-31]]: Session 19:02 records that URnetwork did not connect until manual "Best available" selection.
- [[daily/2026-05-31]]: Session 19:17 records the explicit `bestAvailable` decision and store/cache UI fallback.
- [[daily/2026-05-31]]: Session 19:29 records that `bestAvailable` must not be treated as a concrete country in exit-node strategy.
