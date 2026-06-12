---
title: "TunnelState.Probing nullable engineId contract"
sources:
  - "daily/2026-05-18.md"
created: 2026-06-12
updated: 2026-06-12
---
# TunnelState.Probing nullable engineId contract

## Key Points
- `TunnelState.Probing.engineId` is nullable by design; null means the probed engine is not yet known.
- Extracting status-label helpers can lose smart-cast context and accidentally require non-null `EngineId`.
- UI label helpers must handle `Probing(engineId = null)` with a generic probing fallback.
- Exhaustive `when` branches must account for new stub `EngineId` values as the enum grows.

## Details

The `StatusLabel` decomposition introduced a compile regression because `probingLabelRes(engineId: EngineId, ...)` assumed a non-null engine, while `TunnelState.Probing.engineId` is `EngineId?`. Before extraction, Kotlin smart-cast context hid the mismatch inside the larger `when` expression. After extraction, the helper signature made the nullability contract explicit and CI failed.

The fix changed the helper to accept `EngineId?` and return the generic `main_status_probing` string for null. A sentinel test covered both `pickStatusLabelRes` and `probingLabelRes` null handling. Later enum additions required explicit handling of stub engine IDs, which ties this contract to [[concepts/ci-style-failure-hides-compile-regression]] and [[concepts/strategy-extraction-import-retention]]: small refactors can expose type or exhaustiveness gaps that were previously implicit.

The product-level mapping remains engine-aware. ByeDPI should not show "Поиск маршрута" when the probing state is only a short readiness phase, while reconnecting state should take precedence over failure/probing labels when recovery is active.

## Related Concepts
- [[concepts/ci-style-failure-hides-compile-regression]]
- [[concepts/strategy-extraction-import-retention]]
- [[concepts/engine-readiness-vs-false-connected]]
- [[concepts/byedpi-connection-probe-injection-contract]]

## Sources
- [[daily/2026-05-18]]: Sessions 16:30 and 18:55 record the CI failure at `MainScreen.kt:786`, caused by passing nullable `Probing.engineId` to a non-null helper.
- [[daily/2026-05-18]]: Session "новая" records that new stub `EngineId` values made `probingLabelRes` non-exhaustive until null and stub branches were added.
