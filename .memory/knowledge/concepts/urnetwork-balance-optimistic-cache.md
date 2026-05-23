---
title: "URnetwork Balance Optimistic Cache (SharedPreferences)"
aliases: [balance-cache, urnetwork-balance-persistence, optimistic-hydration]
tags: [urnetwork, architecture, cache, ux]
sources:
  - "daily/2026-05-19.md"
  - "daily/2026-05-20.md"
  - "daily/2026-05-22.md"
  - "daily/2026-05-23.md"
created: 2026-05-19
updated: 2026-05-23
---

# URnetwork Balance Optimistic Cache (SharedPreferences)

`UrnetworkBalanceCache` (SharedPreferences-backed) hydrates `RealUrnetworkBalanceRepository._state` from the last known balance snapshot before the first API refresh completes. This allows the UI to show traffic limits and usage data immediately on screen open — even before the engine is active or a network request returns.

## Key Points

- Without cache: balance UI shows loading spinner until engine active + first API call completes
- With cache: last successful balance values shown immediately from SharedPreferences; refreshed in background
- Cache write: only on successful API refresh — never persists partial/error state
- Cache read: in `RealUrnetworkBalanceRepository` constructor — `_state` initialized from cache if available
- Cache invalidation: none explicit — stale data shown until refresh overwrites; staleness is acceptable for balance display (soft data)

## Details

### Architecture

```kotlin
class UrnetworkBalanceCache(private val prefs: SharedPreferences) {
    fun load(): UrnetworkBalanceState? { /* read from prefs */ }
    fun save(state: UrnetworkBalanceState) { /* write to prefs */ }
}

class RealUrnetworkBalanceRepository @Inject constructor(
    private val sdkBridge: UrnetworkSdkBridge,
    private val cache: UrnetworkBalanceCache,
) {
    private val _state = MutableStateFlow(cache.load() ?: UrnetworkBalanceState.Loading)

    suspend fun refresh() {
        val result = sdkBridge.fetchSubscriptionBalance() ?: return
        val newState = result.toUiState()
        _state.value = newState
        cache.save(newState)  // only on success
    }
}
```

### UX Rationale

Balance data is "soft" — seeing last known limits (e.g., "10 GB remaining") before an active session does not mislead the user. The alternative (showing nothing until refresh) creates a worse experience on slow connections or when the engine hasn't been started.

### Traffic Formula Verification

Related to this task: `usedBytes = startBalanceByteCount - balanceByteCount - openTransferByteCount` matches upstream `SubscriptionBalanceViewModel.kt`. The 2× overcount reported in v0.1.4 is not from the formula — source investigation pending.

### Plan Label Removal

`urnetwork_balance_plan_label` row removed from `UrnetworkBalanceCard.BalanceDetails`. The tariff plan name added noise without user value; balance limits and usage are sufficient.

### ViewModel initialValue Fix (v0.1.9, 2026-05-20)

`UrnetworkEngineSettingsViewModel.balanceState` was declared as:

```kotlin
val balanceState = combine(/* ... */).stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = UrnetworkBalanceState.INITIAL  // ← bug
)
```

The `RealUrnetworkBalanceRepository._state` was correctly hydrated from cache in its constructor. But the ViewModel's `stateIn(initialValue = INITIAL)` meant that the UI observer saw the `INITIAL` sentinel until the `combine` flow emitted — which required at least one upstream event. Result: opening the balance screen showed a loading spinner even though cached data was already available in the repository.

Fix: `initialValue = balanceRepository.state.value` — reads the already-hydrated value at ViewModel construction time. Cache hydration now visible to the UI immediately on screen open without any async round-trip.

### pendingBytes Semantics and availableBytes Formula (2026-05-23)

`openTransferByteCount` (SDK field, exposed as `pendingBytes` in Ozero) is bytes **in active relay sessions right now** — traffic being transferred at this moment, a reserve that will move into `usedBytes`. It is NOT "income in processing" or "payout in pipeline." Adding it to "Доступно" would double-count: those bytes are already deducted from `balanceBytes` via the `usedBytes` computation path.

Final formula for "Доступно" display (sentinel at `UrnetworkBalanceRepositoryTest.kt:81`):

```kotlin
val availableBytes = balanceBytes + reliabilityBonusBytes
// reliabilityBonusBytes = min(meanReliabilityWeight * 100, 100).GiB  — heuristic, not real SDK field
```

`reliabilityBonusBytes` is a cosmetic heuristic derived from the SDK's `meanReliabilityWeight` field (0.0–1.0). It was added at user request to approximate the URnetwork "reliability bonus" concept, since the SDK does not expose an explicit `bonusByteCount` field. Displayed alongside `availableBytes` but based on estimated relay quality, not actual accrued bytes.

Progress bar formula: `total = used + pending + available` (where `available` includes the heuristic bonus). This distorts the denominator cosmetically but keeps it synchronized with the "Доступно" readout per user requirement.

This replaces the earlier (incorrect) formula `availableBytes = balanceBytes + pendingBytes + reliabilityBonusBytes` (commit `277d1dfb`), reverted by commit `defc0438`. The revert removed `pendingBytes` from the sum.

If URnetwork SDK eventually exposes a real `bonusByteCount` field, replace the `reliabilityBonusBytes` heuristic with it.

### FREE_TIER_CAP_BYTES Removal (2026-05-22)

`UrnetworkBalanceCard` originally capped displayed balance at `FREE_TIER_CAP_BYTES = 34 GiB` to normalize the server-side accumulation artifact (see [[concepts/urnetwork-balance-accumulation-mechanism]]). This cap was removed in commit `e0d53ca4` — the display formula changed to `balanceBytes.coerceAtLeast(0L)` (show real balance, clamp at 0). The decision: the server race is not client-fixable, and hiding legitimate surplus GiBs produces worse UX than showing the real number.

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] — full URnetwork SDK integration context
- [[concepts/urnetwork-walletauth-per-device-registration]] — balance is linked to wallet identity
- [[concepts/hiltviewmodel-toomanyfunctions-decomposition]] — balance logic moved to `UrnetworkEngineSettingsViewModel` after VM split

## Sources

- [[daily/2026-05-19.md]] — Session v0.1.5: `UrnetworkBalanceCache` (SharedPreferences) injected into `RealUrnetworkBalanceRepository`; `_state` hydrates from cache on init; successful refresh writes snapshot; plan label removed; traffic formula confirmed correct vs upstream
- [[daily/2026-05-20.md]] — v0.1.9 prep: ViewModel `stateIn(initialValue = INITIAL)` masked cache hydration; fix: `initialValue = balanceRepository.state.value` to expose cache immediately at screen open
- [[daily/2026-05-22.md]] — Session 16:42: FREE_TIER_CAP_BYTES=34GiB removed from UrnetworkBalanceCard; show real balance via coerceAtLeast(0L); cap decision reversed (see urnetwork-balance-accumulation-mechanism)
- [[daily/2026-05-23.md]] — Session 02:16: `openTransferByteCount` (pendingBytes) = active session bytes in flight, NOT payout income; removing from "Доступно" formula (commit defc0438, reverting 277d1dfb); final formula `balanceBytes + reliabilityBonusBytes` with sentinel at `UrnetworkBalanceRepositoryTest.kt:81`; `reliabilityBonusBytes` is a heuristic from `meanReliabilityWeight × 100 GiB`
