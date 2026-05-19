---
title: "URnetwork Balance Optimistic Cache (SharedPreferences)"
aliases: [balance-cache, urnetwork-balance-persistence, optimistic-hydration]
tags: [urnetwork, architecture, cache, ux]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
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

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] — full URnetwork SDK integration context
- [[concepts/urnetwork-walletauth-per-device-registration]] — balance is linked to wallet identity
- [[concepts/hiltviewmodel-toomanyfunctions-decomposition]] — balance logic moved to `UrnetworkEngineSettingsViewModel` after VM split

## Sources

- [[daily/2026-05-19.md]] — Session v0.1.5: `UrnetworkBalanceCache` (SharedPreferences) injected into `RealUrnetworkBalanceRepository`; `_state` hydrates from cache on init; successful refresh writes snapshot; plan label removed; traffic formula confirmed correct vs upstream
