# Build Log

## [2026-04-29T19:34:07+03:00] compile | 2026-04-29.md (session 1)
- Source: daily/2026-04-29.md
- Articles created: [[concepts/vpn-engine-pipeline]], [[concepts/per-engine-ui]], [[concepts/ci-workflow-discipline]], [[concepts/release-process]], [[concepts/wiki-knowledge-base]]
- Connections created: [[connections/ci-and-release-gating]]
- Articles updated: (none)

## [2026-04-29T20:15:00+03:00] compile | 2026-04-29.md (session 2 — silent crash investigation)
- Source: daily/2026-04-29.md
- Articles created: [[concepts/compose-launchedeffect-crash-invisibility]], [[concepts/hilt-di-native-library-failure]], [[concepts/android-silent-crash-diagnosis]], [[concepts/nubia-rom-permission-enforcement]]
- Connections created: [[connections/invisible-crash-vectors]]
- Articles updated: (none)

## [2026-04-30T12:53:43+03:00] compile | 2026-04-30.md
- Source: daily/2026-04-30.md
- Result: SKIPPED — daily log contains only memory flush entries (all "Nothing worth saving" or flush error), no sessions or extractable knowledge
- Articles created: (none)
- Articles updated: (none)

## [2026-04-30T22:00:00+03:00] compile | 2026-04-30.md (manual notes added)
- Source: daily/2026-04-30.md
- Articles created: [[concepts/v001-dpi-bypass-fix-chain]], [[concepts/tun-mtu-dual-layer]], [[concepts/byedpi-auto-strategy-testing]], [[concepts/libhev-tunnel-stats]], [[concepts/vpnservice-builder-traps]]
- Connections created: [[connections/byedpi-reference-parity]]
- Articles updated: [[concepts/nubia-rom-permission-enforcement]] (added setMetered finding)
- Note: [[concepts/byedpi-args-parsing]] already existed from earlier in this session, not re-created

## [2026-04-30T22:06:14+03:00] compile | 2026-04-30.md (session 22:06 — wiki maintenance)
- Source: daily/2026-04-30.md
- Articles created: (none)
- Articles updated: [[concepts/wiki-knowledge-base]] (added flush hook unreliability, compact-before-clear ordering, operational lessons)

## [2026-05-01T20:29:59+03:00] compile | 2026-04-30.md (re-verification)
- Source: daily/2026-04-30.md
- Result: ALREADY COMPILED — all content from daily log covered by 5 prior compile passes (12:53, 22:00, 22:06 on 2026-04-30)
- Articles created: (none)
- Articles updated: (none)
- Note: Session 22:06 "API 400 advisor_tool_result" detail is a Claude Code platform bug, not project knowledge — excluded

## [2026-05-01T22:00:00+03:00] compile | 2026-05-01.md
- Source: daily/2026-05-01.md
- Articles created: [[concepts/robolectric-room-migration-testing]], [[concepts/vpnservice-main-thread-preload]], [[concepts/gomobile-bind-gotchas]], [[concepts/urnetwork-sdk-integration]]
- Connections created: [[connections/native-aar-build-convergence]]
- Articles updated: [[concepts/wiki-knowledge-base]] (added 2026-05-01 flush failures — 3/3 failed, pattern confirmed across 3 days)

## [2026-05-01T21:55:39+03:00] compile | 2026-05-01.md (session 21:55 — latent test discovery)
- Source: daily/2026-05-01.md
- Articles created: [[concepts/junit-platform-silent-skip]], [[concepts/gradle-continue-full-failures]]
- Connections created: [[connections/ci-false-green-vectors]]
- Articles updated: [[concepts/ci-workflow-discipline]] (added --continue + N>0 tests rules from useJUnitPlatform incident)

## [2026-05-01T23:30:00+03:00] compile | 2026-05-01.md (verification pass)
- Source: daily/2026-05-01.md
- Result: ALREADY COMPILED — all content covered by 2 prior compile passes (22:00, 21:55 on 2026-05-01)
- Articles created: (none)
- Articles updated: (none)
- Index fix: [[concepts/wiki-knowledge-base]] date corrected 2026-04-30 → 2026-05-01, added daily/2026-05-01.md source
- Excluded as too granular for standalone articles: HealthMonitor DEGRADED badge (W7.1 UI commit), LanguageSection RU/EN restriction (W9.1), WARP timeout 15→30s, v0.0.2-2 CI+Release green confirmation
