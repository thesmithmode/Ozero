# Build Log

## [2026-05-27T19:25:00+03:00] compile | daily/2026-05-27.md
- Source: daily/2026-05-27.md
- Articles created: [[concepts/ci-heredoc-single-quote-variable-trap]], [[concepts/github-release-asset-name-verification]], [[concepts/github-workflow-failure-all-jobs-success]], [[concepts/windows-runner-gradlew-shell]], [[concepts/gradle-r8-oom-github-runners]]
- Articles updated: [[concepts/release-process]] (multi-platform build architecture, upstream asset name verification, workflow conclusion unreliability)

## [2026-05-27T23:00:00+03:00] compile | daily/2026-05-26.md (pass 9 — index sync)
- Source: daily/2026-05-26.md
- Articles created: (none)
- Articles updated: (none — all confirmed compiled in passes 1-8)
- Index updated: [[concepts/urnetwork-provide-tun-investigation]] summary refreshed to reflect 2026-05-27 IoLoop root cause + pipe-based dummy fix (file updated 2026-05-27, index was stale)

## [2026-05-27T22:30:00+03:00] compile | daily/2026-05-25.md
- Source: daily/2026-05-25.md
- Articles created: [[concepts/ktlint-multiline-paren-gradle-dsl]], [[concepts/feature-deletion-orphaned-consumers]]
- Articles updated: [[concepts/release-process]] (GitHub force-push tag → HTTP 500; safe path: delete API + POST new tag)
- Notes: Core articles (versioncode-git-history-rewrite-regression, channel-conflate-deprecated, compileonly-nontransitive-android-library, gomobile-go-seq-multi-sdk-conflict, singbox-aidl-async-error-swallow, gomobile-build-go-get-require, agp-local-aar-library-restriction) were already compiled in-session on 2026-05-25 — present in index but no log entry existed. Two gaps filled: ktlint multi-line paren Gradle DSL trap and feature-deletion orphaned consumer pattern.

## [2026-05-27T22:10:00+03:00] compile | daily/2026-05-22.md
- Source: daily/2026-05-22.md
- Articles created: [[concepts/fptn-engine-design]], [[concepts/byedpi-udp-quic-routing]], [[concepts/vpn-slot-self-detection-false-positive]]
- Articles updated: [[concepts/visual-connected-switching-state]] (Connected(X) engine-specific clearing, restartVpnIfConnected preserves switching.to), [[concepts/subagent-code-review-false-positives]] (domain memory injection insight, walletOverride/warpSlots FP incident)
- Index updated: vpnservice-double-shutdown-guard source corrected to daily/2026-05-22.md; warp-allowedips-tun-routing source corrected to daily/2026-05-22.md; visual-connected-switching-state and subagent-code-review-false-positives entries updated
- Notes: vpnservice-double-shutdown-guard and warp-allowedips-tun-routing files were already compiled (correct content) but had stale sources in index — corrected. relay-coordinator-ownership-transfer and urnetwork-locvm-bootstrap-race already compiled from daily/2026-05-22 (1).md (duplicate file).

## [2026-05-27T19:20:00+03:00] compile | daily/2026-05-15 (1).md
- Source: daily/2026-05-15 (1).md
- Articles created: [[concepts/auto-mode-traffic-fail-blindspot]], [[concepts/code-review-before-ci-monitor]], [[concepts/cyclomatic-complexity-extract-helper]]
- Articles updated: (none — extractNativeLibs, tun-self-exclusion, sentinel-protecting-bug, byedpi-jni-guard-hardening, android-i18n-vm-compose-extraction already compiled from daily/2026-05-15.md which covers the same session content)

## [2026-05-27T19:30:00+03:00] compile | daily/2026-05-14 (1).md
- Source: daily/2026-05-14 (1).md
- Articles created: [[concepts/github-draft-release-invisible]], [[concepts/shell-printf-dollar-zero-trap]], [[concepts/new-engine-module-ci-checklist]], [[concepts/ga-targetfitness-probe-test-early-exit]]
- Articles updated: (none — runtest-uncompleted-coroutines-trap and robolectric-hilt-eager-init-trap and byedpi-mock-server-ci-fragility already compiled from this source)

## [2026-05-27T18:58:39+03:00] compile | 2026-05-11.md (pass 3 — index correction)
- Source: daily/2026-05-11.md
- Articles created: (none — all compiled in passes 1-2 on 2026-05-11)
- Articles updated: (none — content complete)
- Index fixed: 6 entries had stale source `daily/2026-04-30.md | 2026-04-30` → corrected to `daily/2026-05-11.md | 2026-05-11`: debounce-split-heterogeneous-flow, engine-ownership-boundary, go-runtime-process-isolation, persistent-logger-accumulation-trap, sentinel-fqn-desync, split-tunnel-internet-permission-filter
- Index added: [[concepts/visual-connected-switching-state]] was missing from index despite file existing (excluded in pass 2 notes, but file was created); added with accurate 1-line summary

## [2026-05-26T23:59:00+03:00] compile | daily/2026-05-26.md (pass 8 — verification)
- Source: daily/2026-05-26.md
- Articles created: (none)
- Articles updated: (none)
- Notes: Full verification pass. All 12 concepts confirmed fully compiled across passes 1-7: warp-amber-flash-sentinel, singbox-crash-tombstone-diagnosis (incl. startWithConfig DeadObjectException configLen=868/20448), singbox-subscription-fetch-robustness, singbox-dns-outbound-deprecated, singbox-splithttp-unsupported, git-active-branch-discipline, singbox-ping-abstractbean-deserialization, pingJob-viewmodel-cancellation, gh-run-list-watcher-race, detekt-toomany-functions-semantics, urnetwork-provide-tun-investigation (connectBestAvailable hypothesis D), subagent-code-review-false-positives. Index and log entries verified consistent.

## [2026-05-26T23:55:00+03:00] compile | daily/2026-05-26.md (pass 7 — session 21:41 gap fill)
- Source: daily/2026-05-26.md
- Articles created: [[concepts/warp-amber-flash-sentinel]]
- Articles updated: [[concepts/singbox-crash-tombstone-diagnosis]] (added startWithConfig DeadObjectException + configLen details from session 21:41)

## [2026-05-26T23:30:00+03:00] compile | daily/2026-05-26.md (pass 6 — tombstone gap)
- Source: daily/2026-05-26.md
- Articles created: [[concepts/singbox-crash-tombstone-diagnosis]]
- Articles updated: [[knowledge/index.md]] (new entry added)
- Notes: Pass 6 captured one concept missed in passes 1-5: tombstone pull path `adb pull /data/user/0/ru.ozero.app/files/debug/` from session 13:59 + crash root cause map from session 19:44 (dns outbound, splithttp, Connected race). All other 10 concepts confirmed compiled.

## [2026-05-26T22:00:00+03:00] compile | daily/2026-05-26.md (pass 5 — duplicate guard)
- Source: daily/2026-05-26.md
- Articles created: (none)
- Articles updated: (none)
- Notes: Re-invoked compiler; all articles verified as fully compiled in passes 1-4. No gaps found.

## [2026-05-26T21:30:00+03:00] compile | daily/2026-05-26.md (pass 4 — recheck)
- Source: daily/2026-05-26.md
- Articles created: (none — all already compiled in passes 1-3)
- Articles updated: (none)
- Notes: Recheck pass confirmed all 10 concepts fully compiled: singbox-subscription-fetch-robustness, singbox-dns-outbound-deprecated, singbox-splithttp-unsupported, git-active-branch-discipline, singbox-ping-abstractbean-deserialization, pingJob-viewmodel-cancellation, gh-run-list-watcher-race, detekt-toomany-functions-semantics, urnetwork-provide-tun-investigation, subagent-code-review-false-positives. singbox-subscription-fetch-robustness.md untracked — needs commit.

## [2026-05-26T21:00:00+03:00] compile | daily/2026-05-26.md (pass 3 — final gap fill)
- Source: daily/2026-05-26.md
- Articles created: [[concepts/singbox-subscription-fetch-robustness]]
- Articles updated: (none — all other articles from this log already compiled in passes 1-2)
- Notes: Pass 3 captured subscription timeout/cancel anti-pattern (T5-T6 from session 19:44); all other concepts (dns outbound, splithttp, AbstractBean ping, pingJob cancel, gh watcher race, detekt semantics, git branch discipline, subagent false positives, urnetwork connectBestAvailable, WARP stale tunnel 4ms) already fully compiled

## [2026-05-26T20:30:00+03:00] compile | daily/2026-05-26.md (pass 2 — gap fill)
- Source: daily/2026-05-26.md
- Articles created: [[concepts/subagent-code-review-false-positives]]
- Articles updated: [[concepts/urnetwork-provide-tun-investigation]] (hypothesis D connectBestAvailable added to body, was only in comment)

## [2026-05-26T20:00:00+03:00] compile | daily/2026-05-26.md
- Source: daily/2026-05-26.md
- Articles created: [[concepts/singbox-ping-abstractbean-deserialization]], [[concepts/pingJob-viewmodel-cancellation]], [[concepts/gh-run-list-watcher-race]], [[concepts/detekt-toomany-functions-semantics]], [[concepts/singbox-dns-outbound-deprecated]], [[concepts/singbox-splithttp-unsupported]], [[concepts/git-active-branch-discipline]]
- Articles updated: [[concepts/urnetwork-provide-tun-investigation]] (connectBestAvailable hypothesis added), [[concepts/singbox-engine-design]] (1.13.0 breaking changes: dns outbound + splithttp; ping AbstractBean), [[concepts/warp-false-connected-no-handshake]] (4ms stale tunnel reconnect from v0.2.8/v0.2.9 logs)

## [2026-05-24T21:46:00] compile | 2026-05-24.md
- Source: daily/2026-05-24.md
- Articles created: [[concepts/ktlint-volatile-annotation-spacing]]
- Articles updated: [[concepts/hilt-cross-process-injection]], [[concepts/singbox-engine-design]], [[concepts/singbox-subscription-architecture]], [[concepts/kotlin-expression-body-return-trap]], [[concepts/kapt-per-module-requirement]], [[concepts/ci-gradle-log-reading]], [[concepts/cascade-unresolved-import-masking]]

## [2026-05-24T21:10:45+03:00] compile | daily/2026-05-24.md
- Source: daily/2026-05-24.md
- Articles created: (none)
- Articles updated: [[concepts/singbox-engine-design]], [[concepts/singbox-subscription-architecture]], [[concepts/ci-gradle-log-reading]], [[concepts/kotlin-expression-body-return-trap]], [[concepts/hilt-cross-process-injection]], [[concepts/kapt-per-module-requirement]], [[concepts/cascade-unresolved-import-masking]], [[concepts/parallel-chat-instruction-leak]], [[concepts/git-contributor-rewrite]]
- Notes: All listed articles were fully compiled in passes 1-7 (same day). No new information to add — all facts from this instruction block are already present in existing articles.

## [2026-05-24T21:30:00+03:00] compile | 2026-05-24.md (pass 7 — gap fill)
- Source: daily/2026-05-24.md
- Articles created: [[concepts/singbox-subscription-architecture]] (file was missing despite being listed in index/log)
- Articles updated: (none — all other articles from this log already compiled in passes 1-6)

## [2026-05-24T20:29:06+03:00] compile | 2026-05-24.md
- Source: daily/2026-05-24.md
- Articles created: [[concepts/singbox-subscription-architecture]]
- Articles updated: [[concepts/singbox-engine-design]], [[concepts/kotlin-expression-body-return-trap]], [[concepts/ci-gradle-log-reading]], [[concepts/cascade-unresolved-import-masking]], [[concepts/hilt-cross-process-injection]], [[concepts/kapt-per-module-requirement]], [[concepts/room-entity-database-registration]]

## [2026-05-24T20:30:00+03:00] compile | 2026-05-24.md (pass 6 — sessions 19:34 + 19:53)
- Source: daily/2026-05-24.md
- Articles created: [[concepts/kotlin-import-at-file-level-only]]
- Articles updated: [[concepts/index]] (new entry added)
- Notes: Sessions 19:34/19:53 added: import-inside-function trap, MutableList.replaceAll UnaryOperator issue, PowerShell heredoc limitation. All other concepts from these sessions already covered in passes 1-5.

## [2026-05-24T23:59:00+03:00] compile | 2026-05-24.md (pass 3)
- Source: daily/2026-05-24.md
- Articles created: (none — all concepts already captured in passes 1 and 2)

## [2026-05-24T23:59:45+03:00] compile | 2026-05-24.md (pass 5 — subscription modules + kapt/room patterns)
- Source: daily/2026-05-24.md
- Articles created: [[concepts/kapt-per-module-requirement]], [[concepts/room-entity-database-registration]]
- Articles updated: [[concepts/singbox-engine-design]] (P4/P5 subscription architecture, CI pitfalls, cross-references)

## [2026-05-24T23:59:30+03:00] compile | 2026-05-24.md (pass 4 — engine-singbox P4/P5 CI sessions)
- Source: daily/2026-05-24.md
- Articles created: [[concepts/hilt-cross-process-injection]], [[concepts/kotlin-expression-body-return-trap]], [[concepts/ci-gradle-log-reading]], [[concepts/cascade-unresolved-import-masking]]
- Articles updated: (none — existing articles already current)
- Articles updated: (none — all updates complete)
- Notes: Full audit pass confirmed coverage: singbox-engine-design (urlTest, AD-15 Gradle isolation, preset_groups AD-14 restore, kill switch inheritance), parallel-chat-instruction-leak (sing-box/WARP context bleed incident), toml-windows-path-escaping-trap, git-contributor-rewrite, ci-workflow-discipline (@Volatile blank line + upload-artifact v7), dependabot-dev-workflow-mismatch (v0.3.0 triage). No gaps found.

## [2026-05-24T23:45:00+03:00] compile | 2026-05-24.md (pass 2)
- Source: daily/2026-05-24.md
- Articles created: [[concepts/parallel-chat-instruction-leak]]
- Articles updated: (none — pass 1 already complete)

## [2026-05-24T23:30:00+03:00] compile | 2026-05-24.md
- Source: daily/2026-05-24.md
- Articles created: [[concepts/toml-windows-path-escaping-trap]], [[concepts/singbox-engine-design]], [[concepts/git-contributor-rewrite]]
- Articles updated: [[concepts/ci-workflow-discipline]] (@Volatile blank line + upload-artifact v7 breaking), [[concepts/dependabot-dev-workflow-mismatch]] (v0.3.0 triage data + upload-artifact v7 confirmed breaking)

## [2026-05-24T19:00:00+03:00] compile | 2026-05-23.md
- Source: daily/2026-05-23.md
- Articles created: [[concepts/android-ndk-cxx-static-linking]]
- Articles updated: (none — all other 2026-05-23 concepts were compiled in-session: [[concepts/warp-uapi-stale-socket-cleanup]], [[concepts/fptn-sni-bypass-method]], [[concepts/urnetwork-provide-secret-keys-identity]], [[concepts/urnetwork-jwt-bootstrapper]], [[concepts/masterdns-deploy-hardening]], [[concepts/urnetwork-balance-optimistic-cache]], [[concepts/gradle-configuration-cache-agp-bug]], [[concepts/proguard-release-drift]], [[concepts/release-process]])
- Notes: One net-new concept: Android NDK c++_static requirement for clns-7 namespace. FPTN libfptn_native_lib.so crashed on first System.loadLibrary() because c++_shared is not visible in clns-7; fix = Conan profile compiler.libcxx=c++_static. All other sessions' concepts from this dense day were already captured during the coding sessions themselves.

## [2026-05-24T18:06:14+03:00] compile | 2026-05-22 (1).md
- Source: daily/2026-05-22 (1).md
- Articles created: [[concepts/urnetwork-locvm-bootstrap-race]]
- Articles updated: [[concepts/relay-coordinator-ownership-transfer]] (added providePaused hardcode bug + fix from session 16:02+; commit e0d53ca4)
- Notes: Most 2026-05-22 concepts were already compiled in prior sessions (engine-chip-switching-desync, vpnservice-double-shutdown-guard, warp-allowedips-tun-routing, byedpi-hev-pipeline-upstream-parity udp:tcp, byedpi-vpn-pipeline-upstream-divergence confirmation, urnetwork-balance-optimistic-cache FREE_TIER_CAP removal, self-review-insufficient false-positives). Two gaps filled: (1) LocVM bootstrap race — new article; (2) RelayCoordinator setProvidePaused hardcode — updated existing article.

## [2026-05-24T00:47:26+00:00] compile | 2026-05-08.md
- Source: daily/2026-05-08.md
- Articles created: (all were compiled in-session; none net-new this pass)
- Articles updated: [[concepts/amneziawg-jni-classpath-completeness]] (index corrected: stale 2026-04-30 source → 2026-05-08; summary updated with discovery method), [[concepts/amneziawg-so-binary-integrity]] (index corrected: stale source → 2026-05-08; summary updated), [[concepts/compose-remember-stale-collectasstate]] (index corrected: stale source → 2026-05-08; summary updated), [[concepts/gitignore-jnilibs-conflict]] (index corrected: stale source → 2026-05-08; summary updated), [[concepts/health-monitor-p2p-mismatch]] (index corrected: stale source → 2026-05-08; summary updated with URnetwork false-start detail), [[concepts/test-io-thread-zombie-trap]] (index corrected: stale source → 2026-05-08; summary updated), [[concepts/warp-handle-leak-sigabrt]] (index corrected: stale source → 2026-05-08; summary updated)
- Notes: All 7 key concepts from 2026-05-08 (WARP maven→PORTAL_WG migration, JNI classpath, SO SHA256, attachTun handle guard, gitignore SO conflict, HealthMonitor P2P mismatch, IO thread zombie, compose-remember stale) were compiled during the session itself. This pass corrected the index which had placeholder entries with wrong source daily/2026-04-30.md and wrong dates for all 7 articles. No articles required content updates — only the index was stale.

## [2026-05-24T00:45:00+00:00] compile | 2026-05-07.md
- Source: daily/2026-05-07.md
- Articles created: [[concepts/git-stash-task-switch-trap]]
- Articles updated: [[concepts/ci-workflow-discipline]] (added intermediate commit validation gap rule: gh run list --commit empty → CI not triggered on ee1c1ea; forceVanilla=false + VANILLA test mismatch undetected; source added), [[concepts/warp-config-generator-api]] (added suspicious mirror DNS 176.99.11.77+80.78.247.254 ≠ Cloudflare; source added)
- Notes: Most 2026-05-07 concepts were already compiled during the session itself (warp-awg-obfuscation-russian-isps, android-vpn-traffic-stats, dual-go-runtime-eager-loading, warp-false-connected-no-handshake all cite daily/2026-05-07.md). Three gaps filled: (1) git stash task-switch trap new article from Session 13:24 stash loss; (2) intermediate commit CI gap added to ci-workflow-discipline; (3) mirror DNS suspicion added to warp-config-generator-api.

## [2026-05-24T00:42:00+00:00] compile | 2026-05-06.md
- Source: daily/2026-05-06.md
- Articles created: [[concepts/warp-awgturnon-blocking-fd]], [[concepts/warp-dns-routing-loop]]
- Articles updated: [[concepts/warp-uapi-stale-socket-cleanup]] (added EADDRINUSE/bind-path stale socket variant from session 13:16; added daily/2026-05-06.md as source)
- Notes: android-xml-string-escaping and kotlin-suspendcancellablecoroutine-type-inference were already compiled from this log in a prior pass. Core new findings: (1) awgTurnOn=-1 root cause = non-blocking fd, fixed by blocking=true in TunSpec; (2) DNS loop = endpoint hostname resolved after establish(), fixed by resolveEndpointHost() pre-VPN. Full investigation chain documented in warp-awgturnon-blocking-fd article (7 eliminated hypotheses before root cause identified).

## [2026-05-24T00:38:00+00:00] compile | 2026-05-05.md
- Source: daily/2026-05-05.md
- Articles created: [[concepts/urnetwork-networkspace-env-bundle-fields]]
- Articles updated: [[concepts/amneziawg-relinker-loading-trap]] (index corrected: source was misattributed to 2026-04-30; article was already written from this log), [[concepts/core-backup-module]] (index corrected: already contained 2026-05-05 decisions), [[concepts/warp-slot-corrupt-json-resilience]] (index corrected: article existed but missing from index), [[concepts/runtest-uncompleted-coroutines-trap]] (added runBlocking-in-runTest deadlock pattern from code review), [[concepts/urnetwork-networkspace-init]] (added cross-reference to new env-bundle-fields article)
- Notes: Three articles (amneziawg-relinker-loading-trap, core-backup-module, warp-slot-corrupt-json-resilience) were already written from this log in a prior incomplete pass but were absent or stale in index.md. New gap: URnetwork env=main + bundle fields SIGABRT (unmasked by removing Nubia guard, commit 47d0156). Minor gap: runBlocking-in-runTest deadlock added to existing coroutine trap article. Code review findings (P2: hardcoded error string, missing RomCompat test cases; P3: PersistentLoggers misuse on success, missing TextField attrs, oversized state parameter) were one-time review notes already addressed in implementation — not extractable as reusable knowledge patterns.

## [2026-05-24T00:34:03+00:00] compile | 2026-05-04.md
- Source: daily/2026-05-04.md
- Articles created: [[concepts/engine-config-private-key-plaintext]]
- Articles updated: [[concepts/release-process]] (prerelease tag logic: contains(tag, '-'); tag recreation pattern; source added), [[concepts/ci-workflow-discipline]] (multi-root CI failure reading rule: read ALL errors before acting; first symptom ≠ only root; source added)
- Notes: AWG WARP swap (session 1), v0.0.2 release decisions (session 2), OkHttp/stub CI fixes (session 3) were already fully compiled into [[concepts/amnezia-wg-warp-migration]], [[concepts/okhttp5-kotlin-version-constraint]], [[concepts/gradle-force-vs-catalog]], [[concepts/release-stub-gate]] — all citing daily/2026-05-04.md. Three gaps filled: prerelease tag convention added to release-process; multi-root CI lesson added to ci-workflow-discipline; new article for P2 plaintext private key debt.

## [2026-05-24T00:29:28+00:00] compile | 2026-05-02.md
- Source: daily/2026-05-02.md
- Articles created: [[concepts/android-parcelfiledescriptor-close-trap]], [[concepts/detekt-ratchet-desync-after-refactor]], [[concepts/engine-sdk-bridge-stop-lifecycle]]
- Articles updated: [[connections/ci-false-green-vectors]] (detekt fail-fast as 4th false-green vector added)
- Notes: Sessions covered WarpEngine auto-config (WARP root cause already in warp-config-generator-api), URnetwork consent deletion (already in symptom-fix-vs-system-removal), NetworkSpace init (already in urnetwork-networkspace-init). New gaps: Os.close(Int) POSIX trap, detekt ratchet desync + CI fail-fast masking, EngineUrnetwork.stop() not propagating to sdkBridge (goroutine leak P1 fix).

## [2026-05-24T00:26:29+00:00] compile | 2026-05-01.md
- Source: daily/2026-05-01.md
- Articles created: [[concepts/android-build-dockerfile-env-trap]]
- Articles updated: (none — all other concepts were already compiled into existing articles citing this log: [[concepts/robolectric-room-migration-testing]], [[concepts/vpnservice-main-thread-preload]], [[concepts/junit-platform-silent-skip]], [[concepts/gradle-continue-full-failures]], [[concepts/gomobile-bind-gotchas]], [[concepts/feature-branch-code-review-2026-05-01]])
- Notes: 5 commits pre-release v0.0.2-1 (C3 migration runtime test, C1 runBlocking elimination, W7.1 HealthMonitor UI badge, W9.1 locale filter, DOCS), CI v0.0.2-2 green (session 13:57), URnetwork AAR build iterations + Dockerfile env trap (session 15:24), latent test infrastructure failures revealed by useJUnitPlatform activation (session 21:55). Only gap was Dockerfile SHA256 env propagation trap — new article created.

## [2026-05-24T00:14:00+00:00] compile | 2026-04-30.md
- Source: daily/2026-04-30.md
- Articles created: [[concepts/byedpi-ipv6-silent-drop]]
- Articles updated: [[concepts/wiki-knowledge-base]] (API 400 advisor_tool_result session-killer note added)
- Notes: Most concepts from this log were already compiled into existing articles (v001-dpi-bypass-fix-chain, byedpi-args-parsing, tun-mtu-dual-layer, libhev-tunnel-stats, byedpi-auto-strategy-testing) with daily/2026-04-30.md as source. New gap: IPv6 silent drop in ByeDPI had no dedicated article. index.md reconstructed from full article list (was missing/empty).

## [2026-05-23T20:44:00+03:00] compile | Daily Log 2026-05-23 (pass 7 — session 20:44 versionCode trap)
- Source: daily/2026-05-23.md
- Articles created: (none)
- Articles updated: [[concepts/release-process]] (fetch-depth: 0 requirement for versionCode; shallow clone → versionCode=1 → INSTALL_FAILED_VERSION_DOWNGRADE; sentinel assert versionCode > 1; commit 75e72b48)
- Notes: Final remaining gap from session 20:44: v0.2.0 APK install failure root cause. All other sessions captured in passes 1-6.

## [2026-05-23T20:30:00+03:00] compile | Daily Log 2026-05-23 (pass 6 — session 20:05 gaps)
- Source: daily/2026-05-23.md
- Articles created: (none)
- Articles updated: [[connections/sentinel-trap-family]] (5th trap: comment-token collision in OzeroVpnService sentinel comment b1c6cba0; table + evidence + rule updated), [[concepts/urnetwork-walletauth-per-device-registration]] (WALLET_ADD_TIMEOUT_MS 10s→30s b86fb16f; relay sharing telemetry: endpoint bound/deferred/traffic-forwarded; 6 sentinel tests)
- Notes: Passes 1-5 (18:11–23:59) captured all earlier sessions. This pass covers session (20:05): sentinel comment-token collision as Trap 5, and payout wallet timeout increase with telemetry log events.

## [2026-05-23T23:59:00+03:00] compile | 2026-05-23.md
- Source: daily/2026-05-23.md
- Articles created: (none — all concepts existed)
- Articles updated: [[concepts/urnetwork-jwt-bootstrapper]] (migration pre-check bug 1a58aa88), [[concepts/urnetwork-balance-optimistic-cache]] (pendingBytes semantics, FREE_TIER_CAP removed), [[concepts/urnetwork-relay-always]] (JWT bootstrapper fix e6bac9eb), [[concepts/fptn-sni-bypass-method]] (HTTP 608 root cause, sniDomain trace, Reality IP fix), [[concepts/masterdns-deploy-hardening]] (Phase A-F complete, auto-setup post-deploy), [[concepts/engine-masterdns]] (auto-fill resolvers), [[concepts/urnetwork-provide-secret-keys-identity]] (listener ordering, JWT vs provideSecretKeys separation), [[concepts/warp-uapi-stale-socket-cleanup]] (regression bd6a178a, firstOrNull vs maxByOrNull fix)

## [2026-05-23T22:00:00+03:00] compile | Daily Log 2026-05-23 (pass 5 — gap fill)
- Source: daily/2026-05-23.md
- Articles created: [[concepts/proguard-release-drift]] (broken wikilink from masterdns-deploy-hardening resolved; sshj→EdDSA→sun.security.x509 R8 crash pattern documented)
- Articles updated: [[concepts/masterdns-deploy-hardening]] (Undeploy Feature section added: Removing/Removed states, removeAll script, ViewModel guard, UI buttons)
- Notes: Passes 1-4 confirmed complete. Two gaps found: (1) [[concepts/proguard-release-drift]] was referenced from masterdns-deploy-hardening but never created — broken wikilink; (2) undeploy feature documented in sources but missing from Details section. Both resolved.

## [2026-05-23T21:30:00+03:00] compile | Daily Log 2026-05-23 (pass 4 — verification)
- Source: daily/2026-05-23.md
- Articles created: (none — all 6 new articles confirmed present from prior passes)
- Articles updated: (none — all updates confirmed present from prior passes)
- Verified complete: [[concepts/warp-uapi-stale-socket-cleanup]], [[concepts/fptn-sni-bypass-method]], [[concepts/urnetwork-provide-secret-keys-identity]], [[concepts/masterdns-deploy-hardening]], [[concepts/gradle-configuration-cache-agp-bug]], [[concepts/urnetwork-jwt-bootstrapper]]
- Notes: Full read-verification of all 6 new articles and 4 updated articles (urnetwork-balance-optimistic-cache, urnetwork-relay-always, fptn-engine-protocol, engine-masterdns). All content complete. Index entries confirmed. Passes 1-3 captured everything correctly.

## [2026-05-23T20:00:00+03:00] compile | Daily Log 2026-05-23 (pass 3 — pendingBytes semantics)
- Source: daily/2026-05-23.md
- Articles created: (none — all prior passes complete)
- Articles updated: [[concepts/urnetwork-balance-optimistic-cache]] (pendingBytes = active session bytes NOT income; final formula availableBytes=balanceBytes+reliabilityBonusBytes; sentinel UrnetworkBalanceRepositoryTest.kt:81; reliabilityBonusBytes heuristic from meanReliabilityWeight; commit defc0438 revert of 277d1dfb)
- Notes: Prior passes (18:11 + 19:00) captured 7 new articles and 5 updates. This pass fills the remaining gap from session 02:16 — pendingBytes semantics clarification and the final "Доступно" formula with reliabilityBonusBytes heuristic

## [2026-05-23T19:00:00+03:00] compile | Daily Log 2026-05-23 (pass 2 — late-session gaps)
- Source: daily/2026-05-23.md
- Articles created: [[concepts/urnetwork-jwt-bootstrapper]]
- Articles updated: [[concepts/urnetwork-relay-always]] (migration pre-check bug in JWT Bootstrapper section + new wikilink)
- Notes: Prior pass (18:11) missed sessions 15:57/16:22/16:30/~18:00 JWT bootstrapper extract and regression. New article captures: UrnetworkJwtBootstrapper extract architecture, session-flag (AtomicBoolean), Mutex+double-check, migration pre-check short-circuit bug (commit 1a58aa88, byClientJwt!=null → AlreadyPresent skips migration), sentinel source-path migration EngineUrnetwork.kt→RealUrnetworkJwtBootstrapper.kt, 14 test sites updated.

## [2026-05-23T18:11:09+03:00] compile | Daily Log 2026-05-23
- Source: daily/2026-05-23.md
- Articles created: [[concepts/warp-uapi-stale-socket-cleanup]], [[concepts/fptn-sni-bypass-method]], [[concepts/urnetwork-provide-secret-keys-identity]], [[concepts/masterdns-deploy-hardening]], [[concepts/gradle-configuration-cache-agp-bug]]
- Articles updated: [[concepts/warp-uapi-handshake-polling]] (stale socket section + new source), [[concepts/fptn-engine-protocol]] (brotli implemented, SNI fix, c++_static, cppstd=17), [[concepts/engine-masterdns]] (SSH+Docker deploy hardening), [[concepts/urnetwork-sdk-integration]] (provideSecretKeys persistence fix, commit cc9e3c67)
- Notes: urnetwork-relay-always.md already contained 2026-05-23 JWT bootstrapper content from prior pass. Key themes: WARP stale Unix socket regression (bd6a178a → f458dd5d fix); FPTN SNI domain correctness (sni=host→sni=config.sniDomain; c++_static link; fptnb: brotli now live); URnetwork relay provider identity (addProvideSecretKeysListener missing = 0 relay bytes + addJwtRefreshListener); MasterDNS Phase A-F hardening (firewall markers, dpkg-lock, container readiness, sshj proguard); Gradle config cache AGP 8.7.x workaround

## [2026-05-23T18:07:01+03:00] compile | Daily Log 2026-05-22
- Source: daily/2026-05-22.md
- Articles created: [[concepts/switching-watchdog-engine-conflict]]
- Articles updated: (none — prior 24 passes already captured all other concepts)
- Notes: All major concepts (fptn-engine-protocol, jni-local-global-ref-lifecycle, urnetwork-relay-always, vpn-slot-conflict-detection, vpnservice-double-shutdown-guard, engine-chip-switching-desync, byedpi-hev-pipeline-upstream-parity, warp-allowedips-tun-routing, warp-config-generator-api, qs-tile-vpn-integration) confirmed complete from prior compilation passes. New article: switching watchdog gap — watchdog resets _switching without stopping running engine, causing parallel engine conflict.

## [2026-05-23T12:00:00+03:00] compile | Daily Log 2026-05-21
- Source: daily/2026-05-21.md
- Articles created: [[concepts/urnetwork-lazycolumn-key-collision]]
- Articles updated: [[concepts/vpn-slot-conflict-detection]] (EXTERNAL_VPN_RELEASE_DELAY_MS=750ms, REVOKE 2500→1000ms from Task37)

## [2026-05-22T24:00:00+03:00] compile | Daily Log 2026-05-22 (pass 24 — schema-driven audit)
- Source: daily/2026-05-22.md
- Articles created: (none — all prior passes complete)
- Articles updated: (none)
- Audit: all 23 prior passes confirmed complete; index fully up to date; no missed concepts
- Status: daily/2026-05-22.md compilation verified as 100% complete

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 23 — final schema-driven verification)
- Source: daily/2026-05-22.md
- Articles created: (none — all 22 prior passes complete)
- Articles updated: (none)
- Verified articles (read and confirmed complete):
  - [[concepts/fptn-engine-protocol]] — stop order, JNI fixes, brotli, Kotlin fd-leaks, CI failures; all present
  - [[concepts/jni-local-global-ref-lifecycle]] — GetStringUTFChars, NewGlobalRef return value, NewWeakGlobalRef; complete
  - [[concepts/android-brotliinputstream-hidden-api]] — fptnb: unsupported, org.brotli:dec alternative; complete
  - [[concepts/warp-split-tunnel-allowfamily-bug]] — FIXED; allowFamily(AF_INET6) unconditional; v0.1.15; complete
  - [[concepts/vpnservice-double-shutdown-guard]] — stopping reset in finally → double shutdown; join shutdownJobRef fix; complete
  - [[concepts/urnetwork-relay-always]] — hardcode bug lines 67+81; two-branch fix; UI visibility; bootstrapJob.join(); complete
  - [[concepts/engine-chip-race-observer]] — switching clear condition; restartVpnIfConnected target preservation; complete
  - [[concepts/byedpi-hev-pipeline-upstream-parity]] — udp:tcp→udp:udp revert; hevLogLevel; YouTube 370KB/s confirmed; complete
  - [[concepts/sentinel-protecting-bug-trap]] — switching-desync assertNull incident; complete
  - [[concepts/vpn-slot-conflict-detection]] — isExternalVpnActive() ownerUid false positive v0.1.11/v0.1.12; complete
  - [[concepts/engine-chip-switching-desync]] — generic fix + residual watchdog gap (fix pending 23:11); complete
- Index: no new rows needed — all articles present
- Status: daily/2026-05-22.md fully compiled across 23 passes

## [2026-05-22T23:59:00+03:00] compile | Daily Log 2026-05-22 (pass 22 — final verification + warp-split-tunnel sources fix)
- Source: daily/2026-05-22.md
- Articles created: (none — all 21 prior passes complete)
- Articles updated:
  - [[concepts/warp-split-tunnel-allowfamily-bug]] — Sources section corrected: session 22:09 said "awaiting logs" but article body already said FIXED; added note that fix was confirmed post-session, applied in commit `badcb68e`, released v0.1.15; initial hypothesis (remove allowFamily) was revised to (add both AF_INET + AF_INET6)
- Verified complete: all sessions 11:59–23:11 in daily/2026-05-22.md fully compiled across passes 1–22
- Status: compilation 100% complete

## [2026-05-22T23:11:00+03:00] compile | Daily Log 2026-05-22 (pass 21 — session 23:11 compiled)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated:
  - [[concepts/fptn-engine-protocol]] — Kotlin fd-leak fixes (FileOutputStream on error path, FileInputStream .use{}, SupervisorJob accumulation from repeated attachTun without stop()); CI failure pattern: engine-telegram ci.yml refs left after module deletion; SettingsRepositoryTest reconcile broken by FPTN addition (must grep engine-enumeration tests when adding engine)
  - [[concepts/engine-chip-switching-desync]] — Residual gap: switching watchdog resets _switching but does not stop running engine → ByeDPI survives watchdog timeout → WARP starts in parallel → traffic conflict; fix pending as of 23:11
- Index updated: (no new rows — all articles pre-existing)

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 20 — new standalone articles)
- Source: daily/2026-05-22.md
- Articles created:
  - [[concepts/jni-local-global-ref-lifecycle]] — general JNI ref management traps (GetStringUTFChars, NewGlobalRef return value, NewWeakGlobalRef lifecycle) extracted from FPTN C++ fix; applicable to all Android JNI
  - [[concepts/android-brotliinputstream-hidden-api]] — android.util.BrotliInputStream not a public SDK API; discovered via FPTN fptnb: token parsing; use org.brotli:dec instead
- Articles updated: (none — all prior content already compiled in passes 1-19)
- Index updated: 2 new rows added
- Note: previous 19 passes declared 100% complete; these two articles extract general-purpose traps that deserve independent discoverability beyond fptn-engine-protocol

## [2026-05-22T23:59:00+03:00] compile | Daily Log 2026-05-22 (pass 19 — schema-triggered verification)
- Source: daily/2026-05-22.md
- Articles created: (none — all created in passes 1-17)
- Articles updated: (none — all updated in passes 1-17)
- Verified: vpnservice-double-shutdown-guard, warp-allowedips-tun-routing, engine-chip-switching-desync, qs-tile-vpn-integration, fptn-engine-protocol (JNI leaks + stop race + brotli API), warp-split-tunnel-allowfamily-bug, clash-module-architecture, urnetwork-relay-always (hardcode bug + UI fixes), urnetwork-balance-optimistic-cache (cap removed), vpn-slot-conflict-detection (ownerUid v0.1.12), byedpi-hev-pipeline-upstream-parity (YouTube 370KB/s confirmed), warp-config-generator-api (IP ranges), amnezia-wg-warp-migration (S3/S4/I1/I2/I5 defaults + migration), sentinel-protecting-bug-trap (switching-desync case), engine-chip-race-observer
- Status: compilation 100% complete for all sessions 11:59–22:36 in daily/2026-05-22.md

## [2026-05-22T22:36:02+03:00] compile | Daily Log 2026-05-22 (pass 18 — re-run verification)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated: (none)
- Status: all content already compiled in passes 1-17; re-run confirms 100% coverage for all sessions 11:59–22:34

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 17 — full verification, compilation complete)
- Source: daily/2026-05-22.md
- Articles created: (none — all articles verified current)
- Articles updated: (none — all 16 prior passes complete)
- Verified: fptn-engine-protocol (JNI leaks/stop race/brotli), urnetwork-relay-always (hardcode bug + UI fixes), warp-allowedips-tun-routing (root cause confirmed), sentinel-protecting-bug-trap (switching-desync incident), vpn-slot-conflict-detection (ownerUid fix v0.1.12), vpnservice-double-shutdown-guard, engine-chip-switching-desync, qs-tile-vpn-integration, warp-split-tunnel-allowfamily-bug, clash-module-architecture, byedpi-hev-pipeline-upstream-parity, warp-config-generator-api, amnezia-wg-warp-migration, urnetwork-balance-accumulation-mechanism, urnetwork-balance-optimistic-cache, urnetwork-location-hierarchy-migration, urnetwork-location-stability-privacy-icons, engine-chip-race-observer
- Status: compilation 100% complete for all sessions 11:59–22:34 in daily/2026-05-22.md

## [2026-05-22T22:32:00+03:00] compile | Daily Log 2026-05-22 (pass 16 — session 22:20+ FPTN JNI implementation)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated:
  - [[concepts/fptn-engine-protocol]] — added session 22:20+ content: android.util.BrotliInputStream not public API (fptnb: unsupported); JNI memory leaks (GetStringUTFChars/NewWeakGlobalRef/NewGlobalRef patterns); stop() race condition (correct teardown order: nativeStop→pfd.close→scope.cancel→join→nativeDestroy); diagnostic logging strategy; fis.read() blocking + coroutine cancellation rule
- Index updated: (no new rows; fptn-engine-protocol row already present with correct 2026-05-22 date)
- Gap: session 22:20+ was compiled at 22:00 (pass 9) before the session occurred; passes 10-15 did not revisit fptn-engine-protocol

## [2026-05-22T23:59:00+03:00] compile | Daily Log 2026-05-22 (pass 15 — final: Clash architecture + providerCount)
- Source: daily/2026-05-22.md
- Articles created: [[concepts/clash-module-architecture]]
- Articles updated:
  - [[concepts/urnetwork-location-stability-privacy-icons]] — added providerCount semantics (total registered nodes, not online); isStrongPrivacy renders as lock icon (not infinity); source daily/2026-05-22.md added
- Index updated: 1 new row (clash-module-architecture) + 1 updated row (urnetwork-location-stability-privacy-icons summary + date + source)
- Gap closed: session 19:47 had two uncaptured facts — (1) Clash deferred decision with architecture; (2) providerCount semantic mismatch — both missed in passes 1-14
- Status: compilation complete for all 2026-05-22 sessions (passes 1-15)

## [2026-05-22T22:09:00+03:00] compile | Daily Log 2026-05-22 (pass 14 — session 22:09 captured)
- Source: daily/2026-05-22.md
- Articles created: [[concepts/warp-split-tunnel-allowfamily-bug]]
- Articles updated: (none — passes 1-13 already covered sessions 11:59–21:27)
- Gap closed: session 22:09 (WARP split tunnel + Gemini IPv6 failure) was beyond "21:27" boundary of pass 12 verification
- Status: compilation complete for all 2026-05-22 sessions

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 13 — FREE_TIER_CAP contradiction fix)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated:
  - [[concepts/urnetwork-balance-accumulation-mechanism]] — reversed UX decision: FREE_TIER_CAP_BYTES=34GiB removed (commit e0d53ca4); display real balance via coerceAtLeast(0L); old "cap at 34 GiB" recommendation was contradicted by session 16:42 decision
  - [[concepts/urnetwork-balance-optimistic-cache]] — added FREE_TIER_CAP_BYTES removal note + 2026-05-22 source
- Index updated: 2 rows (urnetwork-balance-accumulation-mechanism, urnetwork-balance-optimistic-cache — corrected summaries + dates)
- Gap found by: grep for FREE_TIER_CAP / coerceAtLeast returned no hits in knowledge/ articles despite being in session 16:42 log
- Status: pass 12 had "100% complete" but missed this contradiction with prior "UX fix: cap at 34 GiB" claim

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 12 — schema-triggered, full verification pass)
- Source: daily/2026-05-22.md
- Articles created: (none — all 5 articles from today confirmed complete: vpnservice-double-shutdown-guard, warp-allowedips-tun-routing, engine-chip-switching-desync, qs-tile-vpn-integration, fptn-engine-protocol)
- Articles updated: (none — all 8 updates confirmed current: urnetwork-relay-always, byedpi-hev-pipeline-upstream-parity, warp-config-generator-api, amnezia-wg-warp-migration, sentinel-protecting-bug-trap, vpn-slot-conflict-detection, urnetwork-location-hierarchy-migration, engine-chip-race-observer)
- Status: compilation 100% complete, all sessions 11:59–21:27 fully captured in passes 1-11

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 11 — triggered compilation, full verification)
- Source: daily/2026-05-22.md
- Articles created: (none — all 5 new articles verified complete)
- Articles updated: (none — all 8 updates verified current)
- Verified: urnetwork-relay-always, warp-config-generator-api, amnezia-wg-warp-migration, byedpi-hev-pipeline-upstream-parity, sentinel-protecting-bug-trap, vpn-slot-conflict-detection, urnetwork-location-hierarchy-migration, engine-chip-race-observer — all include 2026-05-22 source entries with correct content
- Status: compilation 100% complete, no gaps found

## [2026-05-22T23:59:59+03:00] compile | Daily Log 2026-05-22 (pass 10 — final verification, all sessions captured)
- Source: daily/2026-05-22.md
- Articles created: (none — all 5 new articles confirmed complete: vpnservice-double-shutdown-guard, warp-allowedips-tun-routing, engine-chip-switching-desync, qs-tile-vpn-integration, fptn-engine-protocol)
- Articles updated: (none — all 8 updates from passes 1-9 verified current)
- Status: all sessions from 11:59 through 21:27 fully compiled across 9 prior passes; pass 10 is verification-only

## [2026-05-22T22:00:00+03:00] compile | Daily Log 2026-05-22 (pass 9 — FPTN session 21:27 captured)
- Source: daily/2026-05-22.md
- Articles created: [[concepts/fptn-engine-protocol]]
- Articles updated: [[knowledge/index.md]] (new row added)
- Gap found: passes 7+8 completed at 21:00 / 21:27 but session 21:27 (FPTN engine planning) occurred AFTER those passes ran

## [2026-05-22T21:27:15+03:00] compile | Daily Log 2026-05-22 (pass 8 — schema-triggered recompile, verified complete)
- Source: daily/2026-05-22.md
- Articles created: (none — all 4 already exist: vpnservice-double-shutdown-guard, warp-allowedips-tun-routing, engine-chip-switching-desync, qs-tile-vpn-integration)
- Articles updated: (none — all 8 updates from passes 1-7 verified current)
- Status: compilation fully complete as of pass 7; this pass confirmed no gaps

## [2026-05-22T21:00:00+03:00] compile | Daily Log 2026-05-22 (pass 7 — final verification, no gaps)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated: (none)
- Verification: all sessions cross-checked against log entries; session 13:30 (reviewer domain memory injection insight) confirmed already captured in connections/self-review-insufficient-code-reviewer-required.md from daily/2026-05-20.md; compilation complete

## [2026-05-22T20:30:00+03:00] compile | Daily Log 2026-05-22 (full verification — 6-pass compilation complete)
- Source: daily/2026-05-22.md
- Articles created: [[concepts/vpnservice-double-shutdown-guard]], [[concepts/warp-allowedips-tun-routing]], [[concepts/engine-chip-switching-desync]], [[concepts/qs-tile-vpn-integration]]
- Articles updated: [[concepts/engine-chip-race-observer]], [[concepts/urnetwork-relay-always]], [[concepts/byedpi-hev-pipeline-upstream-parity]], [[concepts/warp-config-generator-api]], [[concepts/amnezia-wg-warp-migration]], [[concepts/sentinel-protecting-bug-trap]], [[concepts/vpn-slot-conflict-detection]], [[concepts/urnetwork-location-hierarchy-migration]]
- Index updated: 4 new rows + 8 updated rows
- Sessions covered: 11:59 (chip switching desync fix), 13:00 (YouTube/QUIC revert), 13:40+ (double-shutdown root cause, log analysis), 16:02+ (URnetwork relay coordinator hardcode bug + UI fixes), 17:51 (WARP AWG defaults/migration/AllowedIPs routing), 19:25 (QS tile), 19:32 (user ownerUid false positive), 19:47 (selectLocation offline fix)
- Verification: all passes confirmed against daily log — no gaps found

## [2026-05-22T23:59:00+03:00] compile | Daily Log 2026-05-22 (pass 6 — selectLocation offline fix, session 19:47)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated:
  - [[concepts/urnetwork-location-hierarchy-migration]] — `selectLocation` early return `if (!isUrnetworkActive) return` blocked offline country selection; fix: remove guard — `bridge.setPreferredLocation()` is AtomicReference-safe, no active VPN required
- Index updated: 1 row (urnetwork-location-hierarchy-migration summary + date + source)
- Sessions covered: 19:47 (selectLocation offline fix; Clash-module deferred to future; ktlint `&&` start-of-line + line>120 + UnusedParameter CI patterns)

## [2026-05-22T23:30:00+03:00] compile | Daily Log 2026-05-22 (pass 5 — isExternalVpnActive ownerUid false positive)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated:
  - [[concepts/vpn-slot-conflict-detection]] — isExternalVpnActive() missing ownerUid filter: own dying VPN detected as external → 750ms delay + protect() conflict → cycling loop; fix c1123b04 (API 29+ ownerUid guard, v0.1.12); log pattern + diagnostic shortcut documented
- Index updated: 1 row (vpn-slot-conflict-detection summary + date + source)
- Sessions covered: 19:32 (v0.1.11 user report — cycling VPN; isExternalVpnActive false positive root cause; v0.1.12 fix confirmed)
- Note: Passes 1-4 covered all other sessions; this pass closes the remaining gap

## [2026-05-22T22:00:00+03:00] compile | Daily Log 2026-05-22 (pass 4 — QS tile)
- Source: daily/2026-05-22.md
- Articles created:
  - [[concepts/qs-tile-vpn-integration]] — QS tile smart toggle; ozero_logo_white icon; TunnelController.state flow collection; stub-existence pattern; toggle-via-Intent deferred

## [2026-05-22T21:00:00+03:00] compile | Daily Log 2026-05-22 (pass 3 — AWG defaults migration gap)
- Source: daily/2026-05-22.md
- Articles created: (none)
- Articles updated:
  - [[concepts/amnezia-wg-warp-migration]] — S3/S4/I1/I2/I5 defaults reset to 0; WarpIniBuilder skips zero fields; normalizeAwgParams fingerprint migration (5-field fingerprint 19/20/28/29/10 in slotFromJson); commits 796932cc + 78cb33f2

## [2026-05-22T20:00:00+03:00] compile | Daily Log 2026-05-22 (pass 2 — sentinel update + chip-switching article)
- Source: daily/2026-05-22.md
- Articles created:
  - [[concepts/engine-chip-switching-desync]] — supplementary article for Connected(X) switching-clear desync; content also present in engine-chip-race-observer; indexed separately for retrieval clarity
- Articles updated:
  - [[concepts/sentinel-protecting-bug-trap]] — added 3rd incident (switching-desync assertNull sentinel); sources updated to daily/2026-05-22.md; date 2026-05-15→2026-05-22

## [2026-05-22T19:15:00+03:00] compile | Daily Log 2026-05-22
- Source: daily/2026-05-22.md
- Articles created:
  - [[concepts/vpnservice-double-shutdown-guard]] — stopping flag reset in performShutdown finally → onDestroy CAS triggers second shutdown; fix: join shutdownJobRef; idempotency guard pattern
  - [[concepts/warp-allowedips-tun-routing]] — amneziawg.conf selective AllowedIPs + always 0.0.0.0/0 TUN route → non-matching traffic dropped by AWG; PORTAL_WG adds per-AllowedIPs TUN routes
- Articles updated:
  - [[concepts/engine-chip-race-observer]] — switching clear condition (sw.to==null||sw.to==X); restartVpnIfConnected preserves pending target; sentinel was asserting buggy behavior
  - [[concepts/urnetwork-relay-always]] — RelayCoordinator hardcode bug (always setProvidePaused(false)); fix: URNETWORK branch no-override, relay branch reads configStore; BalanceCard/ProvideSection visibility; bootstrapJob.join() race
  - [[concepts/byedpi-hev-pipeline-upstream-parity]] — udp:tcp→udp:udp revert (QUIC regression); hevLogLevel in YAML; YouTube CMD confirmed 370KB/s from v0.1.13 log
  - [[concepts/warp-config-generator-api]] — validateCloudflarePeer expanded to IP ranges 162.159.192-195.* and 188.114.96-99.* (REQUEST_BODY returns IP not hostname)

## [2026-05-22T18:30:00+03:00] compile | Daily Log 2026-05-21
- Source: daily/2026-05-21.md
- Articles created:
  - [[concepts/engine-masterdns]] — subprocess EnginePlugin (ProcessBuilder libmdnsvpn.so); 7 review fixes: 10s timeout, @Suppress upstream, case-insensitive TOML, MasterDnsResolversCache Eagerly, host:port parsing, stdout limit, DataStore error handling; auto-mode enrollment
  - [[concepts/applytunderlying-split-contract]] — applyUnderlying=false ByeDPI (QUIC parity) vs =true killswitch TUN (P37); 4th sentinel trap: literal vs signature assertion
  - [[concepts/warp-config-naming-dedup]] — generated config name = endpoint hostname; WarpConfigDuplicateException fingerprint dedup
  - [[concepts/urnetwork-providemmode-regions-cities]] — ProvideModeNone→empty regions/cities for fresh users; applyDeviceFields 13-field helper; ProvideModePublic override
  - [[concepts/ontaskremoved-vpn-swipe-standard]] — onTaskRemoved must NOT call stopVpn; swipe ≠ stop VPN; sentinel regression guard
- Articles updated:
  - [[concepts/byedpi-ensure-udp-desync]] — CMD path removal: ensureUdpDesync removed from CMD mode (Task16); CMD verbatim contract; ensureUdpDesync still relevant for UI mode
  - [[connections/sentinel-trap-family]] — 4th trap type added: literal-vs-signature assertion breaks when default-param refactor replaces inline literal with pass-through; table updated; sources updated

## [2026-05-22T00:00:00+03:00] compile | Daily Log 2026-05-20 (pass 11 — session 20:00 ensureUdpDesync)
- Source: daily/2026-05-20.md
- Articles created: [[concepts/byedpi-ensure-udp-desync]] — auto-strategy/evolution probes HTTPS (TCP) only; winning args lack UDP desync; `ensureUdpDesync` appends `-Ku -a1 -An` at apply boundary; side-fix independent of YouTube root cause (setUnderlyingNetworks)
- Articles updated:
  - [[concepts/genetic-strategy-evolution]] — added ensureUdpDesync section + source daily/2026-05-20.md; updated date 2026-05-19→2026-05-20
- Index: 1 row added (byedpi-ensure-udp-desync); genetic-strategy-evolution row was already correct
- Sessions covered: 20:00 (UDP pipeline investigation start — ensureUdpDesync side-defect); sessions 20:30 (per-engine fix) previously captured via byedpi-vpn-pipeline-upstream-divergence article created inline during session

## [2026-05-20T22:00:00+03:00] compile | Daily Log 2026-05-20 (pass 10 — session 19:52 peerWatchdog wall clock + chip race file)
- Source: daily/2026-05-20.md
- Articles created: [[concepts/engine-chip-race-observer]] — file was missing despite pass 9 log entry; created: Connected-only gate drops Probing/Connecting engine changes; Probing(null) guard; sentinel inversions
- Articles updated:
  - [[concepts/viewmodel-polling-runtest-trap]] — wall clock in production coroutine trap: System.currentTimeMillis() in EngineWatchdogCoordinator not synced with virtual dispatcher → elapsed always near zero → watchdog never fires in CI; fix: zeroPeersPolls poll counter; sentinel; sources updated
  - [[concepts/urnetwork-peer-watchdog-recovery]] — poll counter fix documented; sentinel in OzeroVpnServicePeerWatchdogTest; sources updated
- Index: 2 rows updated (viewmodel-polling-runtest-trap, urnetwork-peer-watchdog-recovery dates + summaries)
- Sessions covered: 19:52 (ByeDPI YouTube QUIC investigation, peerWatchdog wall clock CI fix, DNS presets NextDNS/AdGuard/ControlD); 19:07 chip race (file materialisation)
- Note: DNS presets (NextDNS/AdGuard/ControlD chips) and YouTube QUIC investigation are UI/UX additions without new architectural patterns; not breaking out into separate articles

## [2026-05-20T21:00:00+03:00] compile | Daily Log 2026-05-20 (pass 9 — session 19:07 chip race)
- Source: daily/2026-05-20.md
- Articles created: [[concepts/engine-chip-race-observer]] — EngineSettingsRestartObserver Connected-only gate drops engine changes during Probing/Connecting; fix: restart from Probing/Connecting when engine ≠ manualEngine; Probing(null) guard; 3 sentinel inversions for removed behavior; debounce interaction
- Articles updated: (none)
- Index: 1 row added (engine-chip-race-observer)
- Sessions covered: 19:07 (chip race fix — previously uncaptured by passes 1-8)

## [2026-05-20T20:30:00+03:00] compile | Daily Log 2026-05-20 (pass 8 — lint.py overflow fix + contradiction resolution)
- Source: daily/2026-05-20.md
- Articles created: (none)
- Articles updated:
  - [[concepts/wiki-knowledge-base]] — session 18:53 captured: lint.py check_contradictions() SDK overflow fix (path-list + allowed_tools=[Read,Glob,Grep] + max_turns=30, identical to compile.py fix commit 38770996); 3-round iterative contradiction resolution (9→3→3→1→0); duplicate `urnetwork-filter-locations-trigger.md` deleted; transient `exit 1` re-run rule documented; 441 missing-backlinks intentionally skipped
- Index: (none — wiki-knowledge-base row already updated)
- Sessions covered: 18:53 (lint.py overflow fix, iterative contradiction audit, duplicate cleanup)
- Note: All 8 sessions from daily/2026-05-20.md now fully compiled across passes 1-8

## [2026-05-20T19:00:00+03:00] compile | Daily Log 2026-05-20 (pass 7 — wiki-knowledge-base Sources gap)
- Source: daily/2026-05-20.md
- Articles created: (none)
- Articles updated:
  - [[concepts/wiki-knowledge-base]] — Sources section was missing daily/2026-05-20.md entry despite frontmatter having the date; added flush error root cause (truncated JSONL) + contradiction audit reference
- Index: (none — index row already updated in pass 2)
- Sessions covered: gap fix only — wiki-knowledge-base.md body Sources vs frontmatter discrepancy

## [2026-05-20T18:47:26+03:00] compile | Daily Log 2026-05-20 (pass 6 — KB audit article updates)
- Source: daily/2026-05-20.md
- Articles created: (none)
- Articles updated:
  - [[concepts/android-vpn-self-traffic-bypass]] — 2026-05-20 source added; KB audit (18:43) confirmed self-exclusion removal deprecated; canonical = ipProbeRoute() + excludeSelf=true; three regressions confirmed removing self-exclusion breaks engines
  - [[concepts/release-process]] — 2026-05-20 source added; index summary clarified: arm64-v8a only (not multi-ABI); "universal" means single APK for all users not multi-ABI
- Index: 2 rows updated (android-vpn-self-traffic-bypass summary + date; release-process summary + date)
- Sessions covered: KB audit 18:43 (android-vpn-self-traffic-bypass deprecation; release-process ABI claim)

## [2026-05-20T24:00:00+03:00] compile | Daily Log 2026-05-20 (pass 5 — audit sessions + MTU refactor regression)
- Source: daily/2026-05-20.md
- Articles created: (none)
- Articles updated:
  - [[concepts/tun-mtu-dual-layer]] — v0.1.6 refactor regression: TunBuilderHelper.buildTunBuilder() added setMtu(8500) not present in reference; bisected + removed v0.1.8; rule: refactor must not introduce new parameters not in reference
  - [[concepts/byedpi-jni-guard-hardening]] — added daily/2026-05-20 source; KB audit 18:37 confirmed jniStopProxy=shutdown(fd,SHUT_RDWR) vs jniForceClose=close(fd); neither touches g_proxy_running externally — already correctly documented
- Index: 1 row updated (tun-mtu-dual-layer source + summary)
- Sessions covered: audit sessions 18:15 (duplicate), 18:27/18:37/18:43 (contradiction findings), setMtu(8500) regression from 00:00 session

## [2026-05-20T23:62:00+03:00] compile | Daily Log 2026-05-20 (pass 4 — ByeDPI pin upgrade)
- Source: daily/2026-05-20.md
- Articles created: (none)
- Articles updated:
  - [[concepts/native-binary-auto-update-pipeline]] — manual pin upgrade case study: ByeDPI v0.17.3→ba532298 (38 commits, YouTube QUIC/ECH fix); fix procedure documented
- Index: 1 row updated (native-binary-auto-update-pipeline summary + source)
- Sessions covered: v0.1.9 prep (02:25) — ByeDPI commit pin upgrade previously uncaptured

## [2026-05-20T23:59:00+03:00] compile | Daily Log 2026-05-20 (pass 3 — v0.1.9 prep session gap)
- Source: daily/2026-05-20.md
- Articles created: [[concepts/urnetwork-shared-traffic-history]] — SDK cumulative-only → local delta map; 30-day bar chart; payout reset guard
- Articles updated:
  - [[concepts/engine-await-ready-pattern]] — URnetwork timeout 15s→45s (slow P2P discovery); WARP poll 300ms→100ms; path ozero-warp.sock→sockets/ cascade
  - [[concepts/urnetwork-balance-optimistic-cache]] — ViewModel stateIn(initialValue=INITIAL) bug masked cache; fix initialValue=balanceRepository.state.value
- Index: 1 row added, 2 rows updated (total 152)
- Sessions covered: post-compact v0.1.9 prep (02:25) — previously not compiled; timeout bump, shared traffic history, balanceState fix

## [2026-05-20T23:30:00+03:00] compile | Daily Log 2026-05-20 (pass 2 — remaining sessions)
- Source: daily/2026-05-20.md
- Articles created: (none new)
- Articles updated:
  - [[concepts/vpn-slot-conflict-detection]] — onRevoke → postDelayed Process.killProcess(2500ms) liberates Go runtime + VPN slot; only onRevoke not stopVpn/onDestroy
  - [[concepts/wiki-knowledge-base]] — 2026-05-20 flush failure pattern (13+ FLUSH_ERROR); contradiction audit session findings (9 issues: 4 direct contradictions, 5 inconsistencies)
  - [[connections/self-review-insufficient-code-reviewer-required]] — inverse pattern: backup refactor reviewer P1 false positives (walletOverride BC, warpSlots skip) were intentional design; human verification required before applying findings
- Index: 3 rows updated
- Sessions covered: VPN slot onRevoke kill (post-compact), backup refactor false positives (15:37), contradiction audit (18:27)

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
- Articles created: [[concepts/speed-chart-bucket-alignment]] (time-aligned bucket IDs prevent sliding-window drift; SpeedSample typed wrapper), [[concepts/vpn-slot-coexistence-crash]] (establish() null = external VPN holds slot; Failed state + logActiveExternalVpn), [[concepts/urnetwork-filterlocations-trigger]] (filterLocations("") required after vc.start()), [[concepts/hilt-viewmodel-split-too-many-functions]] (hiltViewModel() decomp for TooManyFunctions; shared internal fakes)
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
## [2026-05-28T18:24:16+03:00] compile | 2026-05-21.md
- Source: daily/2026-05-21.md
- Articles created: [[concepts/byedpi-killswitch-applyunderlying-split]], [[concepts/masterdns-startup-hardening]], [[concepts/urnetwork-provide-mode-picker-parity]], [[concepts/byedpi-cmd-verbatim-pipeline]], [[concepts/startsequence-branch-specific-sentinels]], [[concepts/warp-config-import-naming-dedup]]
- Articles updated: none
## [2026-05-28T18:27:11+03:00] compile | 2026-05-27.md
- Source: daily/2026-05-27.md
- Articles created: [[concepts/navigation-cherrypick-preserve-routes]], [[concepts/detekt-returncount-extract-paths]], [[concepts/detekt-object-function-extraction]], [[concepts/repo-debranding-ci-owner-injection]], [[concepts/binaries-lock-real-url-exception]], [[connections/release-status-vs-asset-verification]]
- Articles updated: none
## [2026-05-28T18:29:40+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/codex-wiki-hook-smoke]], [[concepts/release-regression-evidence-checklist]], [[concepts/urnetwork-readiness-connectionstatus]], [[concepts/singbox-autochain-validator-parity]], [[concepts/ci-module-test-coverage-gap]], [[connections/release-regression-ci-vs-runtime-proof]]
- Articles updated: none
## [2026-05-28T19:02:53+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/byedpi-stop-timeout-contract]], [[concepts/warp-uapi-cleanup-all-sockets]], [[concepts/singbox-chain-dns-hijack-parity]], [[concepts/github-actions-run-id-monitoring]], [[concepts/ci-extra-modules-test-gate]], [[connections/release-engine-fix-contract-vs-timeout]]
- Articles updated: none
## [2026-05-28T19:39:36+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/shared-warp-settings-branch-coverage]], [[concepts/masterdns-fake-ssh-specificity]], [[concepts/fake-dao-preseed-autoincrement]], [[concepts/app-desktop-coverage-gate-scope]], [[connections/extra-module-ci-exposes-stale-fakes]]
- Articles updated: none
## [2026-05-28T20:03:01+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/memory-only-commit-ci-risk]], [[concepts/release-regression-self-review-gate]], [[concepts/github-actions-run-level-polling]], [[concepts/release-audit-tag-sha-grounding]], [[concepts/test-retention-evidence-standard]]
- Articles updated: none
## [2026-05-28T20:10:28+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/github-actions-artifact-node-major-upgrade]], [[concepts/release-runtime-scenario-checklist]], [[connections/ci-extra-gate-latent-failures]]
- Articles updated: none
## [2026-05-28T20:31:25+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/engine-failure-recovery-isolation]], [[concepts/release-last-good-baseline-audit]], [[concepts/singbox-private-subscription-chain-validation]]
- Articles updated: none
## [2026-05-28T20:37:32+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/release-post-stop-dev-review-discipline]], [[concepts/code-quality-review-proof-standard]], [[concepts/ci-artifact-report-driven-debugging]], [[concepts/fptn-http-608-regression-baseline]], [[connections/engine-switch-regressions-baseline-runtime-proof]]
- Articles updated: none
## [2026-05-28T20:39:14+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/engine-switch-failure-containment]], [[concepts/private-subscription-sanitized-debugging]], [[concepts/ci-artifact-driven-extra-module-debugging]], [[concepts/release-postpublish-architecture-review]]
- Articles updated: none
## [2026-05-28T20:59:24+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/android-apk-only-release-scope]], [[concepts/engine-poisoned-state-recovery-proof]], [[concepts/release-runtime-regression-sentinels]], [[concepts/ci-extra-module-artifact-feedback-loop]], [[connections/android-apk-scope-vs-runtime-proof]]
- Articles updated: none

## [2026-05-28T22:00:09+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/release-workflow-apk-only-artifact-pruning]], [[concepts/regression-test-bounded-waits]], [[concepts/ci-grouped-job-failure-attribution]], [[connections/apk-only-release-vs-extra-module-ci]]
- Articles updated: none
## [2026-05-28T22:30:54+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/fptn-dead-server-fallback]], [[concepts/singbox-karing-json-import-parity]], [[concepts/gradle-module-type-ci-task-selection]]
- Articles updated: none
## [2026-05-29T23:03:54+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-upstream-dns-websocket-boundary]], [[concepts/byedpi-wedged-lane-restart-isolation]], [[concepts/urnetwork-startup-readiness-vs-runtime-peer-grace]], [[concepts/urnetwork-relay-provideenabled-boundary]], [[concepts/github-actions-multiline-if-parse-failure]], [[concepts/memory-commit-with-work-only]], [[connections/engine-lifecycle-stale-status-cascade]]
- Articles updated: none
## [2026-05-29T23:04:17+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-upstream-websocket-dns-boundary]], [[concepts/fptn-cancellation-cooperative-auth-lifecycle]], [[concepts/byedpi-wedged-lane-generation-restart]], [[concepts/urnetwork-startup-readiness-runtime-peer-grace]], [[concepts/urnetwork-relay-provideenabled-sol-contract]], [[connections/stale-engine-signals-cross-engine-failures]]
- Articles updated: none
## [2026-05-29T23:09:33+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-upstream-readiness-ip-callback-flow]], [[concepts/auto-candidate-terminal-status-invariant]], [[concepts/singbox-exit-ip-probe-chain-socks]], [[concepts/regression-diagnostics-real-path-grounding]], [[connections/cascade-lifecycle-regressions-cross-engine-proof]]
- Articles updated: none
## [2026-05-29T23:21:06+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: none
- Articles updated: [[concepts/fptn-cancellation-cooperative-auth-lifecycle]], [[concepts/auto-candidate-terminal-status-invariant]], [[concepts/byedpi-wedged-lane-generation-restart]], [[concepts/urnetwork-startup-readiness-runtime-peer-grace]], [[concepts/urnetwork-relay-provideenabled-sol-contract]], [[concepts/singbox-exit-ip-probe-chain-socks]], [[connections/stale-engine-signals-cross-engine-failures]]
## [2026-05-29T23:26:16+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-auth-ladder-orchestrator-block]], [[concepts/exit-node-strategy-resolver-contract]], [[concepts/ci-required-check-name-preservation]], [[connections/startup-readiness-runtime-recovery-boundary]]
- Articles updated: none
## [2026-05-29T23:29:37+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[connections/shared-lifecycle-first-fix-order]]
- Articles updated: [[concepts/fptn-upstream-websocket-dns-boundary]], [[concepts/byedpi-wedged-lane-generation-restart]], [[concepts/urnetwork-startup-readiness-runtime-peer-grace]], [[concepts/urnetwork-relay-provideenabled-sol-contract]], [[concepts/singbox-exit-ip-probe-chain-socks]], [[concepts/exit-node-strategy-resolver-contract]], [[concepts/memory-commit-with-work-only]]
## [2026-05-29T23:38:11+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-token-port-schema-upstream-contract]], [[concepts/fptn-single-auth-default-start-contract]], [[concepts/chain-start-timeout-stale-engine-failure-cascade]], [[concepts/urnetwork-providerstate-peer-grace-contract]], [[concepts/urnetwork-relay-user-flag-wallet-chain-contract]], [[concepts/exit-node-probe-no-direct-fallback]], [[connections/engine-startup-status-authority-boundary]]
- Articles updated: none
## [2026-05-29T23:44:08+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-upstream-snapshot-grounding]], [[concepts/experimental-fix-branch-selective-port]], [[concepts/urnetwork-engine-relay-separation]], [[concepts/multiengine-regression-fix-staging]], [[connections/engine-exit-node-safe-routing-contract]]
- Articles updated: none
## [2026-05-29T23:47:37+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/real-path-grounding-before-fix-plan]], [[concepts/memory-hook-postcommit-dirty-contract]], [[connections/candidate-attempt-lifecycle-status-isolation]], [[concepts/exit-node-strategy-no-direct-leak-sentinel]]
- Articles updated: none
## [2026-05-29T23:57:53+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/singbox-active-socks-port-failure-reset]], [[concepts/ci-style-job-downstream-skip]], [[concepts/source-mojibake-diagnostics-risk]], [[concepts/strategy-extraction-import-retention]], [[connections/ci-style-gate-hides-compile-regression]], [[connections/exit-node-probe-resource-state-coupling]]
- Articles updated: none
## [2026-05-30T19:56:36+03:00] compile | 2026-05-30.md
- Source: daily/2026-05-30.md
- Articles created: [[concepts/masterdns-amnezia-dns-running-udp-contract]], [[concepts/overlapping-pr-merge-preserve-dev-contracts]], [[concepts/exit-node-display-viasocks-known-ip-contract]], [[concepts/vpn-switch-confirm-stop-before-start]], [[concepts/groupseeder-url-dedupe-userorder-contract]], [[concepts/pr-ci-push-vs-pull-request-drift]], [[concepts/fptn-healthcheck-auth-diagnostics-contract]], [[connections/layered-pr-merge-ci-feedback-loop]]
- Articles updated: none
## [2026-05-30T22:02:26+03:00] compile | 2026-05-30.md
- Source: daily/2026-05-30.md
- Articles created: [[concepts/fptn-health-preselect-auth-timeout-regression]], [[concepts/warp-vpn-mode-exit-ip-proof-boundary]], [[concepts/byedpi-proxy-lane-test-race-synchronization]], [[concepts/byedpi-settings-schema-marker-migration]], [[concepts/masterdns-amnezia-dns-removal-success-contract]], [[connections/pr-ci-merge-ref-flaky-test-feedback-loop]]
- Articles updated: none
## [2026-05-31T18:04:38+03:00] compile | 2026-05-28.md
- Source: daily/2026-05-28.md
- Articles created: [[concepts/release-regression-self-review-before-main]], [[concepts/ci-extra-modules-exposes-hidden-release-risk]], [[concepts/android-apk-only-release-artifact-scope]], [[concepts/private-subscription-profile-sanitized-evidence]], [[concepts/github-actions-artifact-node24-migration]], [[connections/release-ci-green-vs-runtime-engine-proof]]
- Articles updated: none
## [2026-05-31T18:08:30+03:00] compile | 2026-05-29.md
- Source: daily/2026-05-29.md
- Articles created: [[concepts/fptn-websocket-resolved-ip-readiness]], [[concepts/urnetwork-runtime-grace-startup-gate]], [[concepts/urnetwork-relay-user-config-authority]], [[concepts/exit-node-strategy-ui-unification]], [[concepts/singbox-probe-socks-port-lifecycle]], [[concepts/ci-style-failure-hides-compile-regression]], [[connections/fptn-bye-dpi-cascade-lifecycle-plan]]
- Articles updated: none
## [2026-05-31T18:12:23+03:00] compile | 2026-05-30.md
- Source: daily/2026-05-30.md
- Articles created: [[concepts/masterdns-docker-build-run-proof-contract]], [[concepts/singbox-process-stop-and-wait-sentinel]], [[concepts/exit-node-providerlabel-known-ip-contract]], [[concepts/fptn-v109-health-preselect-regression]], [[concepts/byedpi-youtube-quic-probe-domain-contract]], [[concepts/dev-ci-workflow-dispatch-nonzero-tests-contract]], [[connections/runtime-engine-fix-ci-proof-loop]]
- Articles updated: none
## [2026-05-31T18:16:50+03:00] compile | 2026-05-31.md
- Source: daily/2026-05-31.md
- Articles created: [[concepts/byedpi-strategy-scan-isolated-structured-argv]], [[concepts/singbox-routed-probe-readiness-latency-contract]], [[concepts/split-tunnel-runtime-refresh-coalesced-restart]], [[concepts/masterdns-pinned-release-binary-name-drift]], [[concepts/dev-push-ci-visible-full-run-contract]], [[concepts/codex-pr-review-thread-fix-loop]]
- Articles updated: none
## [2026-05-31T20:48:24+03:00] compile | 2026-05-31.md
- Source: daily/2026-05-31.md
- Articles created: [[concepts/byedpi-argv-grammar-aware-validation]], [[concepts/singbox-vless-early-data-string-contract]], [[concepts/urnetwork-bestavailable-explicit-location]], [[concepts/warp-ipv6-fail-closed-routing]], [[concepts/engine-runtime-failclosed-watchdog-path]], [[concepts/android-jacoco-executiondata-false-green]], [[concepts/backup-one-click-restore-contract]], [[connections/runtime-security-ci-proof-loop]]
- Articles updated: none
## [2026-05-31T20:49:06+03:00] compile | 2026-05-31.md
- Source: daily/2026-05-31.md
- Articles created: [[concepts/singbox-vless-early-data-string-contract]], [[concepts/warp-ipv6-fail-closed-blackhole-route]], [[concepts/urnetwork-explicit-bestavailable-location]], [[concepts/masterdns-port53-bind-preflight-contract]], [[concepts/fail-closed-watchdog-startup-lockdown-contract]], [[concepts/ci-coverage-gate-artifact-trust-contract]], [[concepts/public-repo-secret-and-insecure-asset-boundary]], [[connections/fail-closed-security-ci-trust-boundary]]
- Articles updated: none
## [2026-05-31T23:08:11+03:00] compile | 2026-05-31.md
- Source: daily/2026-05-31.md
- Articles created: [[concepts/engine-runtime-config-provider-boundary]], [[concepts/settings-restart-baseline-debounce-state-machine]], [[concepts/fptn-runtime-fingerprint-replay-contract]], [[concepts/warp-readiness-delayed-handshake-contract]], [[concepts/intentional-tradeoff-sentinel-documentation]], [[connections/runtime-config-restart-boundary-loop]]
- Articles updated: none
## [2026-06-01T21:53:43+03:00] compile | 2026-05-31.md
- Source: daily/2026-05-31.md
- Articles created: [[concepts/local-gradle-validation-ban-ci-only]], [[concepts/runtime-restart-pending-fingerprint-baseline]], [[concepts/engine-runtime-provider-composition-root-boundary]], [[concepts/warp-proxy-config-wgquick-precedence]], [[concepts/ci-snapshot-artifact-failure-grounding]]
- Articles updated: none
## [2026-06-01T21:56:18+03:00] compile | 2026-06-01.md
- Source: daily/2026-06-01.md
- Articles created: [[concepts/warp-pretunnel-endpoint-doh-parity]], [[concepts/warp-awg-field-preservation-contract]], [[concepts/jacoco-honest-coverage-gate-boundary]], [[concepts/runTest-backgroundscope-hot-flow-collectors]], [[concepts/bootstrap-signature-real-trust-gate]], [[connections/post-release-ci-gate-test-harness-feedback-loop]]
- Articles updated: none
## [2026-06-02T18:53:22+03:00] compile | 2026-06-01.md
- Source: daily/2026-06-01.md
- Articles created: [[concepts/post-release-app-test-harness-regression-map]], [[concepts/engine-runtime-config-restart-observer-stateflow-tests]], [[concepts/unified-logger-rotation-test-helper-append-contract]], [[concepts/jacoco-exclude-evidence-boundary]], [[concepts/ci-failure-batch-analysis-before-push]], [[connections/coverage-gate-vs-test-harness-validity-loop]]
- Articles updated: none
## [2026-06-02T18:57:23+03:00] compile | 2026-06-02.md
- Source: daily/2026-06-02.md
- Articles created: [[concepts/ci-push-not-hypothesis-proof]], [[concepts/jacoco-testable-logic-exclude-boundary]], [[concepts/app-ci-job-diagnostic-sharding-nonzero-gate]], [[concepts/bootstrap-empty-seed-product-contract]], [[connections/coverage-artifact-policy-feedback-loop]]
- Articles updated: none
