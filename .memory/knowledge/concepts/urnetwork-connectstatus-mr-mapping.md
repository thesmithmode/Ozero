---
title: "URnetwork ConnectStatus to MR Strings Mapping"
aliases: [urnetwork-connect-status-localization, urconnectstate-mr, sdk-string-localization]
tags: [urnetwork, localization, moko, viewmodel, regression]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# URnetwork ConnectStatus to MR Strings Mapping

SDK status strings from the URnetwork SDK must be mapped to MR (moko-resources) resource identifiers before reaching the UI. A ViewModel refactoring in commit `5e49a5f1` dropped this mapping, causing raw SDK strings like "Unable to get IP" to appear directly in the UI. The content was valid (it is a real diagnostic state) but was no longer localized.

## Key Points

- Before regression: `UrConnectState` held `connectMessageRes: Int?` — mapped SDK `ConnectStatus` enum values to `MR.strings.*` resource IDs
- After regression: `UrConnectState` only held `message: String?` — SDK string passed raw to UI, bypassing localization
- Root cause: ViewModel refactoring dropped the `ConnectStatus → MR.strings` enum-to-resource mapping layer
- MR strings are defined in `engines-core` module via moko-resources (`MR.strings.*`)
- Rule: SDK status strings must always be mapped to MR resources at the ViewModel boundary; never forwarded raw to UI

## Details

### Before and After

Before commit `5e49a5f1` (regression commit `97a2ccf8` introduced the correct pattern), `UrConnectState` was:

```kotlin
data class UrConnectState(
    val status: UrConnectStatus,
    val connectMessageRes: Int?,  // MR.strings resource ID
    val isConnected: Boolean
)
```

The ViewModel mapped `ConnectStatus` from the SDK to `connectMessageRes` using a `when` expression:

```kotlin
ConnectStatus.UNABLE_TO_GET_IP -> MR.strings.connect_status_unable_to_get_ip.resourceId
ConnectStatus.CONNECTING -> MR.strings.connect_status_connecting.resourceId
// etc.
```

After the refactoring, `UrConnectState` became:

```kotlin
data class UrConnectState(
    val status: UrConnectStatus,
    val message: String?,  // raw SDK string
    val isConnected: Boolean
)
```

The UI composable received and displayed `message` directly: `Text(state.message ?: "")`. This shows "Unable to get IP" in English on all locales.

### Why the Content is Valid but the Delivery is Wrong

"Unable to get IP" is a legitimate diagnostic state — the P2P engine connected to peers but could not determine the device's external IP address (often due to EPERM on vendor ROMs when calling `bindSocket`). The string should appear in the UI. The issue is delivery: SDK strings are always English, always implementation-specific phrasing, and not under the app's control for localization. Displaying them raw locks the UI to English and tightly couples UI text to SDK internals.

### The Correct Pattern

The ViewModel is the correct mapping layer. For each `ConnectStatus` enum value from the SDK, the ViewModel produces an `MR.strings` resource ID. The UI receives the resource ID, resolves it to a localized string using moko-resources' `stringResource(id)`, and displays the result. SDK strings never cross the ViewModel boundary.

When the SDK introduces a new `ConnectStatus` value, the correct response is: add an MR string for it in all baseline locales (`ru`, `en`, `es`, `pt`), add the mapping in the ViewModel `when` expression, and add a test that verifies the mapping is exhaustive (no unhandled enum values).

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] - The broader URnetwork engine integration context
- [[concepts/urnetwork-control-network-modes]] - Related UrConnectState usage in the UI layer
- [[concepts/android-xml-string-escaping]] - Localization mechanics for Android string resources

## Sources

- [[daily/2026-05-13.md]] - Regression: commit 5e49a5f1 ViewModel refactoring dropped ConnectStatus→MR.strings mapping; raw SDK string "Unable to get IP" shown in UI; before-state had connectMessageRes: Int? field; fix = restore when-expression mapping at ViewModel boundary
