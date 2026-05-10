# Build Log

## [2026-05-10T21:00:00+03:00] compile | 2026-05-10.md (session 3 — full day verification)
- Source: daily/2026-05-10.md
- Articles created: (none — all already compiled in passes 18:17 and 19:30)
- Articles updated: (none)
- Note: All five concepts from today confirmed present in index and articles: [[concepts/mockk-aar-native-initializer-trap]], [[concepts/kotlin-empty-string-null-coalesce-trap]], [[concepts/viewmodel-polling-runtest-trap]], [[concepts/byedpi-mock-server-ci-fragility]] (Root Cause 3), [[concepts/tun-self-exclusion-sdk-engines]] (SOCKS loop). Session 17:46 GROUP A/B fixes mapped to existing articles. Session 13:00 billing/monetization planning — no technical knowledge to extract.

## [2026-05-10T19:30:00+03:00] compile | 2026-05-10.md (session 2 — GROUP B polling trap)
- Source: daily/2026-05-10.md
- Articles created: [[concepts/viewmodel-polling-runtest-trap]]
- Articles updated: (none)

## [2026-05-10T18:17:00+03:00] compile | 2026-05-10.md
- Source: daily/2026-05-10.md
- Articles created: [[concepts/mockk-aar-native-initializer-trap]], [[concepts/kotlin-empty-string-null-coalesce-trap]]
- Articles updated: [[concepts/byedpi-mock-server-ci-fragility]] (source added — Root Cause 3 injectable probe implemented), [[concepts/tun-self-exclusion-sdk-engines]] (expanded: SOCKS/hev loop mechanism added, renamed to "All VPN Engines")

## [2026-05-05T22:17:00+03:00] compile | 2026-05-05.md
- Source: daily/2026-05-05.md
- Articles created: [[concepts/amneziawg-relinker-loading-trap]], [[concepts/urnetwork-networkspace-bundle-fields]], [[concepts/core-backup-module]], [[concepts/warp-slot-corrupt-json-resilience]]
- Articles updated: [[concepts/urnetwork-sdk-integration]] (env=main + bundle fields, SIGABRT diagnosis added)

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

## [2026-05-05T22:10:00+03:00] compile | 2026-05-02.md
- Source: daily/2026-05-02.md
- Articles created: [[concepts/warp-config-generator-api]], [[concepts/urnetwork-networkspace-init]], [[concepts/ci-job-dependency-masking]], [[concepts/viewmodel-stateflow-test-race]]
- Connections created: [[connections/symptom-fix-vs-system-removal]]
- Articles updated: [[concepts/urnetwork-sdk-integration]] (consent removed, stop() leak fixed, NetworkSpace init), [[concepts/ci-workflow-discipline]] (job dependency masking), [[concepts/vpnservice-builder-traps]] (fd close pattern)
- Excluded as too granular: individual P2/P3 security findings (tracked in AUDIT.md), code review non-critical findings, PortalConnect ≠ WARP (included in warp-config-generator-api context)

## [2026-05-05T23:45:00+03:00] compile | 2026-05-04.md
- Source: daily/2026-05-04.md
- Articles created: [[concepts/amnezia-wg-warp-migration]], [[concepts/gradle-force-vs-catalog]], [[concepts/release-stub-gate]], [[concepts/okhttp5-kotlin-version-constraint]]
- Connections created: [[connections/dependency-override-masking]], [[connections/release-checks-beyond-ci]]
- Articles updated: (none)
- Excluded as not actionable knowledge: specific AWG test counts, ktlint alignment-whitespace specifics, release run number 25310434846, multiple memory flush failures ("Nothing worth saving" or exit code 1)

## [2026-05-10T18:00:00+03:00] compile | 2026-05-08.md
- Source: daily/2026-05-08.md
- Articles created: [[concepts/amneziawg-so-binary-integrity]], [[concepts/amneziawg-jni-classpath-completeness]], [[concepts/gitignore-jnilibs-conflict]], [[concepts/warp-handle-leak-sigabrt]], [[concepts/android-vpn-self-traffic-bypass]], [[concepts/health-monitor-p2p-mismatch]], [[concepts/test-io-thread-zombie-trap]], [[concepts/compose-remember-stale-collectasstate]]
- Articles updated: [[concepts/amnezia-wg-warp-migration]] (Phase 2: Maven→PORTAL_WG SO migration, pre-JNI logging, SHA256 sentinel)
- Connections created: (none new — [[connections/false-positive-engine-status]] extended via 2026-05-07 compile)
- Note: All 8 new concept articles were already written to disk before this log entry was created; this entry retroactively documents the compile

## [2026-05-10T18:30:00+03:00] compile | 2026-05-09.md
- Source: daily/2026-05-09.md
- Articles created: [[concepts/tun-self-exclusion-sdk-engines]], [[concepts/engine-switch-chain-cascading-failures]], [[concepts/codeql-aar-dependency-gap]], [[concepts/byedpi-mock-server-ci-fragility]], [[concepts/vendor-bindsocket-eperm]], [[concepts/dependabot-dev-workflow-mismatch]], [[concepts/vpn-ip-detection-contract]], [[concepts/ip-probe-route-architecture]], [[concepts/urnetwork-window-type-modes]]
- Articles updated: [[concepts/urnetwork-sdk-integration]] (excludeSelf regression + engine-switch chain context), [[concepts/dual-go-runtime-eager-loading]] (GoRuntimeGuard contradiction added), [[concepts/ci-job-dependency-masking]] (2026-05-09 ktlint→test masking example), [[connections/false-positive-engine-status]] (IP warmup cancellation as 4th false-positive vector)
- Connections created: (none new)
- Excluded as too granular: SplitTunnelViewModelTest gap details (testing trivia), specific CI run numbers, memory flush failures

## [2026-05-09T18:28:06+03:00] compile | 2026-05-07.md
- Source: daily/2026-05-07.md
- Articles created: [[concepts/warp-false-connected-no-handshake]]
- Connections created: [[connections/false-positive-engine-status]]
- Articles updated: (none — 3 main concepts already existed from prior 2026-05-08 compile: [[concepts/warp-awg-obfuscation-russian-isps]], [[concepts/android-vpn-traffic-stats]], [[concepts/dual-go-runtime-eager-loading]])
- Excluded as already covered: AwgParams() vs VANILLA confusion (in warp-awg-obfuscation article), CI not triggered on intermediate commits (in same article), git stash unreliability (process trivia), auto-mode failover design mention (insufficient detail), 5 memory flush failures ("Nothing worth saving" or error)
