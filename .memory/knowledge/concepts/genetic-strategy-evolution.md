---
title: "Genetic Algorithm for ByeDPI Strategy Optimization"
aliases: [strategy-evolution, gene-pool, evolution-engine, strategy-gene]
tags: [byedpi, architecture, genetic-algorithm, optimization]
sources:
  - "daily/2026-05-12.md"
  - "daily/2026-05-13.md"
created: 2026-05-12
updated: 2026-05-13
---

# Genetic Algorithm for ByeDPI Strategy Optimization

Ozero Sprint 5 introduced a genetic algorithm layer on top of ByeDPI's auto-strategy testing. Instead of iterating over a fixed list of 75 strategies, `EvolutionEngine` evolves strategy parameters (ByeDPI CLI args) through mutation and crossover, using `GeneMemory` to persist fitness scores across sessions. The key architectural constraint: chromosomes must be evaluated sequentially because the ByeDPI engine cannot run multiple instances in parallel.

## Key Points

- `StrategyGene` encodes ByeDPI CLI args as a chromosome; `GenePool` provides mutation/crossover operators and vocabulary from domain presets
- `EvolutionEngine.evaluatePopulation` runs sequential evaluation — parallel engine starts are invalid (single SOCKS5 port, single TUN fd)
- `GeneMemory` persists fitness scores to disk as JSON; `importRawJson` must validate BEFORE writing to prevent corrupt file on bad input
- SNI domains from domain list presets feed into `GenePool` as vocabulary for the `-H` flag gene
- `SavedStrategyStore` allows users to bookmark winning strategies from either the fixed-list test or the evolutionary search
- Chromosome fitness cache (`HashMap<Chromosome, Double>` inside `evolve()`) prevents redundant `byeDpiEngine.start()` calls for repeated chromosomes
- Persistent `StrategyFitnessCache` with TTL 24h skips already-tested strategies across sessions; DI-injected singleton
- Fitness formula: `successRate * (1.0 / (1.0 + avgLatencyMs / 2000.0))` — hyperbolic latency penalty (linear `1 - 0.2*lat/timeout` gives <1% effect, is dead code)
- `ByeDpiKnownSeeds` object in `engine-byedpi` module provides 75 upstream strategies as evolution seeds; pinned priority seeds fed first; `runEvolution()` merges `userSeeds + ByeDpiKnownSeeds.commands`
- Population size default raised 20→25 in `StrategyTestSettings`; `stagnationThreshold` must use `coerceAtLeast(3)` to prevent degenerate single-generation cutoff; `targetFitness` default 0.85
- Auto-save best chromosome to `SavedStrategyStore` after each evolution run
- `SavedStrategy.lastVerifiedAtMs` tracks staleness; `markVerified` called after successful evolve; `StalenessLabel` composable shows warning after 7-day threshold
- Per-network `GeneMemory` + `StrategyFitnessCache` via `NetworkProfileDetector` + `EvolutionResourcesProvider` — replaces global singleton with per-network instances

## Details

### Architecture

The genetic algorithm builds on the existing auto-strategy testing infrastructure ([[concepts/byedpi-auto-strategy-testing]]). The fixed 75-strategy list serves as the initial population seed. `EvolutionEngine` then:

1. Evaluates each chromosome by starting ByeDPI with the encoded args, probing target sites through SOCKS5, and computing a fitness score (success percentage)
2. Selects top performers via tournament selection
3. Applies crossover (combining arg fragments from two parents) and mutation (randomly changing flag values)
4. Persists fitness scores to `GeneMemory` for cross-session learning

The sequential evaluation constraint was discovered during code review: `List.map { suspend {} }` does not provide parallelism — `coroutineScope { map { async {} }.awaitAll() }` is needed for parallel execution. However, even with correct coroutine usage, parallel evaluation is architecturally invalid because ByeDPI binds to a single SOCKS5 port and requires exclusive access to the TUN fd.

### Chromosome Fitness Cache

`evolve()` maintains a `HashMap<Chromosome, Double>` for the duration of a single evolution run. When crossover or mutation produces a chromosome already seen in that generation or a prior one, the cached fitness score is reused without re-launching the ByeDPI engine. This is safe because fitness is deterministic for a given args string and network state (within a single run), and eliminates redundant engine start/stop cycles that dominate wall-clock time.

### Fitness Formula Evolution

The original fitness formula was pure success rate (0–1). Sprint 5 added a latency term. The linear form `1 - 0.2 * avgLatency / timeout` was implemented first but produces less than 1% effect on scores in practice — effectively dead code. The correct form is hyperbolic: `successRate * (1.0 / (1.0 + avgLatencyMs / 2000.0))`. At 2000ms latency, the multiplier is 0.5; at 200ms, it is 0.91. This gives meaningful separation between fast and slow strategies with equal success rates, without dominating the success signal.

### Known Seeds Bootstrap

`ByeDpiKnownSeeds` is a singleton object in the `engine-byedpi` module containing 75 ByeDPI command strings sourced from ByeByeDPI upstream (`proxytest_strategies.list`). `StrategyTestViewModel.runEvolution()` concatenates `userSeeds + ByeDpiKnownSeeds.commands` as the initial population before the first generation. Pinned priority seeds (strategies previously confirmed working by the user) are placed at the front of the seed list, ensuring the algorithm evaluates known-good strategies first. This guarantees the algorithm starts from a viable neighborhood rather than random chromosome initialization, significantly reducing time-to-convergence on typical Russian ISP networks.

### Auto-Save Best and Target Fitness

After each evolution run completes, the best chromosome (highest fitness) is automatically saved to `SavedStrategyStore`. This ensures the user never loses a discovered strategy due to session termination or app crash. The `targetFitness` parameter (default 0.85) provides an early-stop condition: if any chromosome reaches this fitness threshold, evolution halts and the strategy is applied. This prevents unnecessary evaluation cycles when a good-enough strategy is found early.

### Dead Settings Anti-Pattern

Sprint 3-5 code review found 5 settings that appeared in the Strategy Test UI but had no corresponding implementation logic: `requestsPerDomain`, `delayBetweenMs`, `sniDomain` (input field), `useCustomStrategies`, and `customStrategies`. These "dead settings" create UX confusion — the user sees a control, adjusts it, but the behavior never changes. Options: implement the full logic or remove the UI elements. Dead settings are a code smell specific to iterative feature development where UI scaffolding is added before the backend logic.

### GenePool Vocabulary

`GenePool` maintains a vocabulary of valid values for each gene position. For the SNI domain gene (`-H` flag), the vocabulary is populated from:
- Hardcoded defaults (google.com, etc.)
- User's domain list presets (from `DomainListManager`)

This allows the genetic algorithm to try different SNI values during mutation, potentially discovering that certain domains are less likely to trigger DPI inspection.

### GeneMemory Persistence

`GeneMemory` stores strategy→fitness mappings in a JSON file on disk. Two concurrency issues were found during audit:

1. `scores` was a shared mutable `HashMap` without synchronization — race condition under concurrent read/write from evaluation coroutines. Fixed with `ConcurrentHashMap` and `@Synchronized` annotations.
2. `importRawJson` wrote to disk before validating the JSON structure — corrupt input produced a corrupt file. Fixed: validate first, write second.

### SavedStrategyStore

Users can save winning strategies from the results screen. `SavedStrategyStore` uses a load-then-save pattern that was not atomic — concurrent saves could lose data. Fixed with a `Mutex` guard around the load+modify+save sequence.

### Persistent StrategyFitnessCache

Beyond the in-memory `HashMap<Chromosome, Double>` cache within a single `evolve()` run, a persistent `StrategyFitnessCache` stores fitness results to disk with a 24-hour TTL. When a strategy was already tested within the TTL window, the cache returns the stored fitness without launching the ByeDPI engine. This is critical for cross-session efficiency: a user who runs evolution, closes the app, and re-runs evolution the next day does not re-test strategies that were evaluated hours ago. The cache is DI-injected as a singleton and cleared when TTL expires.

### SavedStrategy Staleness Tracking

`SavedStrategy` includes a `lastVerifiedAtMs` timestamp field. After a successful evolution run, `markVerified()` is called on all strategies that were re-tested, updating their verification timestamp. `StalenessLabel` is a Compose composable that displays a visual warning when a strategy has not been verified for more than 7 days. This signals to the user that network conditions may have changed since the strategy was last confirmed effective — ISP DPI configurations evolve, and a strategy that worked last week may be blocked today.

### Per-Network Memory Isolation

The initial architecture used a single global `GeneMemory` and `StrategyFitnessCache` — all fitness scores and evolution history were shared across all network environments. This is incorrect: a strategy optimal for WiFi on ISP-A may be useless on mobile data via ISP-B. The fix replaces the global singleton with per-network instances via `NetworkProfileDetector` and `EvolutionResourcesProvider`.

`NetworkProfileDetector` identifies the current network profile (WiFi SSID, mobile carrier, connection type). `EvolutionResourcesProvider` returns the appropriate `GeneMemory` and `StrategyFitnessCache` instances for the detected profile. When the network changes (e.g., switching from WiFi to mobile data), the provider returns a different memory/cache pair, ensuring evolution history is isolated per network environment. This is a structural change from singleton to provider pattern in the DI graph.

## Related Concepts

- [[concepts/byedpi-auto-strategy-testing]] - The fixed-list strategy testing that this genetic algorithm extends
- [[concepts/byedpi-args-parsing]] - Strategy genes encode ByeDPI CLI args; same argv[0] and -K traps apply
- [[concepts/gene-memory-concurrency-traps]] - Concurrency bugs found in GeneMemory and SavedStrategyStore during audit
- [[concepts/byedpi-strategy-runtime-disconnect]] - Winning args static at start; auto-save mitigates but runtime hot-reload still absent

## Sources

- [[daily/2026-05-12.md]] - Session 14:05: Sprint 5 implementation (StrategyGene + GenePool + EvolutionEngine + GeneMemory); Session 15:08: code review found sequential evaluation requirement, dead settings, SNI→GenePool vocabulary; Session 18:34: audit found GeneMemory race condition, SavedStrategyStore atomicity, importRawJson validation order
- [[daily/2026-05-13.md]] - Chromosome fitness cache, hyperbolic latency fitness formula (linear form dead code), ByeDpiKnownSeeds 75 upstream seeds with pinned priority, population size 25, stagnationThreshold coerceAtLeast(3) CI fix, persistent StrategyFitnessCache TTL 24h, SavedStrategy.lastVerifiedAtMs staleness 7-day threshold + StalenessLabel, auto-save best chromosome, targetFitness 0.85, per-network GeneMemory + StrategyFitnessCache via NetworkProfileDetector + EvolutionResourcesProvider
