---
title: Singbox subscription coverage should target parser branch edges
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-04
---
# Singbox subscription coverage should target parser branch edges
## Key Points
- `:singbox-subscription` failed `jacocoTestCoverageVerification` on branch coverage rather than on compile or style checks.
- The reported gap was closed by adding tests around `ClashYamlParser` and `RawShareLinksParser`.
- The useful cases were malformed YAML roots, fallback transport branches, invalid share links, and invalid JSON inputs.
- Coverage work should target real production branches, not artificial metric padding.
## Details
The daily log shows a coverage gate failure in `:singbox-subscription` where the remaining work was not to change production behavior, but to exercise the untested parser branches. That meant focusing on the fallback and malformed-input paths that production code rarely reaches during happy-path testing.

The test additions were intentionally narrow: they aimed at the parser edges that lowered branch coverage and made the verification job red. This is a useful pattern for coverage recovery because it closes the gate with observable production behavior rather than weakening the threshold. The concept sits alongside [[ci-coverage-gate-artifact-trust-contract]] and [[release-runtime-scenario-checklist]].
## Related Concepts
- [[ci-coverage-gate-artifact-trust-contract]]
- [[release-runtime-scenario-checklist]]
- [[singbox-subscription-architecture]]
- [[ci-artifact-driven-extra-module-debugging]]
## Sources
- `daily/2026-06-03.md`: noted a `:singbox-subscription:jacocoTestCoverageVerification` failure and the addition of edge-case tests for `ClashYamlParser` and `RawShareLinksParser`.
