---
title: "Genetic Algorithm for ByeDPI Strategy Optimization"
aliases: [strategy-evolution, gene-pool, evolution-engine, strategy-gene]
tags: [byedpi, architecture, genetic-algorithm, optimization]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# Genetic Algorithm for ByeDPI Strategy Optimization

Ozero Sprint 5 introduced a genetic algorithm layer on top of ByeDPI's auto-strategy testing. Instead of iterating over a fixed list of 75 strategies, `EvolutionEngine` evolves strategy parameters (ByeDPI CLI args) through mutation and crossover, using `GeneMemory` to persist fitness scores across sessions. The key architectural constraint: chromosomes must be evaluated sequentially because the ByeDPI engine cannot run multiple instances in parallel.

## Key Points

- `StrategyGene` encodes ByeDPI CLI args as a chromosome; `GenePool` provides mutation/crossover operators and vocabulary from domain presets
- `EvolutionEngine.evaluatePopulation` runs sequential evaluation — parallel engine starts are invalid (single SOCKS5 port, single TUN fd)
- `GeneMemory` persists fitness scores to disk as JSON; `importRawJson` must validate BEFORE writing to prevent corrupt file on bad input
- SNI domains from domain list presets feed into `GenePool` as vocabulary for the `-H` flag gene
- `SavedStrategyStore` allows users to bookmark winning strategies from either the fixed-list test or the evolutionary search

## Details

### Architecture

The genetic algorithm builds on the existing auto-strategy testing infrastructure ([[concepts/byedpi-auto-strategy-testing]]). The fixed 75-strategy list serves as the initial population seed. `EvolutionEngine` then:

1. Evaluates each chromosome by starting ByeDPI with the encoded args, probing target sites through SOCKS5, and computing a fitness score (success percentage)
2. Selects top performers via tournament selection
3. Applies crossover (combining arg fragments from two parents) and mutation (randomly changing flag values)
4. Persists fitness scores to `GeneMemory` for cross-session learning

The sequential evaluation constraint was discovered during code review: `List.map { suspend {} }` does not provide parallelism — `coroutineScope { map { async {} }.awaitAll() }` is needed for parallel execution. However, even with correct coroutine usage, parallel evaluation is architecturally invalid because ByeDPI binds to a single SOCKS5 port and requires exclusive access to the TUN fd.

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

## Related Concepts

- [[concepts/byedpi-auto-strategy-testing]] - The fixed-list strategy testing that this genetic algorithm extends
- [[concepts/byedpi-args-parsing]] - Strategy genes encode ByeDPI CLI args; same argv[0] and -K traps apply
- [[concepts/gene-memory-concurrency-traps]] - Concurrency bugs found in GeneMemory and SavedStrategyStore during audit

## Sources

- [[daily/2026-05-12.md]] - Session 14:05: Sprint 5 implementation (StrategyGene + GenePool + EvolutionEngine + GeneMemory); Session 15:08: code review found sequential evaluation requirement, dead settings, SNI→GenePool vocabulary; Session 18:34: audit found GeneMemory race condition, SavedStrategyStore atomicity, importRawJson validation order
