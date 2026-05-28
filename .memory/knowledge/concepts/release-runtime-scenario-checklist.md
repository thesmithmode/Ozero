---
title: Release runtime scenario checklist
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Release runtime scenario checklist

## Key Points
- Green CI is not a complete proof for engine runtime scenarios such as URnetwork startup, ByeDPI stop-drain, or sing-box live traffic.
- Before saying a release is ready, each original user-facing regression needs a concrete evidence item.
- Timeout increases must be reviewed against the owning contract to distinguish real engine latency from masked readiness bugs.
- Device-runtime proof should be reported separately from code, test, and CI evidence.

## Details

The release-regression work showed that CI success can coexist with unresolved runtime uncertainty. URnetwork readiness, ByeDPI shutdown behavior, and sing-box config/runtime behavior each required different evidence: SDK status or peer discovery for URnetwork, stop contract timing for ByeDPI, and config validator parity plus native crash evidence for sing-box.

The checklist should be scenario-shaped, not commit-shaped. For each reported regression, record whether the fix is backed by code-path evidence, sentinel tests, CI execution, and device-runtime logs. If a scenario cannot be proven on a device, the release report should state that limitation instead of converting green CI into a stronger claim.

## Related Concepts
- [[concepts/release-regression-evidence-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/singbox-autochain-validator-parity]]

## Sources
- [[daily/2026-05-28]] records the user asking whether URnetwork, ByeDPI, and sing-box regressions were guaranteed not to repeat after green `dev` CI.
- [[daily/2026-05-28]] records the conclusion that CI cannot provide 100% runtime guarantees and that a scenario checklist is required before saying "можно в релиз".
