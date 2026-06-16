---
title: "WARP Manual Config Screen Contract"
aliases: [warp-manual-config, warp-edit-screen-contract, warp-slot-manual-edit]
tags: [warp, ui, settings, hilt, navigation]
sources:
  - "daily/2026-05-06.md"
created: 2026-06-12
updated: 2026-06-12
---

# WARP Manual Config Screen Contract

WARP manual configuration is a per-slot settings workflow, not a generic app-level editor. The screen must be reachable from the active WARP slot settings, use the WARP config store through an injected ViewModel, and stay within Compose/detekt size constraints by extracting section composables.

## Key Points

- The route belongs in the main navigation graph and must import the `WarpManualConfigScreen` destination explicitly.
- `WarpManualConfigViewModel` must be a Hilt ViewModel with `WarpConfigStore` injection, not a manually constructed screen helper.
- Manual edit UI should be split into stable sections such as interface fields and AWG fields to avoid detekt `LongMethod`.
- Slot import auto-activation was treated as intentional behavior, not a bug.
- Persisted fields should match actual UI surface; `allowedIps` remained a follow-up because no UI field existed.

## Details

The manual config work added an explicit route from WARP engine settings to a screen where the active slot can be edited. The first CI pass caught missing Hilt wiring and missing navigation import, proving that Compose navigation, DI, and module ownership are part of the contract. The screen is not complete just because the composable exists; it must be visible through `RootNavigation`, created through the correct ViewModel factory, and backed by the owning WARP store.

The follow-up review found maintainability constraints: `WarpEditScreen` grew past the detekt `LongMethod` threshold, and an unused `AwgParams` import tripped ktlint. The fix was structural but narrow: extract `WarpInterfaceSection` and `WarpAwgSection`, keep the main composable small, and remove stale imports. Functional follow-ups such as `allowedIps` persistence and `tunSpec()` sync were left separate because they required additional UI or EngineWarp synchronization work.

## Related Concepts

- [[concepts/per-engine-ui]] - Each engine owns its settings screen surface.
- [[concepts/warp-config-import-naming-dedup]] - WARP slot import and activation behavior interact with manual slot editing.
- [[concepts/detekt-returncount-extract-paths]] - Similar principle: satisfy static gates by extracting real subflows, not suppressing.
- [[concepts/ktlint-test-line-length-ci-blocker]] - Style gates catch small syntactic drift such as unused imports and formatting.

## Sources

- [[daily/2026-05-06.md]] - Session 11:12: WARP manual configuration route, screen, strings, navigation destination, and ViewModel methods were added; CI then exposed missing Hilt annotation and navigation import.
- [[daily/2026-05-06.md]] - Session 11:26: review found ktlint unused import and detekt `WarpEditScreen` length; extraction into `WarpInterfaceSection` and `WarpAwgSection` resolved the static gate.
