---
title: Engine switch regressions need baseline and runtime proof
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Engine switch regressions need baseline and runtime proof

## Key Points
- Multi-engine regressions require both a last-good baseline and runtime evidence for transitions after failure states.
- A trace where one engine fails and another later fails does not automatically identify the second engine as root cause.
- The common switch/orchestration layer must be separated from per-engine protocol, registration, and config failures.
- Green CI is insufficient unless it exercises the same transition scenarios that the user observed.

## Details

The 2026-05-28 logs repeatedly tied user-visible breakage to transitions after a failed engine state: ByeDPI stop/start problems were suspected of affecting later WARP/FPTN/URnetwork/sing-box starts. The same daily log also records that WARP transferred bytes in some attempts, so the investigation needed to avoid collapsing all symptoms into a single "WARP broken" conclusion [[daily/2026-05-28.md]].

The non-obvious relationship is between baseline diffing and runtime scenario validation. `v0.2.11` gives a comparison point for what previously worked, but the release diff alone cannot prove that failure recovery is fixed. The observed scenarios require explicit transition evidence: failed ByeDPI followed by WARP/FPTN/URnetwork/sing-box start, sing-box chain validation on the private subscription, and URnetwork startup behavior with live or absent peers [[daily/2026-05-28.md]].

This connection also explains why code review after `v1.0.3` found real issues despite a green release workflow. URnetwork readiness and sing-box auto-chain filtering had contract gaps that CI had not fully represented as runtime scenarios [[daily/2026-05-28.md]].

## Related Concepts
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/release-runtime-scenario-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28.md]]: records the engine-transition symptoms after ByeDPI failure and the need to compare against `v0.2.11`.
- [[daily/2026-05-28.md]]: records the later review finding that green CI did not prove URnetwork and sing-box runtime correctness.
