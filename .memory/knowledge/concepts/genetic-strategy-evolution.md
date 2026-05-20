---
title: "Genetic Algorithm for ByeDPI Strategy Optimization"
aliases: [strategy-evolution, gene-pool, evolution-engine, strategy-gene]
tags: [byedpi, architecture, genetic-algorithm, optimization]
sources:
  - "daily/2026-05-12.md"
  - "daily/2026-05-13.md"
  - "daily/2026-05-14.md"
  - "daily/2026-05-17.md"
  - "daily/2026-05-18.md"
  - "daily/2026-05-19.md"
created: 2026-05-12
updated: 2026-05-19
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
- **Fitness formula v3 (current, 2026-05-17)**: per-probe `computeProbeScore(ProbeResult): Float` gradient — NetworkError→0.0, Timeout→0.1, HTTP 4xx→0.4, HTTP 5xx→0.3, partial body→0.8, full success→1.0; replaces binary 0/1 to give GA selection pressure over "almost working" strategies. See [[concepts/granular-probe-fitness-scoring]]. **Superseded versions** preserved for historical context: v2 = `successRate^1.5 * (1 - clamp(avgLatency, 0, 3000) / 3000)` (power-law + linear latency); v1 = `successRate * 1/(1+lat/2000)` (hyperbolic, <1% effect). v2's `successRate` aggregation still consumes per-probe scores from v3; the formulas are stacked, not parallel
- `ByeDpiKnownSeeds` object in `engine-byedpi` module provides 75 upstream strategies as evolution seeds; pinned priority seeds fed first; `runEvolution()` merges `userSeeds + ByeDpiKnownSeeds.commands`
- GA v2 defaults: populationSize 30, maxGen 20, eliteCount 3, targetFitness 0.85; initial population split 40% seed / 30% memory / 30% random; `stagnationThreshold` must use `coerceAtLeast(3)` to prevent degenerate single-generation cutoff
- Auto-save best chromosome to `SavedStrategyStore` after each evolution run
- `SavedStrategy.lastVerifiedAtMs` tracks staleness; `markVerified` called after successful evolve; `StalenessLabel` composable shows warning after 7-day threshold
- Per-network `GeneMemory` + `StrategyFitnessCache` via `NetworkProfileDetector` + `EvolutionResourcesProvider` — replaces global singleton with per-network instances
- **Thompson Sampling** (v0.1.5) replaces UCB (Upper Confidence Bound) for strategy exploration; `ucbScore` function deleted; `GeneMemoryTest` rewritten; sentinel added to verify UCB is gone

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

The original fitness formula was pure success rate (0–1). Sprint 5 added a latency term through three iterations:

1. **Linear (dead code)**: `1 - 0.2 * avgLatency / timeout` — <1% effect, no practical separation
2. **Hyperbolic v1**: `successRate * (1.0 / (1.0 + avgLatencyMs / 2000.0))` — 0.5x at 2000ms, 0.91x at 200ms
3. **Power-law v2** (current): `successRate^1.5 * (1 - clamp(avgLatency, 0, 3000) / 3000)` — `^1.5` amplifies high success (0.8→0.72, 0.95→0.92); linear latency penalty capped at 3s

### GA v2 Parameter Tuning (2026-05-14)

| Parameter | v1 | v2 | Rationale |
|-----------|----|----|-----------|
| populationSize | 20→25 | 30 | More genetic diversity per generation |
| maxGen | 10 | 20 | More generations before stagnation |
| eliteCount | 5 | 3 | Less exploitation lock-in, more exploration |
| Initial pop | 100% seed | 40% seed / 30% memory / 30% random | Cross-session learning + diversity |

The initial population split balances exploitation (seed+memory) with exploration (random). 5 sentinel tests guard GA v2 parameters.

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

### GA v2 Parameter Tuning (2026-05-14)

The `feat/ga-byedpi-v2` branch introduced significant parameter changes:

- **Population size**: 20→30. Larger population explores more of the strategy space per generation
- **Max generations**: 10→20. Doubles the evolution runway before giving up
- **Elite count**: 5→3. Fewer elites increases selection pressure, pushing toward convergence faster
- **Target fitness**: 0.7→0.85. Higher bar for "good enough" before early stopping
- **Fitness formula**: `successRate^1.5 * (1 - clamp(avgLatency, 0, 3000) / 3000)`. The `^1.5` exponent penalizes strategies with merely adequate success rates more aggressively. The latency term uses linear decay from 3000ms ceiling instead of the hyperbolic `1/(1+lat/2000)` form — produces stronger separation at higher latencies
- **Initial population composition**: 40% seed (from `ByeDpiKnownSeeds`), 30% memory (from `GeneMemory` best performers), 30% random (fresh exploration). Previous split was not explicitly controlled
- **5 sentinel tests** cover the new parameter defaults

### Cache Poisoning Fix

Migration from old fitness formula to new one leaves stale `0.0` scores in `StrategyFitnessCache`. The `get()` method now skips entries with `fitness == 0.0` — treating them as cache misses rather than valid zero-fitness results. Real fitness from the new formula is always > 0 for any strategy that was actually tested.

### Min Chromosome Length

Raised from 3→5 to prevent degenerate configurations (e.g., just `--ip 0.0.0.0 --port 1080`) that technically parse but produce no DPI bypass effect.

### Granular Probe Fitness Scoring v3 (2026-05-17)

Binary fitness (0/1 success rate) caused GA stagnation at generation 3-5: all failing strategies scored 0, providing no selection pressure toward "almost working" strategies. The `stagnationCount * 0.5f` mutation rate boost was a workaround — the user called it "костыль" (crutch).

Fix: `computeProbeScore(result: ProbeResult): Float` provides a gradient through connection stages: NetworkError→0.0, Timeout→0.1, HTTP 4xx→0.4, HTTP 5xx→0.3, partial body→0.8, full success→1.0. The GA now has a fitness landscape to climb rather than a binary cliff. The mutation rate boost was removed with a sentinel test forbidding its return. See [[concepts/granular-probe-fitness-scoring]] for full details.

### Stale server_fd as Root Cause of 0% Fitness (2026-05-18)

User-reported bug "подбор стратегий возвращает 0%" was traced to stale `server_fd` in the ByeDPI native layer. The evolution engine runs 600+ start/stop cycles. `jniStopProxy` performs only `shutdown()` without `close()`/reset — accumulated stale fds cause every `jniStartProxy` to return -1. All `EvalResult.startFailed=true` → fitness 0 for all chromosomes → GA returns meaningless 0%.

Root cause fix: unconditional `forceClose()` in both `stop()` and `start()` failure paths — not gated on `proxyJob.isActive`. See [[concepts/byedpi-stale-serverfd-unconditional-forceclose]].

Additionally, the `@Singleton ByeDpiEngine` is shared between VPN service and strategy testing ViewModel, creating state leakage. See [[concepts/byedpi-singleton-strategy-testing-isolation]].

Auto-save best chromosome to `savedStrategyStore` was also identified as polluting the favorites list — `onApply` should NOT write to favorites. Fixed by removing `savedStrategyStore.add` calls from `onApply` and `runEvolution` paths.

### Strategy Count Update: 75→78 (2026-05-17)

`ByeDpiKnownSeeds` updated from 75 to 78 strategies following ByeByeDPI v1.7.5 upstream update. All three files synchronized: `byedpi_strategies.list`, `ByeDpiKnownSeeds.kt`, and `ByeDpiAutoStrategyTest EXPECTED_COUNT=78`.

## Related Concepts

- [[concepts/byedpi-auto-strategy-testing]] - The fixed-list strategy testing that this genetic algorithm extends
- [[concepts/byedpi-args-parsing]] - Strategy genes encode ByeDPI CLI args; same argv[0] and -K traps apply
- [[concepts/gene-memory-concurrency-traps]] - Concurrency bugs found in GeneMemory and SavedStrategyStore during audit
- [[concepts/byedpi-strategy-runtime-disconnect]] - Winning args static at start; auto-save mitigates but runtime hot-reload still absent
- [[concepts/granular-probe-fitness-scoring]] - Fitness formula v3: gradient scoring replacing binary 0/1

## Sources

- [[daily/2026-05-12.md]] - Session 14:05: Sprint 5 implementation (StrategyGene + GenePool + EvolutionEngine + GeneMemory); Session 15:08: code review found sequential evaluation requirement, dead settings, SNI→GenePool vocabulary; Session 18:34: audit found GeneMemory race condition, SavedStrategyStore atomicity, importRawJson validation order
- [[daily/2026-05-13.md]] - Chromosome fitness cache, hyperbolic latency fitness formula (linear form dead code), ByeDpiKnownSeeds 75 upstream seeds with pinned priority, population size 25, stagnationThreshold coerceAtLeast(3) CI fix, persistent StrategyFitnessCache TTL 24h, SavedStrategy.lastVerifiedAtMs staleness 7-day threshold + StalenessLabel, auto-save best chromosome, targetFitness 0.85, per-network GeneMemory + StrategyFitnessCache via NetworkProfileDetector + EvolutionResourcesProvider
- [[daily/2026-05-14.md]] - GA v2: popSize 30, maxGen 20, eliteCount 3, targetFitness 0.85, fitness=successRate^1.5*(1-clamp(lat)/3000), initial pop 40/30/30, cache poisoning 0.0 skip, min chromosome length 5, 5 sentinel tests
- [[daily/2026-05-17.md]] - GA v3 fitness: binary→granular computeProbeScore (gradient through connection stages); stagnation boost removed + sentinel; strategies 75→78 (v1.7.5); feat/ga-byedpi-v2 squash→dev
- [[daily/2026-05-18.md]] - Session 11:38/12:06: 0% fitness traced to stale server_fd (600 start/stop cycles); unconditional forceClose fix; singleton sharing between VPN and testing identified; auto-save polluting favorites removed
- [[daily/2026-05-19.md]] - Session 12:46: Thompson Sampling replaces UCB for strategy exploration; `ucbScore` deleted; `GeneMemoryTest` rewritten; sentinel added; `EvolutionEngineTest` decomposed into `EvolutionEngineSentinelTest` + shared helper to fix detekt LargeClass
