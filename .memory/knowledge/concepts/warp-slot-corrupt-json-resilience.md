---
title: "WARP Slot Store Corrupt JSON Resilience"
aliases: [warp-slot-corruption, datastore-json-resilience, slot-parse-resilience]
tags: [warp, storage, gotcha, datastore]
sources:
  - "daily/2026-05-05.md"
created: 2026-05-05
updated: 2026-05-05
---

# WARP Slot Store Corrupt JSON Resilience

`DataStoreWarpConfigSlotStore` stores all WARP configuration slots as a JSON array in a single DataStore key. Parsing the entire array atomically means one corrupt slot entry fails the entire parse, losing all configurations. The resilient pattern parses each slot individually with a per-slot try/catch, skipping corrupt entries while preserving all valid ones.

## Key Points

- Naive atomic JSON array parse: any malformed entry → `JsonParseException` → all slots lost
- Resilient pattern: parse each element individually, catch per-element, skip corrupt entries, log the error
- `setActive(id)` must guard against non-existent IDs before writing — setting an invalid `activeSlotId` produces a null `activeConfig` on next read
- DataStore's `edit {}` block should not redundantly read state it already has from `collect` — double-read creates loose semantics (state observed at collection time may differ from state at edit time, though rarely causes real crashes)
- These issues were found via adversarial (attack) review, not standard code review

## Details

### The Total Loss Failure Mode

All WARP slot configurations are serialized to JSON and stored under a single DataStore key. When `parseSlots` calls `Json.decodeFromString<List<WarpSlot>>(raw)`, it processes the entire array at once. A single slot with a corrupt field (unexpected type, missing required field, encoding error) throws `JsonParseException`, which propagates up and returns an empty list. From the user's perspective, all named configurations disappear.

The probability is low in practice (DataStore is transactional), but the consequence is severe (unrecoverable without backup). The resilient pattern:

```kotlin
fun parseSlots(raw: String): List<WarpSlot> {
    val array = JsonParser.parseString(raw).asJsonArray
    return array.mapNotNull { element ->
        runCatching { Json.decodeFromString<WarpSlot>(element.toString()) }
            .onFailure { Log.w(TAG, "Skipping corrupt slot: $it") }
            .getOrNull()
    }
}
```

Each slot is parsed independently. A corrupt slot is logged and skipped; all other slots survive.

### setActive Guard

`WarpEngineSettingsViewModel.setActive(id)` writes `id` as the active slot identifier in DataStore. If `id` does not correspond to any existing slot (e.g., a slot was deleted while UI was stale), subsequent reads produce `null` for `activeConfig`. The fix: verify `id` exists in the current slot list before persisting:

```kotlin
fun setActive(id: String) {
    val current = _uiState.value.slots
    if (current.none { it.id == id }) return  // guard: id must exist
    viewModelScope.launch { store.setActiveSlotId(id) }
}
```

### Relationship to Core Backup

Per-slot resilience handles partial corruption. Full JSON corruption or accidental DataStore file deletion remains unrecoverable. The `:core-backup` module is the complement — providing a recovery path when resilient parsing cannot help. See [[concepts/core-backup-module]].

## Related Concepts

- [[concepts/core-backup-module]] - Recovery path when corrupt JSON resilience is insufficient
- [[concepts/amnezia-wg-warp-migration]] - The slot system was introduced during AWG migration
- [[concepts/viewmodel-stateflow-test-race]] - Related ViewModel state management gotcha

## Sources

- [[daily/2026-05-05.md]] - Session 12:01: adversarial review found corrupt slot → all slots lost; fix = per-slot try/catch; setActive guard added; core-backup module decision followed
