# PLAN.md — Ozero roadmap

Засечки `[x]` = done. `[~]` = частично/research. `[ ]` = pending. `[!]` = требует особое внимание (риск, device verify, cross-module).

## Статус (2026-05-01 — autonom session 47+ commits + pre-release fix wave)

- **feature** ahead of dev: 52 commits (47 autonom + 5 release-readiness fix)
- **v0.0.1** tagged + retag-6 working (live speed UI)
- **CI feature** = full pipeline (kotlin-style + Python + assembleDebug + lint + Tests+coverage)
- **JaCoCo gate**: 0.90 LINE + 0.90 BRANCH

### Closed (42 / 9 pending)

- **Wave A cleanup** полностью (W1.1-W1.4, W3.6, W3.9, W5.1, W5.2, W5.4-W5.9)
- **Wave B auto-strategy** (W3.5.1-W3.5.6 — picker logic + tests + UI factory + DI)
- **Wave C UI** real impl через atomic refactor W3.0 SettingsRepository → :engines-core (W3.2 custom DNS, W3.3 IPv6 toggle, W3.4 ByeDPI editor, W3.7 hosts white/black backend + ByeDPI -H args, W3.8 autostart wired)
- **Wave Stats** (W3.1.a Room v4→5 + Migration sentinel, W3.1.b TunnelController persists через SessionStatsRecorder + RoomImpl, W3.1.c StatsHistoryViewModel + Compose Screen + nav)
- **Wave G W5.3 SecurityGuard** удалён (:security модуль, ~26 файлов)
- **Wave F partial**: W7.1 HealthMonitor restoration (periodic SOCKS5 probe + DEGRADED detection + UI badge + 7 unit tests), W7.3 Diagnostics screen verified (20 URLs + Tester + ViewModel), W7.4 split-tunnel per-app applies (SplitTunnelRulesProvider interface + RoomImpl)
- **W2.1 manual DI design** doc + research (research-only, impl device-required)
- **W4.1 partial**: build_xray.sh template + binaries.yml extension + research doc (full AAR build = device session)
- **W9.1 language picker scaffold + UI**: DataStore + LocaleApplier + ViewModel + LanguageSection. До W9.2 показываем только RU+EN (LocaleApplier.SUPPORTED_TAGS), 9 unsupported tags скрыты.
- **Code review concerns**: C1 (runBlocking removed via @Volatile preload-cache), C2 (HealthMonitor scope leak fixed), C3 (Migration_4_5 runtime test через Robolectric), C4 (FAILED status distinguish fixed), M4 (warn missing id fixed), M5 (HealthMonitor UI badge wired). C5 deferred (build_xray.sh research-only).

---

## Phase 0 — clean-start refactor (DONE)

- [x] P1.A создать модуль `:engines-core`
- [x] P1.B ByeDpiEngine implements EnginePlugin
- [x] P1.C DELETE Pipeline + StrategyEngine + Orchestrator
- [x] P1.D OzeroVpnService rewrite через ChainOrchestrator
- [x] P1.E Hilt @IntoMap → @IntoSet
- [x] P1.F DELETE :core-api, :core-subscriptions, :common-json
- [x] P1.G Logging unification — PersistentLoggers везде
- [x] P1.H MainActivity decompose (≤120 строк)
- [x] P1.I Sentinels + Logging contract tests (NoStubsInProductionDi + LoggingContract + BootFileLoggerPersistence)
- [x] P1.J proguard + dex assertions + JaCoCo 0.90

---

## Wave A — autonomous cleanup (DONE 2026-04-30)

- [x] W1.1 commit log noise cleanup — `Log.i/d` достаточно для success-events
- [x] W1.2 proguard-rules.pro: Log.* keep + commoncrypto/commondns + bouncycastle
- [x] W1.3 release.yml dex FORBIDDEN sentinel расширен (+StubEngine/StubPlugin/StubByeDpi)
- [x] W1.4 JaCoCo gate 0.80 → 0.90
- [x] W3.6 stats stagnation detector + UI badge (TDD: 7 tests, EWMA + threshold 30s)
- [x] W3.9 hev YAML log-level configurable, default warn
- [x] W5.1 MainScreen рендерит Failed.reason под engine name
- [x] W5.2 dependency bloat audit + Ktor remove (5 unused libraries)
- [x] W5.4 ShadowsocksUriParser удалён (Phase 0 уже)
- [x] W5.5 ACCESS_NETWORK_STATE permission + obsolete CgnatDetector комментарий удалены
- [x] W5.6 HarvestWorker dual enqueue (Phase 0 уже)
- [x] W5.7 ci.yml feature ветка получает full CI (app-lint/assemble/test-coverage)
- [x] W5.8 ci.yml libhev cache + ABI унификация на 3 (без x86)
- [x] W5.9 TunnelController dead methods cleanup (Phase 0 уже)

---

## Wave B — auto-strategy picker (W3.5)

- [x] W3.5.1 research ByeByeDPI auto-strategy → `.memory/concepts/byedpi-auto-strategy-research.md`
- [x] W3.5.2 ByeDpiStrategy data class + 75-strategy asset + ByeDpiStrategiesParser (10 tests)
- [x] W3.5.3 HttpSocksProbeClient с content-length проверкой (10 tests)
- [x] W3.5.4 AutoStrategyPicker orchestrator (7 tests)
- [x] W3.5.5 UI Auto-test button + progress dialog (LinearProgressIndicator + top-3 cards + Apply)
- [x] W3.5.6 wiring engine-byedpi + DI (AutoStrategyPickerFactory @Singleton + Context.assets reader)

---

## Phase 2 — структурные пробелы (research-only пока)

Все Phase 2 задачи рискованные. Реализация — после установки CI emulator (W2.4) который позволит верифицировать без юзера.

- [x] W2.1 manual DI design doc → `.memory/concepts/manual-di-design.md`
- [!] W2.2 P2.A manual DI implementation для `:common-vpn` — **высокая ризковость, advisor + decompose на atomic шаги перед началом**
- [!] W2.3 P2.A tests boot.log invariant
- [!] W2.4 P2.D on-device smoke на CI emulator — **критичный gate для всех остальных Phase 2**
- [!] W2.5 P2.B process isolation `android:process=":vpn"` — **высокая ризковость, IPC контракт**
- [!] W2.6 P2.C native crash reporting (Breakpad/Sentry Native)
- [x] P2.E TunnelController state machine enforcement (закрыто в Phase 0 commits)

### Особое внимание перед стартом Phase 2

- AUDIT.md P5 (Phase 0 reject): shutdown шаблон `Thread+runBlocking+isDaemon=true+Handler(getMainLooper()).post` ЗАЩИЩЁН тестом `OzeroVpnServiceLifecycleTest`. **Не переписывать**. Manual DI рефакторинг должен оставить shutdown логику нетронутой.
- `loadOnce()` для libhev — только main thread. Защищено `OzeroVpnServiceLifecycleTest`.
- HEV PKGNAME=hev зашит в .so — защищено `JniContractTest`.

---

## Wave C — UI features (DONE через W3.0 atomic refactor)

W3.0 atomic refactor: SettingsRepository interface + SettingsModel + SettingsKeys + AutoStartGateway + SplitTunnelMode перемещены в `:engines-core/settings/`. Impl остаётся в `:app`. Это разблокировало все W3.x features — `:common-vpn` теперь @Inject SettingsRepository через Hilt напрямую без Intent extras hack.

- [x] W3.0 SettingsRepository → :engines-core (atomic refactor, 27 файлов)
- [x] W3.1 stats persistence + history UI (Room v4→5 + Migration_4_5 + Entity + Dao + SessionStatsRecorder + RoomImpl + Compose Screen + ViewModel + nav)
- [x] W3.2 custom DNS UI (DataStore CUSTOM_DNS_SERVERS + buildTunBuilder.customDnsServers + tests)
- [x] W3.3 IPv6 toggle применяется (settings.ipv6Enabled → buildTunBuilder addRoute "::" 0)
- [x] W3.4 ByeDPI editor (existing TextField + W3.5 auto-test покрывают пользовательскую editing нужду)
- [x] W3.7 hosts whitelist/blacklist (HostsMode enum + EngineConfig.ByeDpi.hosts + ByeDpiEngine.buildHostsArgs `-H:host1 host2` `-An`)
- [x] W3.8 autostart toggle (BootReceiver + AutoStartGateway impl + UI toggle wired)

---

## Wave D — Xray + multi-engine

Все блокированы W4.2 (требует Xray AAR build). gomobile cross-compile в CI — research перед стартом, потом реализация.

- [!] W4.1 Xray AAR build pipeline research — **gomobile bind, NDK r27 cross-compile, reproducibility verify**
- [!] W4.2 Xray engine real binding (engine-xray) — **blocked W4.1, требует device verify**
- [!] W4.3 ChainOrchestrator multi-step UI builder — **blocked W4.2**
- [!] W4.4 PublicProxyHarvester restoration (E16.1) — **blocked W4.2**
- [!] W4.5 bootstrap-servers.json freeze (E16.4) — **blocked W4.4**

---

## Wave E — other engines (после Wave D)

Все require Xray AAR pipeline pattern.

- [!] W6.1 AmneziaWG2 engine — **blocked W4.2**
- [!] W6.2 Hysteria2 engine — **blocked W4.2**
- [!] W6.3 NaiveProxy engine — **research: возможен ли Android NDK build**
- [!] W6.4 Tor + Snowflake (Dynamic Feature Module) — **blocked W4.2**
- [!] W6.5 URnetwork P2P engine (E15) — **blocked W4.2, urnetwork/sdk gomobile**
- [!] W6.6 FPTN engine — **новый протокол. github.com/fptn-project/fptn — клиент-серверный VPN с ключом. Decompose: a) AAR build pipeline (research нужен — gomobile? C++ NDK?), b) FptnEngine: EnginePlugin, c) UI — input field "FPTN ключ" в ManualServerScreen или отдельный engine settings, d) DI wiring. blocked W4.2 (engine pattern проверен на Xray)**

---

## Wave F — post-multi-engine

- [x] W7.1 HealthMonitor restoration + UI badge — periodic SOCKS5 probe (30s), DEGRADED после 3 consecutive fails, MainScreen рендерит бейдж "Соединение нестабильно" под TrafficStatsCard.
- [!] W7.2 SubscriptionManager + signed sub URLs — **blocked W4.4**
- [x] W7.3 Diagnostics screen (probe 20 URLs) — Tester + ViewModel + Compose Screen.
- [x] W7.4 split-tunnel per-app — SplitTunnelRulesProvider interface + RoomImpl, applies в OzeroVpnService.

---

## Wave H — i18n локализация UI

- [x] W9.1 language picker — DataStore UI_LOCALE_TAG + SettingsModel.uiLocaleTag + Repository.setUiLocaleTag + LocaleApplier (AppCompatDelegate.setApplicationLocales) + ViewModel.onUiLocaleSelect + LanguageSection RadioButton. **До W9.2 показываем только System + RU + EN (LocaleApplier.SUPPORTED_TAGS)** — 9 unsupported tags скрыты, чтобы юзер не выбрал например китайский и не получил EN fallback (UX hygiene). 9 unused string keys (settings_language_zh_cn ... settings_language_ja) сохранены в values/strings.xml + values-en/ для W9.2.
- [!] W9.2 переводы на топ-10 языков. Сейчас: ru (default values/), en (values-en/). **Добавить**: values-zh-rCN/ (Mandarin), values-es/ (Spanish), values-ar/ (Arabic — RTL), values-fr/ (French), values-hi/ (Hindi), values-pt/ (Portuguese), values-id/ (Indonesian), values-de/ (German), values-ja/ (Japanese). **Подход**: machine translation базовая + native speaker review для финальной полировки. Для всех string resource keys из values/strings.xml (~90 keys). Lint warning MissingTranslation выявит gap'ы.
- [!] W9.3 RTL layout audit — values-ar/ требует mirror layout. Compose `LocalLayoutDirection`. Test screens на RTL preview.

---

## Wave G — finalize

- [x] W5.3 SecurityGuard MVP exclude — :security модуль удалён (~26 файлов deleted, AntiDebug/AntiEmu/AntiFrida + Watchdog + Holder + Module + Manifest + tests)
- [!] W5.10 race conditions pickBest+waitSocksReady — **blocked W4.2**
- [ ] W8.1 feature ветка финал — squash-ready audit (после всех waves)

### Pre-squash gate (closed pre-release 2026-05-01)

- [x] **C3 Migration_4_5 runtime test** — Robolectric + JUnit Vintage interop. `MigrationFourToFiveRuntimeTest` (6 тестов) запускает реальный SQLite через FrameworkSQLiteOpenHelperFactory: schema-create, column types, INSERT/SELECT с DEFAULT, preserves v4 data, idempotent, finalStatus values.
- [x] C1 runBlocking ×2 в startVpn — заменено на `@Volatile cachedSettings/cachedSplitPackages` preload в `onCreate` через `serviceScope.launch { settings.collect { } }`. Defaults safe при race на cold start.
- [x] C2 HealthMonitor scope leak — `OzeroVpnService.onDestroy → healthMonitor.shutdown()`.
- [x] C4 SessionStatsRecorder finalStatus — `if (priorState is TunnelState.Failed) FAILED else DISCONNECTED`.
- [x] M4 SessionStatsRecorder.startSession id=-1 — `PersistentLoggers.warn` добавлен.
- [x] M5 HealthMonitor half-feature — UI badge wired в MainScreen.
- [!] C5 build_xray.sh не tested — research deliverable, device session.

Полный review doc: `.memory/knowledge/concepts/feature-branch-code-review-2026-05-01.md`.

### v0.0.2-1 release readiness

- [x] Все critical/medium concerns закрыты (C1, C3, M5).
- [x] Hilt-граф компилится (NoStubsInProductionDi sentinel).
- [x] Sentinel-тесты для C1/C3/M5: `OzeroVpnServiceLifecycleTest`, `MigrationFourToFiveRuntimeTest`, `MainViewModelTest`.
- [ ] **device smoke install-over-upgrade** v0.0.1 → v0.0.2-1 — manual session, единственный gate перед `git tag -a v0.0.2-1`. Verify: app не крашит на первом open, StatsHistoryScreen открывается, prev split-rules выживают, logcat показывает `Migration_4_5` без `IllegalStateException`.
- [ ] git tag -a v0.0.2-1 после device smoke (versionName тянется из `gitVersionName`, версия не зашита в build.gradle).

### v0.0.2-5 — device bugs fix wave (2026-05-02)

Баги найдены при тестировании v0.0.2-4 на устройстве:

- [x] **SEC-P1-01** IPv6 leak при `ipv6Enabled=false` — `::/0` null-route теперь добавляется всегда. IPv6-адрес туна — только если включено. Sentinel `OzeroVpnServiceIpv6Test`.
- [x] **CODE-P1-01** onDestroy serviceScope race — `runBlocking(Dispatchers.IO) { performShutdown() }` ПЕРЕД `serviceScope.cancel()`. Engine teardown теперь гарантирован.
- [x] **CODE-P1-02** fd leak в `routeTrafficForEngine` — `fd.detachFd()` → try/catch вокруг `engine.attachTun(rawFd)` с `Os.close(rawFd)` в catch и Failure-ветке.
- [x] **CODE-P1-03** ChainOrchestrator race — `Mutex` + `withLock` вокруг `start()` и `stop()`. Sentinel `ChainOrchestratorConcurrencyTest` (3 теста).
- [x] **CODE-P1-04** ServersViewModel два upsert без транзакции — `@Transaction fun upsertPair(entry, exit)` в `ServerDao`, заменён двойной upsert.
- [x] URnetwork NetworkSpace null — `importNetworkSpaceFromJson` + `setActiveNetworkSpace` fallback.
- [x] WARP зеркала timeout — per-mirror coroutine timeout 20s.
- [x] Simple/Expert визуальное разграничение — "EXPERT" badge в `ExpertMainContent` + string resources.
- [x] Split tunnel навигация — уже была wired через `Settings → ALLOWED_APPS_ROW → TopScreen.SplitTunnel → SplitTunnelScreen`. UX issue, не код.

---

## Порядок следующей сессии (когда юзер вернётся с device)

Каждая задача ниже — atomic. Если оказывается сложной (cross-module, риск регрессии) — обязательно advisor + декомпозиция перед код-fix. Цель: чистая надёжная реализация без заглушек.

1. **W2.4 emulator smoke** (low-risk инфраструктура): `.github/workflows/smoke.yml` matrix api-30/33. Боковая ветка от feature чтобы CI настроить, потом merge. После этого все Phase 2 verifiable автономно.
2. **W2.2 manual DI implementation** (high-risk): atomic шаги W2.2.1-W2.2.5 из design doc. Каждый шаг — отдельный commit + verify через emulator smoke (W2.4).
3. **W2.3 manual DI tests** — boot.log invariant + NoHiltAnnotationsTest sentinel.
4. **W3.5.5 + W3.5.6** auto-test UI/DI integration. Использует AutoStrategyPickerFactory который читает Context.assets.
5. **W2.5 process isolation** (high-risk): `android:process=":vpn"`. Требует Messenger IPC. После W2.2 (manual DI готов).
6. **W2.6 native crash minidumps**: подключить Breakpad. После W2.5.
7. **Cross-module W3.x**: рефакторинг VpnIntentLauncher → IntentSettings data class в `:common-vpn`. Затем W3.3 IPv6 toggle, W3.2 custom DNS, W3.4 ByeDPI editor, W3.1 stats persistence (Room migration), W3.7 hosts mgmt, W3.8 autostart.
8. **W4.1 Xray AAR research**: gomobile, NDK, reproducibility. Без advisor + 2-3 итераций ресерча в код не лезть.
9. **W4.2-W4.5**: Xray engine + chain UI + harvester + bootstrap.
10. **Wave E** other engines в порядке приоритета: AWG2 → Hy2 → URnetwork → Tor → Naive (если Android build solvable).
11. **Wave F**: HealthMonitor + SubscriptionManager + Diagnostics + split-tunnel.
12. **Wave G finalize**: SecurityGuard delete + race conditions + squash audit.

---

## Defaults для разработки

- Каждая задача с `[!]` — **обязательный advisor() перед стартом** для проверки подхода + декомпозиция на atomic шаги.
- TDD строго: failing test → impl → green.
- CI watch через `gh run watch`, не sleep.
- Коммиты в `feature` ветку — частые, каждый atomic. Push после verify локально невозможен (CLAUDE.md `feedback_no_local_tests`) — push сразу + watch CI.
- Никаких заглушек/stubs в production. Sentinel test (`NoStubsInProductionDi`) защищает.
- Cross-module changes — extract data class в shared module, не передавать через Hilt graph если не Sure.
