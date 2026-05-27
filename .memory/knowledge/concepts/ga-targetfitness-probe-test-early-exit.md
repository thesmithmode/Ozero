---
title: "GA ByeDPI: targetFitness Threshold Causes Early-Exit Test Failures"
aliases: [ga-early-exit-test, targetfitness-alwayssucceedprobe, ga-test-assertions]
tags: [testing, ga, byedpi, gotcha]
sources:
  - "daily/2026-05-14 (1).md"
created: 2026-05-27
updated: 2026-05-27
---

# GA ByeDPI: targetFitness Threshold Causes Early-Exit Test Failures

When the genetic algorithm's `targetFitness` threshold is set below the fitness returned by the test probe (`AlwaysSucceedProbe` returns ~1.0), the GA terminates after the first generation instead of running all configured generations. Tests that assert on total evaluation counts (e.g., "6 evals over 2 generations") fail because early termination produces fewer evaluations.

## Key Points

- `AlwaysSucceedProbe` returns fitness ~1.0 — if `targetFitness < 1.0`, GA exits after gen1 when first chromosome exceeds threshold
- When `targetFitness` default changed (e.g., from 1.0 to 0.7), all tests using the default broke simultaneously
- Fix: always use explicit `targetFitness` in tests, set above the probe's fitness (e.g., `targetFitness = 1.01`) to force all generations to run
- Never rely on GA default parameters in tests that assert on iteration counts or evaluation counts
- Parameter sensitivity: GA tests must freeze ALL generation parameters explicitly to isolate the assertion

## Details

### The Incident (2026-05-14)

After commit `73622376` changed the GA default `targetFitness` from 1.0 to 0.7, `EvolutionEngineTest > onChromosomeEval fires for each chromosome` started failing:

- Test expected 6 `onChromosomeEval` callbacks (3 chromosomes × 2 generations)
- Actual: 3 callbacks — only gen1 ran
- Reason: `AlwaysSucceedProbe` gives every chromosome fitness ~1.0 > 0.7 threshold → GA exits after gen1

The fix: `targetFitness = 1.01` in the test constructor. Since no real probe can exceed 1.0 (fitness is a [0..1] score), setting 1.01 forces the GA to run all configured generations without ever triggering early exit.

### Design Implication

Tests asserting on GA iteration behavior (chromosome counts, generation counts, eval callbacks) must explicitly specify `targetFitness` higher than what the test probe returns. The probe's return value and the test's assertion are coupled:

| Probe | Returns | Safe targetFitness in test |
|-------|---------|---------------------------|
| `AlwaysSucceedProbe` | ~1.0 | `1.01` |
| `AlwaysFailProbe` | 0.0 | any (GA runs all gens) |
| Custom deterministic probe | known value | slightly above that value |

### Parameter Change Policy

When changing any GA default parameter (populationSize, maxGen, targetFitness, eliteCount), all tests using implicit defaults must be updated to explicit values. A single default change should never silently break distant tests — use explicit test constructors.

## Related Concepts

- [[concepts/genetic-strategy-evolution]] - GA algorithm design: fitness formula, population seeding, evolution loop
- [[concepts/byedpi-mock-server-ci-fragility]] - Related: mock return value affects probe lifecycle in ByeDPI engine tests; both are "mock behavior affects control flow" traps
- [[concepts/sentinel-protecting-bug-trap]] - Complementary: tests that pass vacuously or on wrong conditions

## Sources

- [[daily/2026-05-14 (1).md]] - Session 18:00+: `EvolutionEngineTest > onChromosomeEval fires for each chromosome` — AssertionFailedError; default targetFitness=0.7, AlwaysSucceedProbe ~1.0 → early exit gen1, 3 evals instead of 6; fix = explicit `targetFitness = 1.01`
