---
title: "URnetwork Balance Accumulation Mechanism"
aliases: [urnetwork-balance-102gib, transfer-balance-insert, urnetwork-free-quota]
tags: [urnetwork, backend, balance, quota]
sources:
  - "daily/2026-05-20.md"
  - "daily/2026-05-22.md"
created: 2026-05-20
updated: 2026-05-22
---

# URnetwork Balance Accumulation Mechanism

The URnetwork server uses `INSERT` (not `UPDATE`) when adding free transfer balance rows to the `transfer_balance` table. Each midnight UTC cron call inserts a new 34 GiB row with an end time 30 hours in the future. Because the cron period is 24 hours and the row duration is 30 hours, there is a 6-hour daily overlap window where 2–3 rows are simultaneously active, causing `SubscriptionBalance` to sum 68–102 GiB. This is a server behavior, not a client bug.

## Key Points

- `RefreshFreeTransferBalance = 34 GiB` per INSERT; `RefreshTransferBalanceDuration = 30h`
- Midnight UTC cron (`ScheduleRefreshTransferBalances`) fires every 24h, inserts a new row for ALL networks
- `SubscriptionBalance` = SUM of all rows where `EndTime > now` → 2–3 rows active during 6h overlap = 68–102 GiB
- NOT a client bug: the client never calls `AddRefreshTransferBalance`
- NOT a ban risk: the server is doing the inserting; client receives the sum passively
- UX mitigation: cap free-tier display at 34 GiB (canonical value) regardless of server sum

## Details

### Insert-Not-Update Pattern

The `transfer_balance` table stores time-bounded balance grants. Each row has a `StartTime` and `EndTime`. The `RefreshFreeTransferBalance` job issues an `INSERT` for every network in the system. If the network was created before midnight, it may accumulate overlap rows: a row from the previous day still active (endTime = T+30h) plus a new row from today (endTime = T+24h+30h). In the 6-hour window between day N midnight and day N+1 06:00 UTC, three rows can be simultaneously active for networks that existed on day N-1.

`SubscriptionBalance` queries `WHERE EndTime > now()` and sums all matching rows. A network with 3 active rows sees 3 × 34 GiB = 102 GiB displayed.

### Verification

The behavior was confirmed by tracing the `clientId` — it was identical in all rows, confirming a single network rather than multiple `networkCreate` calls. The user who observed 68 GiB later checked and saw 34 GiB once the overlap window expired, confirming the time-bounded nature of the surplus.

### Client-Side UX Fix (Reversed 2026-05-22)

Originally the Ozero client capped the displayed balance at `FREE_TIER_CAP_BYTES = 34 GiB` to normalize the transient 68–102 GiB overlap sum. This cap was **removed** (commit `e0d53ca4`, session 16:42) by user decision: the server-side surplus is non-client-fixable, and hiding the real number actively obscures legitimate bonus GiBs from time overlaps. The new display formula is `balanceBytes.coerceAtLeast(0L)` — show the real SDK value, never negative.

## Related Concepts

- [[concepts/urnetwork-balance-optimistic-cache]] - SharedPreferences cache hydrates balance state before first refresh
- [[concepts/urnetwork-guest-mode-relay-blocker]] - Guest JWT limits relay monetization; balance mechanics are separate

## Sources

- [[daily/2026-05-20.md]] - Session 13:00: server source traced; INSERT not UPDATE; RefreshFreeTransferBalance=34GiB, duration=30h, cron=24h; 3-row overlap window confirmed; client doesn't call AddRefreshTransferBalance; UX fix initially: cap at 34 GiB
- [[daily/2026-05-22.md]] - Session 16:42: FREE_TIER_CAP_BYTES=34GiB removed from UrnetworkBalanceCard; decision: show real balance (coerceAtLeast(0L)) since backend race is not client-fixable and cap hid real balance
