---
title: FPTN dead server fallback
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# FPTN dead server fallback

## Key Points
- FPTN regression analysis must distinguish a dead first endpoint from a broken engine.
- A single HTTP 608 timeout should not be treated as proof that the full FPTN path is unusable.
- Release checks need a sentinel where FPTN falls back after the first dead server.
- `v0.2.11` is the user-confirmed baseline for comparing FPTN behavior.
- The fallback scenario belongs with runtime release proof, not only static config validation.

## Details
The 2026-05-28 release-regression investigation identified FPTN HTTP 608 timeout as a recurring symptom after the `v0.2.11` baseline. The durable lesson is that an endpoint-level timeout is not the same as engine-level failure. If the first configured server is dead, FPTN should continue through the available fallback path instead of poisoning the engine switch cycle.

This concept extends [[concepts/fptn-http-608-regression-baseline]]: the baseline comparison should check whether current code still handles dead endpoints with the same tolerance as the last user-confirmed working release. It also links to [[concepts/release-runtime-regression-sentinels]], because the required evidence is a scenario-level regression test or CI proof that the engine does not fail permanently on the first bad server.

## Related Concepts
- [[concepts/fptn-http-608-regression-baseline]]
- [[concepts/release-runtime-regression-sentinels]]
- [[concepts/engine-switch-failure-containment]]
- [[connections/engine-switch-regressions-baseline-runtime-proof]]

## Sources
- [[daily/2026-05-28]]: the user named `0.2.11` as the last release where FPTN behaved better and reported current HTTP 608 failures.
- [[daily/2026-05-28]]: the release-sentinel checklist stated that FPTN must not fail on the first dead server.
- [[daily/2026-05-28]]: the investigation separated per-engine failures from the shared poisoned-state switching path.
