---
title: Release postpublish architecture review
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Release postpublish architecture review

## Key Points
- A published release and green CI do not prove that release-regression fixes are architecturally correct.
- If the user stops after release and questions root cause, return to `dev` and review the owning-layer contracts.
- Timeout fixes require special scrutiny because they can either encode a real engine contract or mask wrong readiness criteria.
- Postpublish review findings should not be silently merged into the already published release.

## Details

During the 2026-05-28 `v1.0.3` flow, the user stopped the process after release publication and asked whether the fixes had been reviewed deeply enough. The resulting review separated fixes that were contract-aligned from fixes that only increased waiting time. ByeDPI `stopTimeoutMs()` was treated as owning-layer contract work because the engine has a legitimate two-phase native/proxy drain, while URnetwork needed a stronger readiness signal from SDK `connectionStatus=CONNECTED` instead of only peer count or grid size.

The same review found a sing-box gap outside the first release fix: auto-chain config generation did not share the unsupported-transport validators used by standalone auto-select. The durable workflow is to treat postpublish review as a separate `dev` activity, keep `main` and the already published release untouched unless explicitly instructed, and produce follow-up fixes only when they are backed by concrete evidence.

## Related Concepts
- [[concepts/release-regression-self-review-gate]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/singbox-autochain-validator-parity]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28]] records that after `v1.0.3` the user stopped the process and required architectural review of ByeDPI, URnetwork, and sing-box fixes.
- [[daily/2026-05-28]] records the finding that the release URnetwork change was mostly a timeout increase until SDK `connectionStatus` was added as readiness evidence.
- [[daily/2026-05-28]] records the decision that post-stop review fixes stayed on `dev` and were not part of the already published `v1.0.3`.
