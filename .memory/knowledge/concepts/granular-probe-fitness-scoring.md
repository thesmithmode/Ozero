---
title: "Granular Probe Fitness Scoring for GA Strategy Evolution"
aliases: [compute-probe-score, granular-fitness, gradient-fitness-scoring]
tags: [byedpi, genetic-algorithm, architecture, optimization]
sources:
  - "daily/2026-05-17.md"
created: 2026-05-17
updated: 2026-05-17
---

# Granular Probe Fitness Scoring for GA Strategy Evolution

Binary fitness (0 = fail, 1 = success) in the genetic algorithm provided zero gradient signal: a strategy that reached TCP handshake but failed on TLS was scored identically to one that got `NetworkError` immediately. `computeProbeScore` replaces this with a gradient through connection stages: `NetworkError (0.0) < Timeout (0.1) < HTTP error code (0.3-0.6) < full success (1.0)`. This gives the GA mutation/crossover operators a fitness landscape to climb rather than a binary cliff.

## Key Points

- Binary fitness (0/1) produced GA stagnation: all failing strategies scored 0, no selection pressure toward "almost working" strategies
- `computeProbeScore` maps `ProbeResult` sealed class to Float gradient: NetworkError→0.0, Timeout→0.1, HTTP 4xx/5xx→0.3-0.6, full body received→1.0
- `successRate` in `GenerationResult` remains binary (for UI "X% success" display); `avgScore` uses granular values
- `ProbeResult` sealed class has direct subtypes: `Success`, `Failure`, `Timeout` — NOT nested `Failure.NetworkError` (compile error on v1.7.5)
- Sentinel test explicitly forbids the old `stagnationCount * 0.5f` mutation boost — a removed workaround

## Details

### The Stagnation Problem

The previous GA implementation used binary fitness: a probe either succeeded (full HTTP response body received) or failed (anything else = 0). When testing against a TSPU-filtered ISP, most strategies fail — but they fail at different stages:

1. **NetworkError**: socket cannot connect → SOCKS proxy down or ByeDPI crashed
2. **Timeout**: TCP connection established but no HTTP response → DPI blocked the handshake or TLS negotiation
3. **HTTP error**: server responded with 4xx/5xx → DPI passed, server rejected the request
4. **Partial response**: HTTP 200 received but body truncated → TSPU injected RST mid-stream
5. **Full success**: complete response body received

With binary scoring, stages 1-4 all score 0. The GA's tournament selection sees no difference between a strategy that causes NetworkError and one that almost succeeds but gets RST at the last byte. Without selection pressure toward "better failures," the population stagnates at generation 3-5 and the `stagnationCount * 0.5f` mutation boost was added as a workaround — described by the user as "костыль" (crutch).

### The Gradient Solution

`computeProbeScore(result: ProbeResult): Float` assigns scores that create a monotonic gradient through connection stages:

```kotlin
fun computeProbeScore(result: ProbeResult): Float = when (result) {
    is ProbeResult.Success -> 1.0f
    is ProbeResult.Timeout -> 0.1f
    is ProbeResult.Failure -> {
        val code = result.httpCode
        when {
            code == null -> 0.0f           // NetworkError, no HTTP at all
            code in 200..299 -> 0.8f       // Success code but body incomplete
            code in 300..399 -> 0.6f       // Redirect (DPI-injected?)
            code in 400..499 -> 0.4f       // Client error (DPI passed, server rejected)
            code in 500..599 -> 0.3f       // Server error
            else -> 0.1f
        }
    }
}
```

Tournament selection now prefers strategies that reached HTTP 4xx (score 0.4) over strategies that timed out (score 0.1). Crossover between a "reaches HTTP" parent and a "good TLS params" parent can produce offspring that succeeds fully — a gradient the binary scoring could never provide.

### ProbeResult Sealed Class Structure

A CI failure during implementation revealed the correct `ProbeResult` hierarchy:

```kotlin
sealed class ProbeResult {
    data class Success(...) : ProbeResult()
    data class Failure(val httpCode: Int?, val message: String) : ProbeResult()
    data class Timeout(val message: String) : ProbeResult()
}
```

The initial code used `is ProbeResult.Failure.NetworkError` — a non-existent nested subclass. `Failure` with `httpCode = null` represents network errors; `Failure` with `httpCode != null` represents HTTP-level errors. This flat hierarchy is simpler and sufficient.

### Stagnation Workaround Removal

The `stagnationCount * 0.5f` mutation rate boost was removed. A sentinel test now asserts this pattern does not exist in `EvolutionEngine.kt`. With granular scoring, the GA has enough gradient signal to avoid true stagnation (all chromosomes at score 0.0); the remaining "stagnation" (all chromosomes at similar non-zero scores) is natural convergence that increased mutation would only worsen.

## Related Concepts

- [[concepts/genetic-strategy-evolution]] - Parent article for the GA architecture; this article details the fitness formula v3
- [[concepts/byedpi-auto-strategy-testing]] - The fixed-list testing that also uses ProbeResult; granular scoring applies there too
- [[concepts/byedpi-strategy-runtime-disconnect]] - Winning strategy from evolution still static at runtime; scoring improvement doesn't fix the disconnect

## Sources

- [[daily/2026-05-17.md]] - Session 14:41+: GA stagnation called "костыль" by user; computeProbeScore implemented with gradient scoring; ProbeResult.Failure.NetworkError compile error → flat sealed class; stagnation boost removed + sentinel; CI green after sealed class fix
