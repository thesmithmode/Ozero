---
title: "VpnService Main Thread Preload Pattern"
aliases: [preload-cached-settings, runblocking-elimination, vpnservice-anr-prevention]
tags: [android, vpn, performance, anr, pattern]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# VpnService Main Thread Preload Pattern

`runBlocking` on the Android main thread inside a VPN service's `startVpn` path creates ANR risk. DataStore `.first()` and Room DAO queries may each take 10-50ms on cold cache, and combining them blocks the main thread for up to 100ms on low-end devices. The preload pattern eliminates `runBlocking` by caching values eagerly in `onCreate` via coroutine collection, with `@Volatile` fields providing thread-safe reads from `startVpn`.

## Key Points

- `runBlocking { settingsRepository.settings.first() }` on main thread = ANR risk on low-end/Nubia devices (DataStore cold cache up to 50ms)
- `runBlocking { splitTunnelRulesProvider.activePackages() }` on main thread = additional 10-50ms Room query
- Combined blocking: ~60-100ms main thread block — within ANR threshold on slow devices with heavy system load
- Fix: `@Volatile cachedSettings` field populated by `serviceScope.launch { settings.collect { cachedSettings = it } }` in `onCreate`
- Split packages: one-shot fetch in `onCreate` + background refresh on each `startVpn` call
- Default fallback when cache is null (cold start race): safe defaults prevent crash, first VPN start may use slightly stale config

## Details

### The ANR Mechanism

Android's Application Not Responding (ANR) dialog triggers when the main thread is blocked for ~5 seconds. While individual DataStore or Room queries are far below this threshold, the risk compounds on devices with:

- Slow storage (eMMC vs UFS)
- Heavy system load (Nubia ROM background processes)
- Cold DataStore cache (first access after process creation)
- Room database not yet in page cache

The v0.0.2-1 code review (concern C1) identified two `runBlocking` calls in `OzeroVpnService.startVpn`: one for `settingsRepository.settings.first()` and one for `splitTunnelRulesProvider.activePackages()`. Each was individually fast (~10-50ms) but together blocked the main thread during the user's most latency-sensitive interaction — tapping the VPN connect button.

### Preload Architecture

The fix moves data fetching to `onCreate`, which runs when the service is first created (before any `startVpn` call):

```kotlin
private @Volatile var cachedSettings: OzeroSettings? = null
private @Volatile var cachedSplitPackages: Set<String> = emptySet()

override fun onCreate() {
    super.onCreate()
    startSettingsCachePreload()
}

private fun startSettingsCachePreload() {
    serviceScope.launch {
        settingsRepository.settings.collect { cachedSettings = it }
    }
    serviceScope.launch {
        cachedSplitPackages = splitTunnelRulesProvider.activePackages()
    }
}
```

`startVpn` reads `cachedSettings` and `cachedSplitPackages` without blocking. Split packages are also refreshed in background on each `startVpn` call, ensuring the cached value stays current for subsequent reconnects without blocking the main thread.

### Race Condition Handling

On cold start, there is a theoretical race where `startVpn` runs before the first `collect` emission populates `cachedSettings`. The code handles this with safe defaults — if `cachedSettings` is null, fallback values are used. This means the first VPN start after a fresh process creation may use default settings rather than user-customized ones, but this is preferred over an ANR. In practice, `onCreate` runs well before the user can tap the connect button, so the race is unlikely.

### Sentinel Tests

Three sentinel tests in `OzeroVpnServiceLifecycleTest` protect against regression:

1. `startVpn` does not contain `runBlocking` imports
2. `onCreate` calls `startSettingsCachePreload`
3. `startSettingsCachePreload` uses `collect` and `activePackages` (not blocking variants)

## Related Concepts

- [[concepts/vpnservice-builder-traps]] - Builder API traps in the same VpnService startup path
- [[concepts/nubia-rom-permission-enforcement]] - Nubia devices are most vulnerable to ANR due to heavy ROM background processes
- [[concepts/feature-branch-code-review-2026-05-01]] - C1 concern that identified the runBlocking calls

## Sources

- [[daily/2026-05-01.md]] - C1 fix: preload settings/split in onCreate, removed runBlocking from startVpn; sentinel tests added to OzeroVpnServiceLifecycleTest
