---
title: Multi-engine stabilization requires CI-visible review loop
sources:
  - daily/2026-05-31.md
created: 2026-06-13
updated: 2026-06-13
---

# Multi-engine stabilization requires CI-visible review loop

## Summary
Large Ozero stabilization work succeeds only when engine fixes, code review, visible `dev` CI, and runtime proof are treated as one loop rather than separate finish lines.

## Key Points
- Multi-engine fixes must be split by owning layer, but integration remains orchestrator-owned because shared lifecycle and UI contracts can regress across engine boundaries.
- A green manual CI run is not enough; `dev` pushes need visible full CI with N>0 evidence on the pushed SHA.
- Code review findings are not advisory noise when they map to real contracts such as ByeDPI argv grammar, sing-box routed proof, or restart debounce semantics.
- Local Gradle/test/lint execution is not the fallback in Ozero; CI artifacts, workflow files, git history, and static contract review are the fallback evidence path.
- Runtime proof must stay separate from CI delivery proof for WARP, sing-box, FPTN, URnetwork, MasterDNS, and ByeDPI.

## Details

The 2026-05-31 work began as a broad package of independent symptoms: ByeDPI strategy scan crashes, exit-IP confusion, sing-box false readiness, MasterDNS deploy failures, split-tunnel refresh gaps, URnetwork best-available startup, WARP leaks, and restart observer regressions. The workstreams could be analyzed independently, but the final integration required a single review loop because fixes touched shared lifecycle, routing proof, UI state, and CI workflow behavior.

The non-obvious connection is that CI visibility and code review are part of the stabilization mechanism, not post-work ceremony. Manual `workflow_dispatch` produced a weaker user-visible signal than push-associated full CI. Codex review then found real defects in argv validation, restart coalescing, sing-box latency probing, timeout sizing, quoted SNI handling, exit-IP behavior, and readiness proof. Those findings tied directly back to runtime contracts and would not be closed by a generic green badge.

The same loop constrained debugging. Local tests and Gradle tasks were forbidden, so red CI recovery depended on run IDs, artifacts, snapshots, workflow inspection, memory knowledge, and static review. When GitHub access was unreliable, repeated broad searches became low-value; the productive path was to anchor to confirmed failing modules or contract risks before making minimal fixes.

## Related Concepts
- [[concepts/byedpi-strategy-scan-isolated-structured-argv]]
- [[concepts/singbox-routed-probe-readiness-latency-contract]]
- [[concepts/dev-push-ci-visible-full-run-contract]]
- [[concepts/local-gradle-validation-ban-ci-only]]
- [[connections/runtime-provider-debounce-replay-loop]]
- [[connections/fail-closed-security-ci-trust-boundary]]

## Sources
- [[daily/2026-05-31]]: sessions 11:32 through 12:27 decompose the multi-engine stabilization scope and record the ByeDPI, sing-box, MasterDNS, split-tunnel, exit-IP, and URnetwork contracts.
- [[daily/2026-05-31]]: sessions 13:07 through 18:04 record push CI visibility, N>0 gates, Codex review findings, and fixes for real review defects.
- [[daily/2026-05-31]]: sessions 19:29 through 20:03 record red-CI diagnosis under a local-test ban and unavailable GitHub logs.
- [[daily/2026-05-31]]: sessions 20:48 through 23:44 connect fail-closed review, intentional backup-key documentation, runtime restart provider boundaries, and WARP reference comparison.
