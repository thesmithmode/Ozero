---
title: Release regression fixes need self-review before main
sources:
  - [[daily/2026-05-28]]
created: 2026-05-31
updated: 2026-05-31
---

# Release regression fixes need self-review before main

## Summary

Engine regression fixes should pass an explicit self-review checklist before `dev` is merged into `main`, because green CI and a successful release workflow do not prove that timeout-looking fixes are architecturally correct.

## Key Points

- A user stop after `v1.0.3` showed that CI success had not proven the ByeDPI, URnetwork, and sing-box fixes were root-cause fixes.
- The review separated owning-layer fixes, such as `EnginePlugin.stopTimeoutMs()`, from timeout padding that can mask wrong readiness criteria.
- URnetwork was reclassified from "wait longer" to "use SDK `connectionStatus=CONNECTED` as readiness evidence".
- sing-box auto-chain was reviewed against auto-select to ensure both paths share unsupported-transport filtering.
- This complements [[concepts/release-regression-self-review-gate]] and [[concepts/release-runtime-scenario-checklist]].

## Details

The May 28 release flow reached a green `dev` CI and published `v1.0.3`, but the user stopped the process and asked whether the fixes had been reviewed as architecture, not patches. That changed the acceptance bar: the team returned to `dev`, reviewed each engine fix, and treated "CI is green" as delivery evidence rather than proof that the regression root cause was closed.

The review found mixed outcomes. ByeDPI's longer stop timeout was accepted as an owning-layer contract because the engine has a legitimate two-phase drain window. URnetwork's release fix was not sufficient because it mostly increased `awaitReady`; the correct readiness signal had to include SDK `connectionStatus=CONNECTED`. sing-box's unsupported VLESS flow fix was valid, but auto-chain still needed the same supported-transport filtering as auto-select.

## Related Concepts

- [[concepts/release-regression-self-review-gate]]
- [[concepts/release-runtime-scenario-checklist]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/singbox-autochain-validator-parity]]

## Sources

- [[daily/2026-05-28]]: Sessions 16:54, 17:20, 17:22, and 17:30 recorded the post-release stop, the self-review, and the distinction between green CI and architectural correctness.
- [[daily/2026-05-28]]: The URnetwork and sing-box review findings were explicitly tied to SDK readiness and shared validator parity.
