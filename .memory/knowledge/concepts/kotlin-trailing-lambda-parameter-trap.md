---
title: "Kotlin Trailing Lambda Parameter Position Trap"
aliases: [trailing-lambda-trap, trailing-lambda-position, functional-parameter-order]
tags: [kotlin, gotcha, compile-error, api-design]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# Kotlin Trailing Lambda Parameter Position Trap

When a function has multiple functional (lambda) parameters and a new functional parameter is added after an existing one that callers use as a trailing lambda, all existing call sites break at compile time. Kotlin's trailing lambda syntax always attaches to the **last** lambda parameter. Call sites using `foo(args) { ... }` silently begin passing the block to the new last parameter rather than the intended one — if the types happen to match, there is no compile error, just wrong behavior.

## Key Points

- Kotlin trailing lambda syntax attaches the `{ }` block to the **last** functional parameter declared in the function signature
- Adding a new `() -> Unit` parameter after an existing trailing lambda parameter silently re-routes all call sites
- If new and old parameter types are the same (`() -> Unit`), the compiler sees no error — the wrong lambda runs
- Fix for new API: place rarely-used or optional functional parameters before the primary trailing lambda
- Fix for broken call sites: add named parameters (`onGeneration = { ... }`) instead of trailing lambda syntax

## Details

### The Mechanism

In Kotlin, trailing lambda syntax allows placing the last lambda argument outside the parentheses: `foo(a, b) { doWork() }`. The compiler assigns this trailing block to whichever functional parameter is last in the signature. This is unambiguous as long as the function has exactly one functional parameter at the end.

When a function previously had one lambda at the end and a new lambda parameter is inserted before it (or added after it, displacing the old "last" position), all existing call sites using trailing syntax now bind to the new parameter. In the Ozero EvolutionEngine case, `evolve(seeds, onChromosomeEval, onGeneration)` had `onGeneration` added before `onChromosomeEval` during a refactor, then corrected to append `onChromosomeEval` after `onGeneration` — but this made `onChromosomeEval` the new trailing position. All existing tests writing `evolve(seeds) { generation -> }` now passed the block to `onChromosomeEval`.

### The EvolutionEngine Incident (2026-05-13)

`EvolutionEngine.evolve()` previously had signature `evolve(seeds: List<Chromosome>, onGeneration: (List<Pair<Chromosome, Double>>) -> Unit)`. A new `onChromosomeEval: (Chromosome) -> Unit` callback was added. After the addition, the full signature was `evolve(seeds, onGeneration, onChromosomeEval)`. All test code using `evolve(seeds) { result -> }` now silently passed the block to `onChromosomeEval` instead of `onGeneration`. Tests that checked generation results observed empty lists because the `onGeneration` callback was never called — but the compile succeeded because both lambdas have `Unit` return type.

The fix: grep all call sites for `evolve(` and add named parameter syntax: `evolve(seeds, onGeneration = { ... }, onChromosomeEval = { ... })`.

### Prevention Rules

1. **Never append a new functional parameter after an existing trailing lambda** without auditing all call sites
2. **Named parameters at call sites** when a function has 2+ functional parameters
3. **Distinct types when possible**: different functional parameter types (e.g., `(Chromosome) -> Double` vs `(List<Result>) -> Unit`) produce compile errors on type mismatch, making the swap visible
4. **Grep before merge**: `grep -r "evolve(" --include="*.kt"` to find all call sites before renaming/reordering parameters

## Related Concepts

- [[concepts/gene-memory-concurrency-traps]] - Same EvolutionEngine refactoring context; both bugs found during Sprint 5 work
- [[concepts/genetic-strategy-evolution]] - The evolve() function where this trap was encountered
- [[concepts/ci-workflow-discipline]] - CI was the first place the wrong-lambda bug manifested (compile/test failures)

## Sources

- [[daily/2026-05-13.md]] - Session evening GA improvements: `evolve(seeds) {}` trailing lambda matched `onChromosomeEval` not `onGeneration` after parameter was added; fixed by adding named parameters at all call sites; compile errors in tests revealed the mismatch
