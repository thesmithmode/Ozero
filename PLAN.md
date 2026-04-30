# PLAN.md — Ozero roadmap

## Статус (2026-04-30)

- **dev**: 9193580 (P1.A-H done, P1.G logging migration done, detekt fix)
- **v0.0.1**: ребилд после CI green на 9193580
- **Architecture review (2026-04-30)**: 5 структурных пробелов идентифицированы

## Phase 1 — clean-start refactor

| ID | Status | Описание |
|----|--------|----------|
| P1.A | ✅ | Создать модуль `:engines-core` |
| P1.B | ✅ | ByeDpiEngine implements EnginePlugin |
| P1.C | ✅ | DELETE Pipeline + StrategyEngine + Orchestrator |
| P1.D | ✅ | OzeroVpnService rewrite через ChainOrchestrator |
| P1.E | ✅ | Hilt @IntoMap → @IntoSet |
| P1.F | ✅ | DELETE :core-api, :core-subscriptions, :common-json |
| P1.G | ✅ | Logging unification — PersistentLoggers везде |
| P1.H | ✅ | MainActivity decompose (≤120 строк) |
| P1.I | ⏳ | Tests — Sentinels + Logging contract |
| P1.J | ⏳ | Cleanup — proguard, JaCoCo 0.90, sentinel checks |

### P1.I (pending)
- I.1 NoStubsInProductionDiTest — sentinel против stub-движков в проде
- I.2 LoggingContractTest — все warn/error попадают в boot.log
- I.3 BootFileLoggerPersistenceTest — файл переживает onDestroy

### P1.J (pending)
- J.1 proguard-rules.pro cleanup
- J.2 release.yml dex assertions cleanup
- J.3 JaCoCo gate 0.80 → 0.90

## v0.0.1 BUGFIX (требует логи новой сборки)

| ID | Status | Описание |
|----|--------|----------|
| #53 | ✅ | Удалить старый релиз + тег |
| #54 | ⏳ | DPI не пробрасывает трафик — diagnosis pending logs |
| #55 | ⏳ | Disconnect VPN бесконечный спиннер — diagnosis pending logs |
| #56 | ✅ | App icon — silver Ω logo |

## Phase 2 — структурные пробелы (architecture review 2026-04-30)

Под multi-engine VPN с native deps нынешний стек *undersized*. 5 задач из ревью.

### P2.A — Manual DI для `:common-vpn` (КРИТИЧНО)
- **Проблема**: Hilt provider бросает в `super.onCreate()` → service умер до боевой логики → boot.log пуст → диагностика = 0. Knowledge base зафиксировал 2× (`hilt-di-native-library-failure`, `compose-launchedeffect-crash-invisibility`).
- **Цель**: VpnService и Engine plugins создаются через `ServiceLocator` или manual factory. Hilt остаётся для UI слоя.
- **Acceptance**:
  - OzeroVpnService.onCreate() не зависит от `@AndroidEntryPoint`
  - Любая ошибка инициализации движка пишется в boot.log ДО throw
  - Test: симулировать failed engine init → boot.log содержит причину
- **Scope**: HiltVpnModule → ServiceLocator pattern, EnginePlugin Set инициализируется явно
- **Riskiness**: высокая, тронет все DI bindings в `:common-vpn`/`:engine-byedpi`

### P2.B — Process isolation `android:process=":vpn"`
- **Проблема**: ByeDPI SIGSEGV в native = весь процесс умер = MainActivity тоже. UI и engine в одном процессе.
- **Цель**: VpnService + engine plugins в отдельном процессе. Краш engine ≠ краш UI.
- **Acceptance**:
  - AndroidManifest: `<service ... android:process=":vpn">`
  - MultiDex/Hilt + multi-process нюансы решены (Hilt не рекомендуется → подкрепляет P2.A)
  - Test: kill -SIGSEGV на VPN process → MainActivity жива
- **Scope**: AndroidManifest, BootFileLogger init на process-level, IPC контракт между UI и service (Messenger/AIDL)
- **Riskiness**: высокая, IPC сериализация state, lifecycle в 2× процесса

### P2.C — Native crash reporting (.so SIGSEGV visibility)
- **Проблема**: Краш в .so → ART tombstone в logcat → boot.log пуст. У anti-DPI половина риска в native libs (libbyedpi, libhev-socks5-tunnel, future libxray, libhy2). Без visibility = чёрный ящик.
- **Цель**: breakpad-style minidump на SIGSEGV/SIGABRT, dump → filesDir/crash/, отображение в Settings → Crash log.
- **Acceptance**:
  - Sentry Native / Breakpad подключён или собственный sigaction handler с unwind
  - Forced crash в test → minidump сохранён
  - UI показывает stack trace в Settings → Crash log
- **Scope**: новый модуль `:common-crash`, JNI bridge, signal handler init в Application/process start
- **Riskiness**: средняя, signal handlers тонкая работа

### P2.D — On-device smoke на CI emulator
- **Проблема**: Coverage 0.80 зелёный, runtime сломан (#54/#55). Unit-тесты не ловят VPN regression. Юзер ловит — это плохо.
- **Цель**: Github-hosted emulator в CI: install APK → start VPN → ping → traffic test → stop. Или Firebase Test Lab если бесплатный quota хватает.
- **Acceptance**:
  - `.github/workflows/smoke.yml` matrix [api-30, api-33]
  - Шаги: emulator boot → install debug APK → adb shell am startservice → curl через VPN tun → assert HTTP 200
  - Длительность ≤15 мин
- **Scope**: CI workflow, debug build с smoke-friendly endpoint
- **Riskiness**: низкая, чисто infrastructure

### P2.E — TunnelController state machine enforcement
- **Проблема**: TunnelState sealed class есть, но переходы не валидируются. `onConnecting → onConnecting → onEngineDied` принимается без ошибки. Race conditions в UI spinner (баг #55 возможно отсюда).
- **Цель**: allowed transitions матрица + assert. `onProbing → Connecting → Connected` единственный happy path. Любой невалидный transition = throw + log.
- **Acceptance**:
  - `TunnelController.transition(from, to)` гейт
  - Test: каждый невалидный переход → IllegalStateException
  - UI spinner расходится корректно при `Failed`
- **Scope**: `:common-vpn/TunnelController.kt`, тесты
- **Riskiness**: низкая, локальная задача

## Приоритет Phase 2

1. **P2.A → P2.E** (state machine — простая, ловит #55) — параллельно после v0.0.1 retest
2. **P2.A** (manual DI) — после v0.0.1 закрыт, КРИТИЧНО для будущей диагностики
3. **P2.D** (CI smoke) — после P2.A, blocking для релизов
4. **P2.C** (native crash) — после P2.A/D, блокирует следующие движки (Xray/Hy2)
5. **P2.B** (process isolation) — последним, самая ризковая, требует P2.A

## Phase 3 — multi-engine (после Phase 2)

- Xray VLESS+Reality engine plugin
- Hysteria2 engine plugin
- AmneziaWG2 engine plugin
- ChainOrchestrator real chains (не single-engine)
- Auto chain switcher (geo-binding + DPI conditions)

## Текущая работа (живая)

- ⏳ CI на 9193580 → green → тег v0.0.1 → release
- ⏳ Юзер тестит APK → даёт логи → диагностика #54/#55
- 🟢 Параллельно: можно начать P1.I (тесты-сентинели) — изолированная задача
