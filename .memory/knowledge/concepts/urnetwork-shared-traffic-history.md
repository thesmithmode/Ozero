---
title: "URnetwork Shared Traffic 30-Day History (Local Delta Tracking)"
aliases: [shared-traffic-history, urnetwork-delta-chart, cumulative-sdk-delta-pattern]
tags: [urnetwork, architecture, chart, sdk, pattern]
sources:
  - "daily/2026-05-20.md"
created: 2026-05-20
updated: 2026-05-20
---

# URnetwork Shared Traffic 30-Day History (Local Delta Tracking)

The URnetwork SDK exposes only a cumulative `unpaidByteCount` — a monotonically increasing counter reset on wallet payout. It provides no daily or session-level breakdown. To display a 30-day bar chart of shared traffic, Ozero stores daily deltas locally in SharedPreferences and reconstructs the histogram from stored diffs.

## Key Points

- SDK gives `unpaidByteCount` cumulative only — no daily, session, or period breakdown available
- `UrnetworkSharedTrafficHistory` stores a JSON map of `date→deltaBytes` in SharedPreferences, one entry per calendar day
- Each `load()` call computes delta vs stored `last_cumulative` and appends today's delta to the map
- Write on every `load()` — rate-limited by SDK refresh cadence, not continuous
- Bar chart rendered on `Canvas` in `UrnetworkSharedTrafficScreen`; total shared shown as sum of all stored deltas

## Details

### Pattern: Local Delta from SDK Cumulative

When an SDK only exposes cumulative counters, local delta tracking is the standard client-side solution:

```kotlin
class UrnetworkSharedTrafficHistory(private val prefs: SharedPreferences) {
    fun load(currentCumulative: Long): Map<String, Long> {
        val lastCumulative = prefs.getLong(KEY_LAST_CUMULATIVE, 0L)
        val todayDelta = maxOf(0L, currentCumulative - lastCumulative)
        val today = LocalDate.now().toString()  // YYYY-MM-DD

        val history = loadHistory()  // Map<String, Long> from JSON
        val updated = history + (today to (history.getOrDefault(today, 0L) + todayDelta))

        prefs.edit()
            .putString(KEY_HISTORY, updated.toJson())
            .putLong(KEY_LAST_CUMULATIVE, currentCumulative)
            .apply()

        return updated.filterKeys { isWithin30Days(it) }
    }
}
```

**Key invariants:**
- `todayDelta = maxOf(0, current - last)` guards against counter reset (wallet payout zeroes SDK counter)
- `today` key uses ISO date string — allows aggregation across app restarts within the same day
- Pruning to 30 days prevents unbounded SharedPreferences growth
- `last_cumulative` is always updated even when delta = 0, preventing phantom delta on next read

### UI Architecture

`UrnetworkSharedTrafficScreen` renders a Canvas-based bar chart with one bar per calendar day for the last 30 days. Bar height is proportional to daily delta bytes. The total shared traffic is computed as the sum of all stored deltas (not the current cumulative counter — the cumulative resets on payout, but stored deltas persist across payouts, accumulating the user's total lifetime contribution in the 30-day window).

### Counter Reset on Payout

When the URnetwork wallet receives a payout, `unpaidByteCount` resets to 0. The `maxOf(0, current - last)` guard in `load()` ensures this counter reset is not interpreted as a negative delta. The result: on payout day, `todayDelta = 0` for the post-reset period until new sharing accumulates. The history correctly shows no traffic for the payout moment, then continues accumulating normally.

### Comparison with Speed Chart Architecture

The speed chart (`MainScreen`) uses a different approach: live in-memory `SpeedSample` ring buffer with time-aligned bucket aggregation (see [[concepts/chart-nice-max-dynamic-scaling]]). Shared traffic history uses persistent SharedPreferences storage because it spans days, survives app restarts, and accumulates across sessions. The architectures are complementary: real-time in-memory for current throughput, persistent delta-store for historical contribution tracking.

## Related Concepts

- [[concepts/urnetwork-balance-accumulation-mechanism]] - URnetwork server-side balance accumulation; `unpaidByteCount` is what this history tracks pre-payout
- [[concepts/urnetwork-sdk-integration]] - Full URnetwork SDK integration including relay and SDK lifecycle
- [[concepts/chart-nice-max-dynamic-scaling]] - Speed chart architecture (in-memory, real-time) — different use case to this 30-day persistent approach
- [[concepts/urnetwork-balance-optimistic-cache]] - Parallel pattern: SharedPreferences-backed cache for balance data; same storage tier

## Sources

- [[daily/2026-05-20.md]] - v0.1.9 prep session: SDK cumulative-only constraint; `UrnetworkSharedTrafficHistory` with date→delta SharedPreferences map; `load()` computes and stores delta; wallet payout counter reset guard; bar chart on Canvas in UrnetworkSharedTrafficScreen
