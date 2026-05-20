# Build Log

## [2026-05-20T20:00:00+03:00] compile | Daily Log 2026-05-20
- Source: daily/2026-05-20.md
- Articles created:
  - [[concepts/urnetwork-filteredlocations-bestmatches]] — FilteredLocations 5-list structure; bestMatches field was ignored → empty search results; fix: allBestMatches VM + "Лучшие совпадения" section
  - [[concepts/urnetwork-balance-accumulation-mechanism]] — server INSERT not UPDATE; 30h duration vs 24h cron → 6h overlap → 68-102 GiB sum; not client bug; UX fix: cap at 34 GiB
  - [[concepts/prod-log-infra-redaction]] — three boot.log leak categories: WARP mirror URLs, Solana wallet prefix, dev-language instructions; all three redacted
- Articles updated:
  - [[concepts/byedpi-args-parsing]] — commit 7cc2348f regression: Kotlin prepended "ciadpi" without checking native-lib.c:85 already sets argv[0]="byedpi"; result: IDLE state; sentinel rule added
  - [[concepts/warp-uapi-handshake-polling]] — legacy socket path bug: WarpUapi.readState used ozero-warp.sock → null stats → false DEGRADED; fix: findUapiSocket() cascade through sockets/ subdir + listFiles; WarpSocketDiagnostics added
  - [[concepts/urnetwork-sdk-integration]] — runStartOnMain symmetry fix: both paths must apply all 12 device fields; previous fix only covered ensureDeviceOnMain (settings path); engine.start() path stayed at 1 field → SDK hid cities/regions
- Index: 3 rows added, 3 rows updated, total 150
- Sessions covered: 13:00 (FilteredLocations bestMatches, balance 102GiB server source), 15:59 (boot.log security audit), post-compact (ByeDPI argv[0] regression 7cc2348f, WARP sockets/ path bug), 00:48/v0.1.9 (URnetwork runStartOnMain symmetry)

## [2026-05-20T17:30:00+03:00] compile | Daily Log 2026-05-19
- Source: daily/2026-05-19.md
- Articles created:
  - [[concepts/warp-awg-handle-zero-valid]] — AWG handle=0 valid first slot; handle<0 correct guard; handle<=0 misdiagnosis reverted
  - [[concepts/byedpi-hev-pipeline-upstream-parity]] — HevTunnelConfig YAML extra fields + IPv6 blackhole removal for ByeDPI upstream parity
  - [[concepts/engine-status-bar-symmetric-columns]] — IpInfoCard 50/50 split; isUrnetworkVisibleInMain auto-mode fix; i18n keys
- Articles updated:
  - [[concepts/warp-false-connected-no-handshake]] — added 5s→10s timeout revert + handle=0 misdiagnosis context
  - [[concepts/urnetwork-location-stability-privacy-icons]] — APK investigation confirmed Material Icons are correct reference
- Index: 4 rows added, 1 row updated, total 148

## [2026-05-20T12:00:00+03:00] compile | Daily Log 2026-05-18 (pass 8 — uncovered late sessions 23:41 and 23:57)
- Source: daily/2026-05-18.md
- Articles created:
  - [[concepts/urnetwork-location-stability-privacy-icons]] — ConnectLocation.stable/strongPrivacy fields; Warning/Lock icons; OzeroPalette mapping; inner class name shadowing trap; ktlint in test files
- Articles updated:
  - [[concepts/warp-uapi-handshake-polling]] — soTimeout 500ms→50ms (correctness), WARP_READY_POLL_MS 300ms→100ms (optimization); detection lag max 800ms→150ms; sources updated
- Index: 1 row added (urnetwork-location-stability-privacy-icons), 1 row updated (warp-uapi-handshake-polling), total 144
- Sessions covered (incremental over pass 7): 23:41 (WARP UAPI detection latency optimization), 23:57 (URnetwork location picker stable/strongPrivacy icons + inner class shadowing + ktlint in tests)
- Excluded: wiki-query session (read-only, no new knowledge), memory flush errors (infra noise), CI run IDs (ephemeral)

## [2026-05-19T20:00:00+03:00] compile | Daily Log 2026-05-19 (pass 2 — gap fill)
- Source: daily/2026-05-19.md
- Articles created: [[concepts/speed-chart-bucket-alignment]] (time-aligned bucket IDs prevent sliding-window drift; SpeedSample typed wrapper), [[concepts/vpn-slot-coexistence-crash]] (establish() null = external VPN holds slot; Failed state + logActiveExternalVpn), [[concepts/urnetwork-filter-locations-trigger]] (filterLocations("") required after vc.start()), [[concepts/hilt-viewmodel-split-too-many-functions]] (hiltViewModel() decomp for TooManyFunctions; shared internal fakes)
- Note: urnetwork-balance-optimistic-cache + hiltviewmodel-toomanyfunctions-decomposition + vpn-slot-conflict-detection + urnetwork-filterlocations-trigger already existed from pass 1; new files are extended/renamed variants — index refs pass-1 canonical names
- Articles updated: [[concepts/chart-nice-max-dynamic-scaling]] (bucket alignment fix + SpeedSample + speed-chart-bucket-alignment cross-ref), [[concepts/byedpi-jni-guard-hardening]] (__android_log_print checkpoints; cold-start 5s+ vs 14ms warm; hang root cause pending)
- Index updated: 1 row added (speed-chart-bucket-alignment), total 143

## [2026-05-19T19:00:00+03:00] compile | Daily Log 2026-05-19 (pass 1)
- Source: daily/2026-05-19.md
- Articles created: [[concepts/hiltviewmodel-toomanyfunctions-decomposition]], [[concepts/vpn-slot-conflict-detection]], [[concepts/urnetwork-filterlocations-trigger]], [[concepts/urnetwork-balance-optimistic-cache]]
- Articles updated: [[concepts/viewmodel-polling-runtest-trap]] (extended rule: ANY advanceUntilIdle + active collectJob = deadlock, not just while-true; diagnostic grep pattern added), [[concepts/warp-false-connected-no-handshake]] (WARP_READY 10s→5s, SWITCHING 12s→6s; awaitEngineReady Boolean; timeout→handleEngineFailure), [[concepts/genetic-strategy-evolution]] (Thompson Sampling replaces UCB; EvolutionEngineTest decomposed)
- Index updated: 4 rows added, 3 rows updated, total 142
- Sessions covered: 00:06 (icons research — Material Icons in URnetwork APK confirmed), 00:51 (CI fix + chart bucketize), 10:30/10:56 (v0.1.3 bugs list), CI refactor (VM split), 12:46 (Thompson Sampling + EvolutionEngineTest decomp), 13:53 (advanceUntilIdle deadlock extended rule + probingLabelRes fix + v0.1.4 tag), v0.1.5 WARP/switch speedup + ByeDPI native diag, 15:13/16:23 (URnetwork traffic/cache/filterLocations/coexistence), 17:40/17:47 (open bugs + KB consultation)
- Open bugs (not yet fixed): ByeDPI not passing real traffic, traffic 2× overcount, traffic badge missing at VPN start, WARP device verification pending, expert mode status bar symmetric layout

## [2026-05-18T23:30:00+03:00] compile | Daily Log 2026-05-18 (pass 7 — late sessions)
- Source: daily/2026-05-18.md
- Articles created: (none)
- Articles updated: [[concepts/urnetwork-fixed-ip-enhanced-anonymization]] (relocate from MainScreen to EngineSettings: dual-VM ownership eliminated, peer count → IpInfoCard, 11 tests removed + 4 added, sentinel inverted, commit 87ff2d47)
- Index updated: 1 row summary updated, total 138
- Sessions covered (incremental): 21:25 (memory push discipline — feedback memory only), ~22:00 (URnetwork toggles relocate: MainScreen→EngineSettings, single source of truth, +205/−430), 22:36 (CI watcher)
- Note: Sessions 21:25 and ~22:00 were after pass 6 verification cutoff

## [2026-05-19T10:00:00+03:00] compile | Daily Log 2026-05-18 (pass 6 — verification)
- Source: daily/2026-05-18.md
- Articles created: (none)
- Articles updated: (none)
- Verification: all 15 articles referencing daily/2026-05-18.md confirmed present and complete across 5 prior compile passes. 138 index entries. All sessions covered: 10:41 (release sentinel ABI), 11:38-12:06 (ByeDPI stale fd + 0% fitness), 13:08-13:41 (URnetwork features + CI), 14:21-14:45 (killswitch audit), 15:02-15:52 (locations + reconnect), 16:09-18:55 (Fixed IP + Enhanced Anonymization), 16:59 (5-reviewer code review), 17:51 (changelog + relay JWT), 18:25-19:07 (walletAuth research), 19:30 (walletAuth implementation), 19:55 (autonomous fix cycle). No remaining gaps.

## [2026-05-19T09:00:00+03:00] compile | Daily Log 2026-05-18 (pass 5 — gap fill)
- Source: daily/2026-05-18.md
- Articles created: [[concepts/urnetwork-location-hierarchy-migration]] (setPreferredCountry→setPreferredLocation migration, findBestMatch city-by-countryCode guard, UrnetworkLocationSelection data class consolidation)
- Articles updated: (none)
- Index updated: 1 new row, total 138
- Sessions covered (incremental): 13:08 (6 selectedCountry/Region/City → data class), 15:02 (Bridge API migration, findBestMatch helper), 16:59 (code review finding: city-by-name without countryCode), 19:55 (fix commit ff7f5044)
- Note: All other sessions already covered by passes 1-4. This pass closes the last content gap: location hierarchy migration was referenced in multiple sessions but had no standalone article.

## [2026-05-19T05:00:00+03:00] compile | Daily Log 2026-05-18 (incremental pass 4 — walletAuth)
- Source: daily/2026-05-18.md
- Articles created: [[concepts/urnetwork-walletauth-per-device-registration]] (per-device Ed25519 auto-registration, AES-GCM keypair storage, server protocol, migration flow, 40 tests)
- Articles updated: [[concepts/urnetwork-guest-mode-relay-blocker]] (Resolution Options: Option 2 implemented, commit 0ef16e3a), [[concepts/urnetwork-relay-always]] (walletAuth resolution section + related concepts)
- Index updated: 1 new row, total 137
- Sessions covered (incremental): 18:25 (walletAuth research), 18:52 (protocol details), 19:07 (review fixes + memory), 19:30 (full implementation — Base58, DeviceIdentity, AuthService, migration, 40 tests, commit 0ef16e3a)

## [2026-05-19T04:15:00+03:00] compile | Daily Log 2026-05-18 (incremental pass 3)
- Source: daily/2026-05-18.md
- Articles created: [[concepts/extension-function-import-migration-trap]] (interface→extension breaks consumers without import), [[concepts/poll-flow-resilience-pattern]] (runCatching + last-value fallback for poll flows)
- Articles updated: [[connections/self-review-insufficient-code-reviewer-required]] (added 2026-05-18 evidence: 5-reviewer autonomous fix cycle, ~25 findings, 4 commits)
- Index updated: 2 new rows, 1 row updated, total 136
- Sessions covered (incremental): 16:59 (5-agent code review details), 17:51 (extension import trap), 19:55 (autonomous fix cycle with poll-flow resilience)

## [2026-05-19T03:30:00+03:00] compile | Daily Log 2026-05-18 (incremental pass 2)
- Source: daily/2026-05-18.md
- Articles created: [[concepts/urnetwork-runtime-release-lifecycle]] (Go-runtime singleton release after stop; cross-app conflict with URnetwork-app)
- Articles updated: [[concepts/chart-nice-max-dynamic-scaling]] (M1 60s baseline + bucket aggregation for M5/M30/H1), [[connections/go-runtime-conflict-resolution-evolution]] (added phase 4: explicit URnetwork runtime release)
- Index updated: 1 new row, 1 row updated, total 134
- Sessions covered (incremental): 18:52 (URnetwork runtime release + chart timeframes + walletAuth research)

## [2026-05-18T22:50:00+03:00] compile | Daily Log 2026-05-18
- Source: daily/2026-05-18.md
- Articles created: [[concepts/byedpi-stale-serverfd-unconditional-forceclose]], [[concepts/urnetwork-guest-mode-relay-blocker]], [[concepts/killswitch-binder-death-detection]], [[concepts/urnetwork-fixed-ip-enhanced-anonymization]], [[concepts/byedpi-singleton-strategy-testing-isolation]]
- Articles updated: [[concepts/urnetwork-relay-always]] (JWT bootstrap requirement + guest mode monetization blocker), [[concepts/genetic-strategy-evolution]] (stale server_fd as root cause of 0% fitness + singleton sharing + favorites pollution)
- Index updated: 5 new rows, 2 rows updated, total 133
- Sessions covered: 10:41 (release sentinel ABI fix), 11:38-12:06 (ByeDPI stale fd + 0% fitness + favorites pollution), 13:08-13:26 (URnetwork feature parity + CI fixes), 13:41 (balance card + anonymization toggle), 14:21-14:45 (killswitch audit P30-P37), 15:02-15:52 (URnetwork locations + reconnect indicator), 16:09-16:30 (Fixed IP + Enhanced Anonymization), 16:59 (5-agent code review), 18:25 (guest mode monetization investigation), 18:55-19:55 (autonomous fix cycle + toggles completion)

## [2026-05-19T01:30:00+03:00] compile | Daily Log 2026-05-17
- Source: daily/2026-05-17.md
- Articles created: [[concepts/sentinel-refactor-batch-audit]], [[concepts/stateflow-waitfor-zero-test-race]], [[concepts/granular-probe-fitness-scoring]], [[concepts/relay-coordinator-ownership-transfer]]
- Articles updated: [[concepts/urnetwork-relay-always]] (implementation: relayOwned ownership, bridge idempotency, relay not working discovery), [[concepts/genetic-strategy-evolution]] (GA v3 granular scoring, strategies 75→78, stagnation boost removed)
- Index updated: 4 new rows, 2 rows updated, total 128

## [2026-05-18T22:15:00+03:00] compile | Daily Log 2026-05-16
- Source: daily/2026-05-16.md
- Articles created: [[concepts/yaml-biginteger-parsing-trap]], [[concepts/filechannel-lock-posix-per-process]], [[concepts/vpnservice-god-object-decomposition]], [[concepts/sentinel-anchor-substringafter-trap]], [[concepts/collect-vs-collectlatest-restart-semantics]], [[concepts/kotlin-lazy-cross-reference-type-inference]], [[connections/sentinel-trap-family]]
- Articles updated: [[concepts/suppress-annotation-decomposition]] (ExpertMainContent badges extraction example)
- Index updated: 7 new rows, 1 row updated, total 124

## [2026-05-18T18:26:57+03:00] compile | Daily Log 2026-05-15
- Source: daily/2026-05-15.md
- Articles created: [[concepts/extract-native-libs-legacy-packaging]], [[concepts/byedpi-jni-guard-hardening]], [[concepts/modular-boundary-engine-specific-logic]], [[connections/self-review-insufficient-code-reviewer-required]]
- Articles updated: [[concepts/tun-self-exclusion-sdk-engines]] (third regression: modular boundary violation from excludeSelf conditional), [[concepts/sentinel-protecting-bug-trap]] (new example: excludeSelf sentinels guarding broken behavior)
- Index updated: 4 new rows, 2 rows updated, total 117

## [2026-05-15T03:00:00] compile | Daily Log 2026-05-14 (pass 7 — final)
- Source: daily/2026-05-14.md
- Articles updated: [[concepts/urnetwork-relay-always]] (Session 21:29: setupPayoutWallet auto-bind implementation + 3 contract tests, replaced PRESET_WALLET dead code note)
- Index updated: 1 row updated, total 113
- Verification: all sessions 10:00–21:29 now covered; no remaining gaps

## [2026-05-15T02:00:00] compile | Daily Log 2026-05-14 (pass 6 — post-compaction)
- Source: daily/2026-05-14.md
- Articles created: [[concepts/urnetwork-relay-always]] (session 20:30 — relay-always architecture, UrnetworkRelayCoordinator, SDK tunnelStarted/providePaused decoupling, PRESET_WALLET dead code, monetization constraints)
- Articles updated: [[concepts/byedpi-mock-server-ci-fragility]] (RC4 extended: added probe-before-isActive production ordering fix, commit b61550bc; existing test fix documented as Problem B)
- Index updated: 1 new row, total 113

## [2026-05-15T01:00:00] compile | Daily Log 2026-05-14 (pass 5 — verification)
- Source: daily/2026-05-14.md
- Articles created: none (all created in passes 1-4)
- Articles updated: none (all updated in passes 1-4)
- Verification: confirmed all 11 "existing" articles present on disk and indexed; confirmed 5 target articles (byedpi-mock-server-ci-fragility, genetic-strategy-evolution, ci-workflow-discipline, go-runtime-process-isolation, warp-false-connected-no-handshake) contain 2026-05-14.md content; index has 112 rows covering all sessions

## [2026-05-15T00:30:00] compile | Daily Log 2026-05-14 (pass 4)
- Source: daily/2026-05-14.md
- Articles created: [[concepts/engine-telegram-mtproxy]]
- Articles updated: [[concepts/native-binary-auto-update-pipeline]] (binaries.lock.yaml + regen_lock.py + ProcessBuilder pattern), [[concepts/ci-workflow-discipline]] (new module must be explicitly in CI test job)
- Index updated: 1 new row

## [2026-05-14T23:59:00] compile | Daily Log 2026-05-14
- Source: daily/2026-05-14.md
- Articles created: [[concepts/shell-mock-positional-arg-trap]]
- Articles updated: [[concepts/runtest-uncompleted-coroutines-trap]] (TelegramProxyCoordinator + DataStore scope cases), [[concepts/robolectric-hilt-eager-init-trap]] (TelegramProxyService lazy fix), [[concepts/byedpi-mock-server-ci-fragility]] (root cause 4: mock returns 0 instantly), [[concepts/github-draft-release-visibility]] (3 draft releases fixed), [[concepts/combined-aidl-race-elimination]] (WARP H3/H4 fixes), [[concepts/go-runtime-process-isolation]] (combined AIDL + adoptFd), [[concepts/engine-await-ready-pattern]] (URnetwork peerCount + WARP UAPI), [[concepts/warp-uapi-handshake-polling]] (LocalSocket implementation), [[concepts/warp-false-connected-no-handshake]] (awaitReady integration), [[concepts/byedpi-native-thread-join-race]] (second join after forceClose), [[concepts/android-foreground-service-long-operation]] (StrategyScanService), [[connections/engine-readiness-vs-false-connected]] (awaitReady unification), [[concepts/genetic-strategy-evolution]] (GA v2: popSize 30, maxGen 20, fitness formula)

## [2026-05-14T22:00:00] compile | Daily Log 2026-05-14
- Source: daily/2026-05-14.md
- Articles created: [[concepts/android-foreground-service-long-operation]]
- Articles updated: (none — prior session articles for 2026-05-14 were already compiled)
- Key extractions: FGS pattern for 6-min strategy scanning, START_NOT_STICKY rationale, finally>onCleared cleanup discipline, notification ID collision avoidance, mockk relaxed Context test pattern

## [2026-05-14T19:00:00+03:00] compile | 2026-05-14.md (pass 1)
- Source: daily/2026-05-14.md
- Result: 7 new articles, 1 connection, 4 articles updated
- Articles created:
  - [[concepts/runtest-uncompleted-coroutines-trap]] — testScope children infinite coroutines, @AfterEach too late, backgroundScope pattern, DataStore scope
  - [[concepts/engine-await-ready-pattern]] — awaitReady() default no-op, URnetwork peerCount 200ms/15s, WARP UAPI 300ms/10s, between routeTrafficForEngine and onEngineStarted
  - [[concepts/warp-uapi-handshake-polling]] — LocalSocket UAPI polling last_handshake_time_sec, NOT awgGetConfig JNI (SIGSEGV), WarpHandshakeUapi.kt
  - [[concepts/byedpi-native-thread-join-race]] — proxyJob.cancel() ≠ native C exit, second join after forceClose(), g_proxy_running flag race
  - [[concepts/robolectric-hilt-eager-init-trap]] — @HiltAndroidApp loads real Application in Robolectric, eager field init NPE, lazy + @Config fix
  - [[concepts/github-draft-release-visibility]] — gh release list omits drafts, asset URLs 404 unauth, fix = --draft=false
  - [[concepts/combined-aidl-race-elimination]] — turnOnAndGetSockets combined AIDL method, ParcelFileDescriptor.adoptFd().close()
- Connections created:
  - [[connections/engine-readiness-vs-false-connected]] — awaitReady() unifies false-connected fix across engines
- Articles updated:
  - [[concepts/warp-false-connected-no-handshake]] — UAPI socket polling implementation, awaitReady integration, NOT awgGetConfig JNI
  - [[concepts/genetic-strategy-evolution]] — GA v2 params (pop30, gen20, elite3, target0.85, fitness^1.5, initial 40/30/30), cache poisoning fix, min length 5
  - [[concepts/go-runtime-process-isolation]] — combined AIDL turnOnAndGetSockets (H3), ParcelFileDescriptor.adoptFd().close() (H4)
  - [[concepts/byedpi-mock-server-ci-fragility]] — Root Cause 4: mock returns 0 instant proxyJob completion; fix = answers{latch.await();0}; backgroundScope for pluginScope
- Index: 9 rows added, 4 rows updated
- Sessions covered: 10:00 (detekt/ktlint, foreground service), 13:56 (feat/mtg CI), 15:xx (UncompletedCoroutinesError x6+7, Robolectric NPE), 17:xx (root fixes: separate scope, DataStore scope, lazy init, $0 printf), 14:17-14:39 (CI iterations), 15:56 (code review + Split Tunneling tabs/ViewModel), 16:20 (awaitReady URnetwork + ByeDPI join race), 16:33-16:50 (WARP UAPI handshake, awaitReady), 17:47 (combined AIDL H3/H4, URnetwork window), 18:00+ (draft releases, GA v2, CI red fixes, backgroundScope)
- Excluded: detekt SheetTarget enum (implementation detail), ktlint line-breaks (mechanical), SplitTunneling tabs/ViewModel (UI wiring), foreground service StrategyScanService (implementation), specific CI run numbers, memory flush failures, task tracking feedback (already in feedback memory)

## [2026-05-14T14:00:00+03:00] compile | 2026-05-13.md (pass 7 — sentinel-protecting-bug + stateIn dedup)
- Source: daily/2026-05-13.md
- Result: 1 new article, 1 updated
- Articles created:
  - [[concepts/sentinel-protecting-bug-trap]] — sentinel guards buggy behavior (Unavailable instead of StaticLocation), blocks correct fix; EngineUrnetworkContractTest incident; prevention: grep sentinels when fixing functions
- Articles updated:
  - [[concepts/stateIn-eagerly-test-trap]] — added StateFlow dedup section: distinctUntilChanged() redundant on stateIn() output, StateFlow deduplicates by equality
- Index: 1 row added (sentinel-protecting-bug-trap), 1 row updated (stateIn summary)
- Sessions covered: 20:53 (sentinel blocking correct fix), 21:13 (sentinel rewrite + distinctUntilChanged redundancy confirmed)

## [2026-05-14T11:00:00+03:00] compile | 2026-05-13.md (pass 6 — format upgrade + sentinel note)
- Source: daily/2026-05-13.md
- Result: 1 article reformatted to wiki schema; 1 article updated with sentinel note
- Articles created: (none)
- Articles updated:
  - [[concepts/urnetwork-location-token-best-available]] — переписан в AGENTS.md wiki формат (YAML frontmatter, Key Points, Details, Anti-Pattern, Related Concepts, Sources); убран informal Russian style, добавлены wikilinks
  - [[concepts/ip-probe-route-architecture]] — добавлен раздел "StaticLocation — null country контракт": country может быть null когда countryCode существует; IpInfoCard обрабатывает через stringResource; sentinel обновлён с Unavailable на StaticLocation(null, code)

## [2026-05-14T10:00:00+03:00] compile | 2026-05-13.md (pass 5 — net-new content)
- Source: daily/2026-05-13.md
- Result: 1 new article created (session 20:35 content missed by prior passes)
- Articles created:
  - [[concepts/urnetwork-location-token-best-available]] — LocationToken.fromConnectLocation drops bestAvailable flag; isBestAvailable=false after DataStore round-trip; fix = add field + = false default + Serializer update
- Articles updated: (none)
- Index: 1 row added

## [2026-05-14T09:00:00+03:00] compile | 2026-05-13.md (pass 4 — verification)
- Source: daily/2026-05-13.md
- Result: ALREADY COMPILED — all content covered by 3 prior compile passes (19:00, 22:00 on 2026-05-13; 00:15 on 2026-05-14)
- Articles created: (none)
- Articles updated: (none)
- Verification: 5 new articles on disk, index complete, both updated articles contain 2026-05-13 content:
  - [[concepts/byedpi-args-parsing]] — double argv[0] trap (C native-lib.c + Kotlin buildArgs) ✓
  - [[concepts/genetic-strategy-evolution]] — fitness cache, hyperbolic formula, 75 seeds, persistent TTL 24h, per-network isolation, staleness, auto-save, targetFitness 0.85 ✓
  - [[concepts/kotlin-trailing-lambda-parameter-trap]] ✓
  - [[concepts/stateIn-eagerly-test-trap]] ✓
  - [[concepts/native-binary-auto-update-pipeline]] ✓
  - [[concepts/warp-doh-per-slot-config]] ✓
  - [[concepts/urnetwork-connectstatus-mr-mapping]] ✓

## [2026-05-14T00:15:00+03:00] compile | 2026-05-13.md (pass 3 — evening GA session 19:34 + git commits)
- Source: daily/2026-05-13.md
- Articles created: (none)
- Articles updated: [[concepts/genetic-strategy-evolution]] (persistent StrategyFitnessCache TTL 24h, SavedStrategy.lastVerifiedAtMs staleness 7-day threshold + StalenessLabel composable, per-network GeneMemory + StrategyFitnessCache via NetworkProfileDetector + EvolutionResourcesProvider, auto-save best chromosome, targetFitness 0.85 default, 75 upstream seeds from ByeByeDPI with pinned priority)
- Index updated: genetic-strategy-evolution summary expanded
- Sessions covered: 19:34 (persistent fitness cache, staleness tracking, per-network memory isolation, auto-save best, targetFitness, 75 upstream seeds, pinned priority seeds)
- Git commits covered: 3a33e6e0 (per-network GeneMemory), 177b7461 (staleness lastVerifiedAtMs), ac50c8f8 (persistent StrategyFitnessCache TTL 24h), c2a6054b (ktlint, 75 seeds, pinned priority, auto-save, targetFitness)
- Excluded: ktlint line-length fixes (mechanical), per-network DI wiring details (implementation), specific test method names (trivia)

## [2026-05-13T22:00:00+03:00] compile | 2026-05-13.md (pass 2 — residual concepts)
- Source: daily/2026-05-13.md
- Articles created: [[concepts/urnetwork-connectstatus-mr-mapping]]
- Articles updated: [[concepts/genetic-strategy-evolution]] (chromosome fitness cache, hyperbolic latency formula dead code note, ByeDpiKnownSeeds + population 25 + stagnationThreshold coerceAtLeast(3)), [[concepts/byedpi-args-parsing]] (double argv[0] registration trap: C native-lib.c + Kotlin buildArgs both prepend, first real flag dropped)
- Index updated: byedpi-args-parsing and genetic-strategy-evolution rows updated with 2026-05-13 sources; urnetwork-connectstatus-mr-mapping row added
- Sessions covered: GA cache/latency-fitness/known-seeds/population25/stagnation CI fix; double argv[0] bug (f0e9f206); native-auto-apply.yml pipeline; warp-doh-per-slot refactor; trailing lambda trap; stateIn(Eagerly) vs WhileSubscribed test trap; URnetwork ConnectStatus→MR.strings regression
- Note: kotlin-trailing-lambda-parameter-trap, stateIn-eagerly-test-trap, native-binary-auto-update-pipeline, warp-doh-per-slot-config already existed from pass 1 — verified content complete, no updates needed

## [2026-05-13T19:00:54+03:00] compile | 2026-05-13.md
- Source: daily/2026-05-13.md
- Articles created: [[concepts/kotlin-trailing-lambda-parameter-trap]], [[concepts/stateIn-eagerly-test-trap]], [[concepts/native-binary-auto-update-pipeline]], [[concepts/warp-doh-per-slot-config]]
- Articles updated: [[concepts/genetic-strategy-evolution]] (chromosome cache, hyperbolic latency fitness, ByeDpiKnownSeeds, population 25)

## [2026-05-12T23:59:00+03:00] compile | daily/2026-05-12.md
- Source: daily/2026-05-12.md
- Articles created: [[concepts/suppress-annotation-decomposition]]
- Articles updated: [[concepts/core-backup-module]] (hardcoded AES-GCM key by design), [[concepts/genetic-strategy-evolution]] (dead settings anti-pattern)
- Note: Most 2026-05-12 concepts were already compiled in prior sessions — chart scaling, genetic algorithm, concurrency traps, backup serializer, URnetwork modes, tautology tests, ByeDPI runtime disconnect, peer watchdog, WARP DNS exhaustion, audit-driven discovery

## [2026-05-13T02:00:00+03:00] compile | 2026-05-12.md (pass 5 — session 21:19 engine diagnostics)
- Source: daily/2026-05-12.md
- Articles created: [[concepts/warp-preflight-dns-exhaustion]], [[concepts/urnetwork-peer-watchdog-recovery]], [[concepts/byedpi-strategy-runtime-disconnect]]
- Connections created: [[connections/engine-specific-failure-diagnostics]]
- Articles updated: [[concepts/urnetwork-sdk-integration]] (peer discovery loss, recover() watchdog, Solana/wallet UI removal, country switch UX)
- Sessions covered: 21:19 (6-subagent diagnostic: WARP DNS preflight 5s×3 exhaustion, URnetwork peer loss after 4-5 min + recover(), ByeDPI jniStartProxy=-1 + static winning args, country switch UX, Solana/URx UI cleanup, EnginePlugin FQN→imports)
- Excluded: EnginePlugin FQN→imports (code cleanup, not concept), specific sentinel test names (implementation detail), WARP preflight DNS fix #12 status (in-progress work)

## [2026-05-13T01:30:00+03:00] compile | 2026-05-12.md (pass 4 — residual patterns)
- Source: daily/2026-05-12.md
- Articles created: [[concepts/test-tautology-always-green]]
- Articles updated: [[concepts/gene-memory-concurrency-traps]] (SNI seed tokenizer trap added), [[connections/ci-false-green-vectors]] (tautology assertions as third false-green vector)
- Sessions covered: 17:55 (Logger reflection fragility), 18:34 (tautology `isEmpty() || isNotEmpty()`, SNI seed `"-s domain"` split), 20:54 (commit/push discipline — feedback memory only, no wiki concept)
- Rationale: pass 3 excluded "Logger reflection fragility" and "tautology assertions" as trivia; on re-review, both are reusable testing anti-patterns that compound existing CI false-green vectors — worth documenting

## [2026-05-13T00:30:00+03:00] compile | 2026-05-12.md (pass 3 — verification)
- Source: daily/2026-05-12.md
- Articles created: (none)
- Articles updated: (none)
- Verification: 6 articles reference daily/2026-05-12.md in index, all content matches daily log:
  - [[concepts/genetic-strategy-evolution]] — StrategyGene + GenePool + sequential evaluation ✓
  - [[concepts/gene-memory-concurrency-traps]] — HashMap race + SavedStrategyStore Mutex + importRawJson validation ✓
  - [[concepts/backup-awg-field-roundtrip-loss]] — 5 missing AWG fields + AppBackupSerializer hardening ✓
  - [[concepts/urnetwork-control-network-modes]] — AUTO/ALWAYS + WIFI/ALL + provideControlMode≠WindowType ✓
  - [[concepts/chart-nice-max-dynamic-scaling]] — 17 thresholds 1-2-5 + SPEED_SAMPLE_INTERVAL_MS ✓
  - [[connections/audit-driven-concurrency-discovery]] — 6-subagent audit pattern ✓
- Excluded: SNI seed tokenizer issue (Sprint 5 impl detail), @Suppress("LongMethod") discipline (CLAUDE.md rule), engineAutoPriority default order (minor config), v0.0.13 lint cycle (covered pass 2), JDK Temurin winget failure (env issue), Logger reflection fragility (tech debt trivia)

## [2026-05-12T23:00:00+03:00] compile | 2026-05-12.md (pass 2 — session 18:55 coverage)
- Source: daily/2026-05-12.md
- Articles created: (none)
- Articles updated: (none)
- Sessions covered: 18:55 (v0.0.13 release, lint double-red CI, squash dev→main)
- Excluded: v0.0.13 release operational details (reinforces existing [[concepts/ci-workflow-discipline]] and [[concepts/release-process]], no new concept); lint misfires (unused import, regex line, single-line if brace) — already covered by feedback_ktlint_traps memory

## [2026-05-12T19:00:00+03:00] compile | 2026-05-12.md
- Source: daily/2026-05-12.md
- Articles created: [[concepts/genetic-strategy-evolution]], [[concepts/gene-memory-concurrency-traps]], [[concepts/backup-awg-field-roundtrip-loss]], [[concepts/urnetwork-control-network-modes]], [[concepts/chart-nice-max-dynamic-scaling]]
- Connections created: [[connections/audit-driven-concurrency-discovery]]
- Articles updated: (none)
- Sessions covered: 11:59 (chartNiceMax), 14:05 (Sprint 3-5 StrategyTest), 15:08 (code review Sprint 2-5), 17:55 (URnetwork modes, decomp refactor), 18:34 (22-finding audit, 5 fix commits)
- Excluded: @Suppress annotation discipline (existing CLAUDE.md rule), JDK install winget failure (env issue), UnifiedLogger decomp details (refactoring trivia), specific CI run numbers, 7 memory flush failures, Icons.Filled.Bookmark violation (already in feedback_material_icons_core.md), dead settings list (implementation detail), es/pt locale strings (i18n mechanics)

## [2026-05-11T22:30:00+03:00] compile | 2026-05-11.md (pass 2 — session 20:41 coverage)
- Source: daily/2026-05-11.md
- Articles created: [[concepts/debounce-split-heterogeneous-flow]]
- Articles updated: [[concepts/dual-go-runtime-eager-loading]] (asymmetric per-process bootstrap guard), [[concepts/go-runtime-process-isolation]] (asymmetric bootstrap guard + OzeroAppProcessIsolationTest), [[concepts/engine-switch-chain-cascading-failures]] (debounce split + prev-tracking + missing engineAutoPriority in Snapshot)
- Sessions covered: 20:41 (SIGABRT asymmetric bootstrap, late yellow debounce split, chart jumping visual state, EngineSettingsRestartObserver prev-tracking)
- Excluded: chartNiceMax() stable Y-axis thresholds (UI detail), drawPath style named arg (ktlint fix), Path() local val (ktlint fix), visualConnected state management (tactical UI — too specific for reusable concept)

## [2026-05-11T20:00:00+03:00] compile | 2026-05-11.md
- Source: daily/2026-05-11.md
- Articles created: [[concepts/go-runtime-process-isolation]], [[concepts/engine-ownership-boundary]], [[concepts/split-tunnel-internet-permission-filter]], [[concepts/persistent-logger-accumulation-trap]], [[concepts/sentinel-fqn-desync]]
- Connections created: [[connections/go-runtime-conflict-resolution-evolution]]
- Articles updated: [[concepts/dual-go-runtime-eager-loading]] (GoRuntimeGuard removed, process isolation supersedes), [[concepts/engine-switch-chain-cascading-failures]] (GoRuntimeGuard removed, Engine Ownership Boundary, process isolation)
- Sessions covered: 11:01 (GoRuntimeGuard removal), 11:23 (PersistentLoggers accumulation), 11:38 (split tunnel fixes + timeframe redesign), 12:45 (FQN sentinel desync + CI stabilization), 14:12 (Engine Ownership Boundary + 3 bugs), 19:49 (process isolation + AIDL + CI churn)
- Excluded: TimeframeOption UI redesign (S30/M5/M30/H1, padding zeros, MAX_SPEED_HISTORY_POINTS 86400→3600) — UI configuration detail, not reusable knowledge; chartNiceMax() thresholds — UI detail; specific CI run numbers; 8 memory flush failures; auto-mode → TopScreen.AutoModeSettings routing fix — minor UI routing bug

## [2026-05-11T00:30:00+03:00] compile | 2026-05-10.md (pass 6 — full recompile verification)
- Source: daily/2026-05-10.md
- Articles created: (none — all 4 new concepts already exist from passes 1-5)
- Articles updated: (none — all updates already applied)
- Verification: 8 articles reference daily/2026-05-10.md in index, all content matches daily log:
  - [[concepts/mockk-aar-native-initializer-trap]] — LocationInfo wrapper pattern ✓
  - [[concepts/kotlin-empty-string-null-coalesce-trap]] — takeIf{isNotBlank()} ✓
  - [[concepts/viewmodel-polling-runtest-trap]] — advanceTimeBy+runCurrent ✓
  - [[concepts/hilt-assistedinject-mixed-injection]] — regular vs @Assisted params ✓
  - [[concepts/byedpi-mock-server-ci-fragility]] — Root Cause 3 injectable lambda ✓
  - [[concepts/tun-self-exclusion-sdk-engines]] — SOCKS/hev loop mechanism ✓
  - [[concepts/engine-switch-chain-cascading-failures]] — IP warmup 8s→3s ✓
  - [[concepts/vpn-ip-detection-contract]] — warmup timing tuning ✓
- Excluded: Session 13:00 billing/monetization planning (project direction, not technical concept), wiki-find ModuleNotFoundError (tooling issue), naming refactor question (no context)

## [2026-05-10T23:30:00+03:00] compile | 2026-05-10.md (pass 5 — final gaps)
- Source: daily/2026-05-10.md
- Articles created: [[concepts/hilt-assistedinject-mixed-injection]]
- Articles updated: [[concepts/engine-switch-chain-cascading-failures]] (IP warmup 8s→3s from session 17:46), [[concepts/vpn-ip-detection-contract]] (warmup timing + retry tuning from session 17:46)
- Note: Prior 4 passes missed: (1) @AssistedInject mixed injection pattern from session 14:48 — subagent DI errors exposed the gotcha, (2) IP_INFO_WARMUP_MS tuning 8000→3000 from session 17:46 GROUP B — existing articles still referenced 8s

## [2026-05-10T21:41:00+03:00] compile | 2026-05-10.md (session 4 — end-of-day close)
- Source: daily/2026-05-10.md
- Articles created: (none)
- Articles updated: (none)
- Note: Session 21:41 — CI green (simplify complete), user asked about naming refactor but no context available. No extractable technical knowledge. Full day compile verified complete across 3 prior passes.

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
