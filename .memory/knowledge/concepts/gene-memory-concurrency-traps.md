---
title: "GeneMemory and SavedStrategyStore Concurrency Traps"
aliases: [gene-memory-race, saved-strategy-atomicity, concurrent-hashmap-fix]
tags: [concurrency, android, gotcha, byedpi, testing]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# GeneMemory and SavedStrategyStore Concurrency Traps

The ByeDPI strategy evolution system (Sprint 5) introduced two shared-state components with concurrency bugs found during a 6-subagent audit: `GeneMemory.scores` used a plain `HashMap` accessed from evaluation coroutines without synchronization, and `SavedStrategyStore.add` performed a non-atomic load→modify→save sequence. Both were silent data corruption vectors — no crash, just lost or inconsistent data.

## Key Points

- `GeneMemory.scores`: plain `HashMap` shared across evaluation coroutines → race condition on concurrent put/get. Fix: `ConcurrentHashMap` + `@Synchronized` on aggregate operations
- `SavedStrategyStore.add`: loads JSON from disk, appends entry, saves back — without lock, concurrent adds can overwrite each other. Fix: `Mutex` around the entire load+modify+save sequence
- `GeneMemory.importRawJson`: wrote file to disk BEFORE validating JSON structure → corrupt input = corrupt file. Fix: parse and validate first, write only on success
- These bugs were invisible to unit tests because tests ran single-threaded; only audit-level analysis caught them
- Pattern: any shared mutable state in coroutine-based systems needs explicit synchronization even when "only one coroutine writes" — coroutine scheduling is non-deterministic

## Details

### GeneMemory Race Condition

`GeneMemory` maintains a `Map<String, Double>` mapping strategy arg strings to their fitness scores. During evolution, `EvolutionEngine.evaluatePopulation` updates scores after each strategy evaluation. Although evaluation is sequential (single SOCKS5 port constraint), the score map is also read by the UI layer (to display rankings) and by the selection operator (to pick parents for crossover). These reads happen on different coroutine dispatchers.

The `HashMap` implementation is not thread-safe. Concurrent `put` during evaluation and `get` during UI rendering can produce `ConcurrentModificationException` or silently corrupt the internal hash table. The fix uses `ConcurrentHashMap` for individual operations and `@Synchronized` on compound operations (e.g., "get top N scores sorted by fitness"):

```kotlin
class GeneMemory {
    private val scores = ConcurrentHashMap<String, Double>()
    
    fun record(strategy: String, fitness: Double) {
        scores[strategy] = fitness  // atomic single-key put
    }
    
    @Synchronized
    fun topStrategies(n: Int): List<Pair<String, Double>> {
        return scores.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }
}
```

### SavedStrategyStore Non-Atomic Update

`SavedStrategyStore` persists user-bookmarked strategies to a JSON file via a load→modify→save pattern:

```kotlin
// BROKEN: non-atomic
suspend fun add(strategy: SavedStrategy) {
    val current = loadFromDisk()      // read
    val updated = current + strategy  // modify
    saveToDisk(updated)               // write
}
```

If two `add` calls overlap (user rapidly bookmarks two strategies), both read the same `current` list, each appends one entry, and the second `saveToDisk` overwrites the first — losing one entry. The fix wraps the entire sequence in a `Mutex`:

```kotlin
private val mutex = Mutex()

suspend fun add(strategy: SavedStrategy) {
    mutex.withLock {
        val current = loadFromDisk()
        saveToDisk(current + strategy)
    }
}
```

### importRawJson Validation Order

`GeneMemory.importRawJson(json: String)` was writing the raw JSON to disk before parsing it. If the JSON was malformed, the file on disk became corrupt, and subsequent reads would fail. The fix: parse into the in-memory map first (catching `JSONException`), then write to disk only on successful parse.

### SNI Seed Tokenizer Trap

`GenePool` populates its SNI vocabulary from domain list presets. When a preset contains compound args like `"-s domain.com"`, the pool's `split(" ")` tokenizer breaks it into `["-s", "domain.com"]` — two separate tokens where one was intended. The `-s` fragment becomes a standalone "domain" in the vocabulary, producing invalid ByeDPI args when mutated into the `-H` flag gene. The fix requires a context-aware tokenizer that respects quoted or flag-prefixed compound values rather than naive whitespace splitting.

## Related Concepts

- [[concepts/genetic-strategy-evolution]] - The system where these components live; evaluation is sequential but UI/persistence access is concurrent
- [[concepts/warp-slot-corrupt-json-resilience]] - Same pattern: JSON persistence with corruption risk; per-slot try/catch is the WARP equivalent of GeneMemory's validation-first approach
- [[concepts/test-tautology-always-green]] - Logger/GeneMemory tests also exhibited tautology assertions discovered in the same audit

## Sources

- [[daily/2026-05-12.md]] - Session 18:34: audit found GeneMemory.scores HashMap race (fix: ConcurrentHashMap + @Synchronized), SavedStrategyStore.add non-atomic (fix: Mutex), importRawJson write-before-validate (fix: validate first), SNI seed `"-s domain"` splits into 2 tokens in GenePool (fix: smart tokenizer)
