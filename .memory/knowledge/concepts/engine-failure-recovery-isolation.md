---
title: Engine failure recovery isolation
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Engine failure recovery isolation

## Key Points
- Failure of one engine must not poison later starts of other engines.
- Regressions after ByeDPI failure must be checked as engine-switch recovery issues, not only as ByeDPI bugs.
- WARP, FPTN, URnetwork, and sing-box need verification after a failed engine transition.
- Runtime release readiness requires testing transition paths after failure, not only single-engine happy paths.

## Details

The 2026-05-28 release investigation recorded a recurring symptom: after ByeDPI failed, WARP could also fail to start, and the user suspected a broader switching-mechanism problem. This makes the recovery path part of the engine contract. A failed engine stop/start must release native state, proxy state, sockets, and orchestrator locks so that the next engine receives a clean lifecycle.

This concept connects direct engine fixes to transition-level validation. [[concepts/byedpi-stop-timeout-contract]] and [[concepts/warp-uapi-cleanup-all-sockets]] address concrete causes, but they are insufficient unless the release checklist verifies that other engines can start after a failed ByeDPI attempt. The broader risk belongs with [[concepts/engine-switch-chain-cascading-failures]] and [[concepts/release-runtime-scenario-checklist]].

## Related Concepts
- [[concepts/engine-switch-chain-cascading-failures]]
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/warp-uapi-cleanup-all-sockets]]
- [[concepts/release-runtime-scenario-checklist]]

## Sources
- [[daily/2026-05-28]]: пользователь сообщил, что после ошибки ByeDPI не запускается даже WARP.
- [[daily/2026-05-28]]: пользователь указал на вероятную архитектурную проблему переключения между движками.
- [[daily/2026-05-28]]: action items включили проверку recovery path после падения ByeDPI и влияния на WARP/FPTN/URnetwork/sing-box.
