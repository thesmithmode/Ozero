---
title: "Subagent Code Review False Positives"
aliases: [subagent-review-verification, code-review-false-positive, agent-review-trust]
tags: [workflow, code-review, subagents, android, kotlin]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Subagent Code Review False Positives

When a subagent performs a code review and returns findings, those findings must be personally verified against the actual code before accepting or acting on them. In practice, 2 out of 3 findings from a code review subagent were false positives — the agent flagged correct patterns as bugs. Accepting subagent findings blindly wastes time on non-issues and potentially introduces actual regressions by "fixing" correct code.

## Key Points

- Subagent code review findings are hypotheses, not ground truth — always verify against the actual code
- Common false positive patterns: flagging idiomatic coroutine patterns as races, misunderstanding Compose lifecycle guarantees
- `isPinging.clear()` before a new pingJob is a correct pattern — the old job is already cancelled; the new job sets the flag to `true` as its first action
- `LaunchedEffect(key)` auto-cancels previous effect when key changes — this is standard Compose API, not a race
- `Exception().message` being null is a real finding — the null case requires a fallback string

## Details

### The Incident (Session 16:22, 2026-05-26)

A subagent reviewed `SingboxServerListViewModel` and returned 3 findings:
1. `isPinging.clear()` before new pingJob — flagged as a race condition (REJECTED: correct pattern)
2. `LaunchedEffect(key)` key change — flagged as a race (REJECTED: standard Compose guarantees cancellation of previous effect)
3. `Exception().message` can be null — flagged as missing null-safety (ACCEPTED: real issue, fallback `?: "Unknown error"` added)

The 2 rejected findings were false positives caused by the subagent not understanding the invariants of:
- Cooperative coroutine cancellation (old job is cancelled before clear() is called; new job starts after)
- Compose `LaunchedEffect` semantics (key change → automatic cancellation of old coroutine before new one starts)

### Verification Protocol

After receiving subagent review findings:

```
For each finding:
1. Locate the exact code lines mentioned
2. Read the surrounding context (±20 lines)
3. Check if the flagged pattern matches an anti-pattern or a known correct idiom
4. For Compose: verify against Compose API guarantees, not intuition
5. For coroutines: trace the execution order explicitly
6. Only then: accept or reject the finding
```

### Why Subagents Produce False Positives

Code review subagents use heuristic pattern matching — they flag patterns that *look like* races or null issues. They may not have enough context about:
- Framework-level guarantees (Compose LaunchedEffect, coroutine cooperative cancellation)
- Project-specific patterns that are intentionally designed (e.g., isPinging reset before new job)
- The wider lifecycle context that makes a pattern safe

The false positive rate is high enough (2/3 in this session) that blind acceptance would cause harm. The value of subagent reviews is in finding issues that are easy to miss (real null pointer paths, actual concurrency violations) — not in rubber-stamping all findings.

## Related Concepts

- [[concepts/pingJob-viewmodel-cancellation]] - The specific patterns flagged: isPinging.clear() and LaunchedEffect(key) — both verified as correct
- [[concepts/compose-launchedeffect-crash-invisibility]] - LaunchedEffect lifecycle semantics
- [[connections/self-review-insufficient-code-reviewer-required]] - Subagent review complements but does not replace understanding the code

## Sources

- [[daily/2026-05-26.md]] - Session 16:22: subagent returned 3 code review findings for SingboxServerListViewModel; personally verified 2 as false positives (isPinging.clear race, LaunchedEffect race); 1 accepted (Exception.message null fallback); established rule: verify all subagent findings against actual code before accepting
