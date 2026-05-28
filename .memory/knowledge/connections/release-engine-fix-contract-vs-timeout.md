---
title: Release engine fix contract vs timeout
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Release engine fix contract vs timeout

## Summary
Release-regression fixes around engine startup must distinguish contract signals from timeout padding. ByeDPI needed an engine-owned stop timeout, while URnetwork needed SDK connection status instead of a longer peer-count wait.

## Key Points
- Both ByeDPI and URnetwork initially looked like timeout problems.
- ByeDPI timeout was accepted because it matched a known two-phase stop contract.
- URnetwork timeout-only was rejected as incomplete because readiness was using the wrong signal.
- Self-review must classify each timeout change as either contract implementation or symptom masking.

## Details
The 2026-05-28 release cycle produced a useful contrast. ByeDPI needed more than the generic orchestrator stop timeout because the engine legitimately drains native/proxy state in phases. That made `EnginePlugin.stopTimeoutMs()` a contract expression at the owning layer.

URnetwork was different. Increasing `awaitReady` to 5 minutes matched user expectations for peer discovery, but the architectural review found the true readiness gap: Ozero was relying on grid peer count while the reference app treats `connectionStatus=CONNECTED` as the connection signal. This connection ties [[concepts/byedpi-stop-timeout-contract]] to [[concepts/urnetwork-readiness-connectionstatus]] and reinforces [[concepts/release-regression-evidence-checklist]].

## Related Concepts
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/release-regression-evidence-checklist]]

## Sources
- [[daily/2026-05-28]]: ByeDPI timeout was accepted after matching it to two-phase stop behavior.
- [[daily/2026-05-28]]: URnetwork timeout-only was later judged insufficient because readiness ignored SDK `connectionStatus`.
- [[daily/2026-05-28]]: User required architecture review to avoid treating green CI as proof of correct fixes.
