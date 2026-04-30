# Code review feature → dev (2026-05-01, autonomous session)

**Scope**: 38 commits ahead of dev, 125 файлов, +3703/-1100 lines.

## Findings

### Critical

— нет findings.

### Concerns

**C1. runBlocking ×2 на main thread в OzeroVpnService.startVpn**
- `settingsRepository.settings.first()` (W3.3 line 140)
- `splitTunnelRulesProvider.activePackages()` (W7.4 line 145)
- ANR risk на слабых устройствах (Nubia/RedMagic, low-end). DataStore typically <10ms warm cache, Room dao first() — может занять до 50ms на cold start. Total ~60ms блок main thread.
- **Fix path**: preload в `onCreate` через `serviceScope.launch` → `@Volatile cachedSettings` field. startVpn читает cached. Default fallback при null cache. Defer — приемлемый компромисс для v0.0.x. Документировать в CLAUDE.md правиле.

**C2. HealthMonitor CoroutineScope leak**
- `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` создаётся при `@Inject` в Singleton.
- `shutdown()` метод есть, но никто не вызывает — instance живёт пока process жив.
- Acceptable для VPN service (Singleton lifecycle = process lifetime). Real leak только при `Hilt component recreation`, не происходит в production.
- **Fix path**: `OzeroVpnService.onDestroy → healthMonitor.shutdown()`. Не critical.

**C3. Migration_4_5 без runtime test**
- MigrationFourToFiveTest — source-pattern sentinel, не runtime SQL execution.
- Если CREATE TABLE синтаксис неверен — упадёт на первом install over upgrade.
- **Fix path**: Robolectric + Room MigrationTestHelper в `core-storage/src/test/`. Защищает install over upgrade юзеров с installed v0.0.1.
- **Status**: pending PLAN [!] reminder.

**C4. SessionStatsRecorder finalStatus всегда DISCONNECTED**
- `recordSessionEnd(SessionStatsRecorder.Status.DISCONNECTED)` вызывается из stopVpn (любой path).
- FAILED status declared в enum но не используется. Should differentiate engine-died vs user-disconnect.
- **Fix path**: `recordSessionEnd(if (current is TunnelState.Failed) FAILED else DISCONNECTED)`. Minor.

**C5. build_xray.sh не tested**
- Pure template из build_amneziawg.sh — actual build не verified в CI.
- Risk: gomobile bind может fail на cgo + NDK r27 + Xray-core specifics.
- **Fix path**: device session с manual run. PLAN.md помечен как research-only deliverable.

### Минор

**M1. customDnsServers comma-joined storage**
- DataStore хранит как `"8.8.8.8,1.1.1.1"` — split на `,`. IPv6 не содержит `,` (содержит `:`), безопасно. Domain тоже не `,`. OK.

**M2. SocksProbeClient HttpURLConnection timeout**
- connectTimeout = readTimeout. Slow site → counts as fail. Acceptable false-negative.

**M3. ByeDpiEngine.buildHostsArgs whitespace-joined hosts**
- `-H:host1 host2 host3` single argv slot. Pattern скопирован 1:1 из ByeByeDPI v1.7.4 — проверен upstream.

**M4. SessionStatsRecorder.startSession id=-1 silent fail**
- `endSession` skip if id<0. Acceptable graceful degradation. Но log warn missing — tихий silence в production.
- **Fix path**: PersistentLoggers.warn если startSession fail.

**M5. HealthMonitor auto-failover не реализован**
- Только observable status DEGRADED. UI не подключен (ViewModel не observ'ит).
- Half-feature до multi-engine W6.x когда failover к next engine = real value.
- **Status**: by design (post-multi-engine), документ.

### OK

- W3.0 atomic refactor SettingsRepository → :engines-core. `:common-vpn` dependency правильная (engines-core upstream).
- W5.3 :security delete — все references очищены, tests passing.
- 8 Fakes (SettingsRepository) обновлены сistematically через sed.
- LoggingContractTest whitelist правильно очищен от security/* paths.
- W3.5 strategy logic — pure, тесты покрывают edge cases.
- TDD дисциплина: Migration sentinel + StatsStagnation + SocksProbe + AutoStrategyPicker — все имеют tests до impl.
- CI feature ветка получает full pipeline (W5.7) — no gap до dev.

## Risks для squash → dev

1. **Hilt graph compile** — verified CI, ОК.
2. **JaCoCo 0.90** — verified CI passing, ОК.
3. **Lint MissingTranslation** — RU+EN coverage всех keys включая новые (main_stagnation_warning EN добавлен W3.6 fix).
4. **Bytecode dex assertions** — release.yml FORBIDDEN включает StubEngine/StubPlugin/StubByeDpi. CI release-build only on main — не тестится в feature.
5. **Migration v4→5** — без runtime test. Risk medium для install over upgrade.

## Recommendation

✅ Squash к dev допустимо при:
1. CI feature green (текущий 25189676865 verify)
2. Phase 2 deferred work explicit в PLAN.md
3. C3 Migration runtime test — **рекомендуется добавить** перед squash (Robolectric MigrationTestHelper) — защита prod юзеров

⚠️ **Не делать**: push в `dev` без явной команды юзера (CLAUDE.md rule).
