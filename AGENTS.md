# AGENTS.md — Ozero

Само-обучение: новое правило → сюда. Без дублей, ≤100 строк.

## Продукт и архитектурный контракт

Видение → `README.md`. Архитектура → `.codex/Контекст/Architect.md`.

**Нерушимые инварианты:**
- Каждый движок = отдельный gradle-модуль `engine-*`. Падение одного не валит соседей.
- Контракт: `Engine.start(config, upstream)`. `app/` знает только контракт, не детали.
- Single-engine = chain длины 1. Новый движок никогда не идёт файлами в `app/`.
- Единый UI, максимальная изоляция движков.

## Комментарии в коде — запрет

- НЕ писать комментарии. Code self-documenting через имена.
- Исключение: критическая нетривиальность (subtle invariant, hidden constraint, workaround конкретного бага). One-liner.
- НЕ оставлять ссылки на тикеты/инциденты/фазы (RT.X, E10, F4, JIRA-XXX).
- При правке файла — удалять старые комментарии если не критичны.
- Перед commit: `git diff --staged` → убрать подозрительное.

## Чувствительные данные

Репо публичный. Каждая строка индексируется навсегда.

- Никаких user-specific доменов, имён хостов, тестовых юзеров, путей `/root/...`, токенов/ключей.
- Никаких упоминаний инцидентов/людей в коде/доках. Все логи — sanitize (CrashLogStore pattern).
- Перед push: "что узнает посторонний из этого diff?"

## Режим работы

- **Никогда не ждать субагентов или CI без дела.** Простой = нарушение.

## Подход к проблемам

- Минимизировать вопросы к юзеру. Сам исследовать → решать → валидировать.
- Любая проблема: **широкий взгляд первым** → vertical (UI→handler→service→native) + horizontal → **корень**.
- Фиксить корень, не симптом. 3 фикса подряд → пересмотр гипотезы.
- Структурный fix > точечный patch. Корень = workflow → добавить gate.

## Workflow

- main не трогать без явной команды.
- Merge dev→main: БЕЗ сквоша (`git merge --ff` или `--no-ff`), история 1:1 с dev.
- Релизы: тег `v*.*.*` на dev → release.yml соберёт APK.

## Билд

- Один APK (`assembleRelease`), без dynamic features.
- abiFilters: только arm64-v8a. Расширение ABI без перестройки native = рантайм-краш.
- R8 minify+shrink включены, Log.* НЕ стрипаются (proguard-rules.pro).

## Native libs

- `System.loadLibrary` — **только lazy** через `loadOnce()`. Никогда в `Application.onCreate`/`attachBaseContext`/init-блоках/companion init. Исключение: `OzeroApp.onCreate` с `isEngineWarpProcess()` guard. `libam-go` только в `:engine_warp`, `libgojni` только в main process. Coexistence = SIGABRT. [sentinel: `OzeroAppProcessIsolationTest`]
- `loadOnce()` для `libhev-socks5-tunnel` — **только из main thread**, до `serviceScope.launch`. [sentinel: `OzeroVpnServiceLifecycleTest`]
- `libhev-socks5-tunnel.so` — собирать с `APP_CFLAGS=-DPKGNAME=hev` (release.yml + ci.yml). [sentinel: `JniContractTest`]
- `hev.TProxyService` — объявлять **все три** `external fun`: `TProxyStartService(String, Int)`, `TProxyStopService()`, `TProxyGetStats(): LongArray`. [sentinel: `JniContractTest`]
- `loadOnce()` — `catch (e: Throwable)` после специфичных catch-блоков. [sentinel: `TProxyServiceLogTest`]
- ByeDPI CMD mode (`byedpiUseUiMode=false`): args идут **verbatim**, только `trim()`. Никаких авто-suffix (`-Ku -a1 -An`). [sentinel: `ByeDpiBuildManualConfigTest`]
- ByeDPI: `stop()` — `proxy.forceClose()` ДО `job.join()` (unblock READ_WAIT перед ожиданием). [sentinel: `ByeDpiEngineTest`]

## Logging

- `PersistentLoggers.error/warn` — только критичные события (errors, JNI checkpoints для hang-диагностики). На success — запрещено, `Log.i/d` достаточно. Дубль → шум, удалять.

## Коммиты — типовые ловушки

- Удалил вызов → удали импорт. ktlint режет unused imports как errors. Перед commit: `git diff --staged`.

## Тесты — типовые ловушки

- Интерфейс изменился → `grep -r "FakeXxx\|StubXxx"` и обновить все fakes. Compile fail в CI = не обновил.
- VM с StateFlow в `@BeforeEach`: создавать VM **внутри теста** ПОСЛЕ `store.setRaw(...)`. Иначе race.
- Material Icons: только `material-icons-core`. `Icons.Filled.Android/Apps/PhoneAndroid` — нет в core.

## Per-engine UI

- Каждый engine обязан иметь settings screen в `ui/settings/engines/`. Текущие: byedpi, fptn, urnetwork, warp, masterdns.

## Subprocess-proxy

- `engine-masterdns` — subprocess-pattern, но полноценный `EnginePlugin` (`@IntoSet` через `MasterDnsModule`). `libmdnsvpn.so` через `ProcessBuilder`, **не** `System.loadLibrary`.
- Routing: WARP (`socksPort==0`) → subprocess наследует UID (TUN авто). SOCKS-engine → `--socks5-proxy-url socks5://127.0.0.1:<port>`.

## Расследование — порядок (закон)

ПЕРЕД субагентом/advisor/гипотезой:
1. `wiki-find <тема>` — `.memory/knowledge/` уже компилированы из daily логов.
2. `ls .codex/Контекст/` + читать `*_ANALYSIS.md` соответствующего движка.
3. Только если 1-2 не дали ответ — субагент/новые гипотезы.

## Reference impls движков (`.codex/Контекст/`)

- `android/` → **URnetwork** (`URNETWORK_INIT_ANALYSIS.md`)
- `PORTAL_WG_v1.4.3/` + `CYBERPORTAL_X-v1.0.2/` → **WARP** (`PORTAL_WG_ANALYSIS.md`)
- `ByeByeDPI-v.1.7.5/` → **ByeDPI**. byedpi submodule pin: `ba532298`.
- `karing/`, `Invizible_Pro*/`, `КИБЕРЩИТ*/`, `ResultV/`, `PortalConnect*/`, `amnezia-{client,vpn}/` — secondary references.
- `Architect.md`, `AUDIT.md`, `PRD.md`, `SPEC.md`, `ПЛАН.md` — проектные документы, не reference.

<!-- === rules:start === -->
## Внешние правила

- [`context7.md`](.codex/rules/context7.md) — **Закон.** Context7 MCP для всех библиотек/SDK.
- [`tests.md`](.codex/rules/tests.md) — **Закон.** AAA, coverage ≥95%, exhaustive edge-cases.
- [`folders.md`](.codex/rules/folders.md) — **Дух.** Новый движок = `engine-*` модуль, не файлы в `app/`.
- [`git.md`](.codex/rules/git.md) — **Reference.** У Ozero: `dev` default, squash-merge без PR, main только по команде.
- [`translate.md`](.codex/rules/translate.md) — **Baseline.** Локали ru/en/es/pt → `values-{en,es,pt}/strings.xml`.

<!-- === rules:end === -->

## graphify

- `graphify-out/GRAPH_REPORT.md` — god nodes, communities. `graphify query/path/explain`. После правок кода — `graphify update .`.
- `.memory/knowledge/index.md` — project knowledge (почему так сделано, прошлые баги, неочевидные решения).

