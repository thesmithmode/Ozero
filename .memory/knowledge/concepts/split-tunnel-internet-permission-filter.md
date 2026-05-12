---
title: "Split Tunnel App Filtering: INTERNET Permission vs Launchable"
aliases: [split-tunnel-internet-filter, applistprovider-internet, getpackagesholdingpermissions]
tags: [android, vpn, split-tunnel, gotcha, ui]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# Split Tunnel App Filtering: INTERNET Permission vs Launchable

Split tunnel app selection must show ALL apps with the `android.permission.INTERNET` permission, not just user-launchable apps. Filtering by `isUserVisibleApp` (or `getLaunchIntentForPackage != null`) excludes critical system services — Google Play Services, Android WebView, carrier services — that use the network and should be configurable in ALLOWLIST/BLOCKLIST modes. The correct API is `PackageManager.getPackagesHoldingPermissions(arrayOf(INTERNET), GET_META_DATA)`.

## Key Points

- `isUserVisibleApp` / `getLaunchIntentForPackage` filter = only apps with launcher Activity → misses system services with INTERNET permission
- `PackageManager.getPackagesHoldingPermissions(arrayOf("android.permission.INTERNET"))` = all apps that use network → complete list
- Missing Google Play Services from split tunnel BLOCKLIST → Google services bypass VPN → data leak for privacy-focused users
- Reference: AmneziaWG (PORTAL WG) uses `getPackagesHoldingPermissions(INTERNET)` for their split tunnel implementation
- Work profile support preserved: `UserManager.getUserProfiles()` → query each user's packages → unified list with profile badges

## Details

### The Launchable Filter Problem

Android split tunnel UI typically shows a list of apps the user can select for VPN inclusion/exclusion. The naive implementation uses `packageManager.getLaunchIntentForPackage(pkg)` or `applicationInfo.flags & FLAG_SYSTEM == 0` to show only "user-visible" or "user-installed" apps. This creates a blind spot: system services that actively use the network (Google Play Services, Android WebView, DNS services, carrier apps) are invisible in the list but still route traffic through (or around) the VPN.

In ALLOWLIST mode, where only selected apps go through the VPN, a user who wants all Google traffic through the VPN cannot select Google Play Services because it's hidden. In BLOCKLIST mode, a user who wants to exclude Google services from the VPN cannot find them in the list.

### The Correct Approach

```kotlin
val internetApps = packageManager.getPackagesHoldingPermissions(
    arrayOf(android.Manifest.permission.INTERNET),
    PackageManager.GET_META_DATA
)
```

This returns every installed package that declares `<uses-permission android:name="android.permission.INTERNET"/>` in its manifest. Since Android requires this declaration for any network socket usage, the result is a complete list of apps that may generate network traffic — exactly the set relevant for split tunnel configuration.

### Reference Implementation

AmneziaWG (PORTAL WG) uses this exact approach in their split tunnel UI. The discovery was made during v0.0.12 debugging when comparing Ozero's app list (missing system services) against AmneziaWG's (complete list). The difference traced to the filtering API used.

### Additional Bugs Found in Split Tunnel (v0.0.11)

Two related split tunnel bugs were fixed in the same session:

1. **XOR read**: `readSplitConfig` was reading both `allowedPackages` AND `disallowedPackages` regardless of mode. In ALLOWLIST mode, only `allowedPackages` should be read; in BLOCKLIST mode, only `disallowedPackages`. Reading both wastes I/O and creates semantic confusion.

2. **Silent empty fallback**: `?: emptySet()` on read timeout/error was silent — no log, no warning. In ALLOWLIST mode, this means the user thinks traffic is filtered but the rules set is empty (nothing goes through VPN). Fix: `PersistentLoggers.warn` on fallback.

3. **excludeSelf asymmetry**: WARP engine was not getting `excludeSelf = true` in one code path. Fixed to apply `excludeSelf = true` for ALL non-TunFdAcceptor engines unconditionally. Sentinel test added.

## Related Concepts

- [[concepts/tun-self-exclusion-sdk-engines]] - excludeSelf = true for all engines; related split tunnel configuration
- [[concepts/engine-ownership-boundary]] - Split tunnel bugs discovered alongside Engine Ownership Boundary violations in v0.0.12
- [[concepts/vpnservice-builder-traps]] - `addDisallowedApplication` is the Builder API that split tunnel configuration feeds into

## Sources

- [[daily/2026-05-11.md]] - Session 11:38: readSplitConfig XOR fix, excludeSelf=true for all engines, PersistentLoggers.warn on fallback
- [[daily/2026-05-11.md]] - Session 14:12: AppListProvider `isUserVisibleApp` → `getPackagesHoldingPermissions(INTERNET)` per AmneziaWG/PORTAL WG reference; work profile support preserved
