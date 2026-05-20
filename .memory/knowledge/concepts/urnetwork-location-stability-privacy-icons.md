---
title: "URnetwork Location Stability and Privacy Icons"
aliases: [location-stability-icons, connectedlocation-stable-strongprivacy, location-picker-icons]
tags: [urnetwork, sdk, ui, compose, locations]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# URnetwork Location Stability and Privacy Icons

`ConnectLocation` in the URnetwork SDK exposes two boolean quality fields: `stable` and `strongPrivacy`. These map to the "stability badge" and "privacy badge" shown in the upstream URnetwork app's location picker. `UrnetworkLocationItem` was extended to carry these fields, and `LocationRow` renders them as inline icons alongside the location name. Icon colors must be mapped from existing `OzeroPalette` states since `StateWarning`/`StateSuccess` do not exist in the palette.

## Key Points

- `ConnectLocation.stable: Boolean` — false = peer known to be unstable (high packet loss or frequent disconnects)
- `ConnectLocation.strongPrivacy: Boolean` — true = peer routes through extra privacy hops; shown as lock icon
- `!stable` → `Icons.Filled.Warning` with color `OzeroPalette.StateConnecting` (orange)
- `strongPrivacy` → `Icons.Filled.Lock` with color `OzeroPalette.StateConnected` (green)
- `OzeroPalette.StateWarning` and `OzeroPalette.StateSuccess` do NOT exist — must use `StateConnecting`/`StateConnected` as semantic proxies
- Icons are size 14.dp, placed inline inside `LocationRow` before or after the location name

## Details

### SDK Field Discovery

The upstream bringyour `ConnectLocation` type (found in `Контекст/android/`) has `stable` and `strongPrivacy` boolean properties. These are populated by the URnetwork backend based on peer telemetry. The Ozero location picker initially showed only country/region/city names without any quality indicators, diverging from the upstream UX where these badges are prominent navigation signals for users.

### OzeroPalette Mapping

The original URnetwork app uses its own color system with distinct `stable` and `strongPrivacy` palette colors. Ozero's `OzeroPalette` only defines connection-state colors:

| Badge | Original URnetwork color | OzeroPalette mapping |
|-------|--------------------------|----------------------|
| `!stable` (warning) | amber/yellow | `StateConnecting` (orange) |
| `strongPrivacy` (trust) | green | `StateConnected` (green) |

The semantic meaning is preserved: orange signals caution (unstable connection), green signals positive quality (strong privacy). The exact shade differs from upstream but the intent is identical.

### Inner Class Name Shadowing Trap

During implementation of `LocationRow`, a test defined an `inner class ConnectLocation(...)` inside the test class for fixture construction. Because `ConnectLocation` is also the name of a production SDK type imported at the top of the test file, the inner class shadowed the import. All usages of `ConnectLocation` inside the test resolved to the inner class, causing type mismatches when passing fixture objects to functions expecting the SDK type.

Rule: never give a test helper class the same name as a production type it wraps. Use a prefix/suffix (`FakeConnectLocation`, `TestLocation`, `mkLocation()`).

### Location Picker Integration

`UrnetworkLocationItem` is the data model passed to each row in the location list:

```kotlin
data class UrnetworkLocationItem(
    val location: ConnectLocation,
    val stable: Boolean,
    val strongPrivacy: Boolean,
)
```

`LocationRow` renders the icons conditionally:

```kotlin
if (!item.stable) {
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,  // decorative; row label already reads location name
        tint = OzeroPalette.StateConnecting,
        modifier = Modifier.size(14.dp)
    )
}
if (item.strongPrivacy) {
    Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = null,
        tint = OzeroPalette.StateConnected,
        modifier = Modifier.size(14.dp)
    )
}
```

### ktlint Line Length in Test Files

During this feature, a CI failure surfaced that `ktlint` enforces the 120-character line limit in test files (`src/test/`) equally to production files. Long fixture construction lines (spreading multiple `ConnectLocation` constructor parameters across a single line) triggered the violation. Rule: verify test files with `ktlintCheck` before push, not just production files.

## Related Concepts

- [[concepts/urnetwork-filterlocations-trigger]] — filterLocations("") trigger that populates the list; stability icons rely on the same location delivery
- [[concepts/urnetwork-location-hierarchy-migration]] — setPreferredLocation(ConnectLocation?) migration; the same ConnectLocation type carries stable/strongPrivacy
- [[concepts/per-engine-ui]] — location picker lives in URnetwork engine settings screen; icons are part of that dedicated UI

## Sources

- [[daily/2026-05-18.md]] — Session 23:57: SDK stable/strongPrivacy fields discovered; UrnetworkLocationItem extended; LocationRow icons added with StateConnecting/StateConnected palette mapping; ktlint >120 chars in test files caught in CI; inner class ConnectLocation name shadowing production type → renamed immediately
