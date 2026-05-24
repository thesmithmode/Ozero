---
title: "URnetwork ConnectStatus Mapping Regression (commit 5e49a5f1)"
aliases: [urnetwork-connectstatus-regression, connectmessageres-removed, sdk-strings-bypass-localization]
tags: [urnetwork, regression, localization, architecture, gotcha]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# URnetwork ConnectStatus Mapping Regression (commit 5e49a5f1)

Commit `5e49a5f1` removed `connectMessageRes: Int?` from `UrConnectState` and deleted the mapping from SDK `ConnectStatus` values to `MR.strings` localized resource IDs. As a result, raw SDK strings (e.g., `"Unable to get IP"`) now flow directly into the UI instead of being mapped through localized string resources. This is a regression: the string itself is correct and important for the user to see — the problem is bypassing the localization layer.

## Key Points

- `connectMessageRes: Int?` was removed from `UrConnectState` in commit `5e49a5f1`
- The mapping `SDK ConnectStatus → MR.strings.<localized_id>` was deleted along with it
- Raw SDK strings (English-only, internal SDK vocabulary) now appear directly in UI across all locales
- The string `"Unable to get IP"` IS a meaningful error for users — the issue is not hiding it but mapping it through localized resources
- Fix: restore `connectMessageRes: Int?` field (or equivalent) and restore the `ConnectStatus → MR.strings` mapping table

## Details

### What Was Removed

`UrConnectState` previously carried a `connectMessageRes: Int?` field. This field held a `@StringRes` ID from the `MR.strings` multiplatform resources — a localized string corresponding to the current SDK `ConnectStatus`. The ViewModel mapped each `ConnectStatus` enum value to an appropriate localized message:

```
ConnectStatus.CONNECT_STATUS_UNABLE_TO_GET_IP → MR.strings.urnetwork_error_unable_to_get_ip
ConnectStatus.CONNECT_STATUS_CONNECTING → MR.strings.urnetwork_status_connecting
...
```

Commit `5e49a5f1` removed this mapping and the field. The ViewModel now passes the raw SDK status string directly.

### Why This Is a Regression, Not a Simplification

The motivation for the removal was likely to simplify the ViewModel — the field required maintaining a mapping table that needed to be updated whenever the SDK added a new `ConnectStatus` value. However, the tradeoff is incorrect:

1. **Localization loss**: Russian, Spanish, and Portuguese users see English SDK strings
2. **Vocabulary mismatch**: SDK strings use internal vocabulary ("Unable to get IP") while the UI should use user-friendly phrasing ("Could not obtain an IP address")
3. **Unmaintained mapping is better than no mapping**: a stale mapping (missing some new ConnectStatus values) degrades gracefully — the UI shows the raw SDK string only for unmapped values. Deleting the mapping entirely degrades all values.

### The Correct Fix Direction

The string `"Unable to get IP"` is important user-facing feedback — it must be shown, not suppressed. The fix is:

1. Restore `connectMessageRes: Int?` to `UrConnectState` (or equivalent structure)
2. Restore the `ConnectStatus → MR.strings` mapping in the ViewModel
3. Add a fallback: unmapped `ConnectStatus` values → raw SDK string (acceptable degradation for new values)
4. Update all locales (`values-en/`, `values-es/`, `values-pt/`) with the string resources

## Related Concepts

- [[concepts/urnetwork-connectstatus-mr-mapping]] - The original mapping article that describes the intended architecture
- [[concepts/urnetwork-sdk-integration]] - SDK integration patterns; SDK string vocabulary vs UI string vocabulary

## Sources

- [[daily/2026-05-13.md]] - Commit `5e49a5f1` identified as removing `connectMessageRes: Int?` from `UrConnectState`; raw SDK strings ("Unable to get IP") now bypass localization; regression noted — string IS important to show but must go through MR.strings mapping
