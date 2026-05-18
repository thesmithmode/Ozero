# CLAUDE.md — Ozero

Правило само-обучения:
После каждой допущенной ошибки и исправления, или нарушения формата, стиля и так далее, в общей любое вновь открывшееся правило должно быть добавлено в этот файл CLAUDE.md проекта,
чтобы впредь ты не допускал подобные ошибки. Следи, чтобы файл оставался минималистичным, без дублирований и повторений, обобщай одинаковые мысли, сокращай текст при необходимости.
Короткий но содержательный CLAUDE.md залог успеха.

## Продукт и архитектурный контракт

Видение продукта → `README.md`. Архитектура → `.claude/Контекст/Architect.md`.

**Нерушимые инварианты:**
- Каждый движок = отдельный gradle-модуль `engine-*`. Падение одного не валит соседей. Удалить движок = убрать один модуль.
- Контракт: `Engine.start(config, upstream)` — единый интерфейс. `app/` знает только контракт, не детали реализации.
- Single-engine = chain длины 1. Новый движок никогда не идёт файлами в `app/`.
- Единый UI, максимальная изоляция движков. Низкая связанность — приоритет над любым удобством реализации.

## Комментарии в коде — запрет

- НЕ писать комментарии. Code self-documenting через имена.
- Исключение: критическая нетривиальность (subtle invariant, hidden constraint, workaround конкретного бага). One-liner без внутреннего контекста.
- НЕ оставлять ссылки на тикеты/инциденты/фазы (RT.X, E10, F4, JIRA-XXX).
- При правке файла — удалять старые комментарии если не критичны.
- Перед commit: `git diff --staged` → убрать подозрительное.

## Чувствительные данные

Репо публичный. Каждая строка индексируется навсегда.

- Никаких user-specific доменов, имён хостов, тестовых юзеров, путей `/root/...`, токенов/ключей.
- Никаких упоминаний инцидентов/людей в коде/доках.
- Все логи приложения — sanitize (см. CrashLogStore pattern).
- Перед push: "что узнает посторонний из этого diff?"

## Режим работы

- **Никогда не ждать субагентов или CI без дела.** Пока субагент работает или CI крутится — делать следующую задачу из списка, ревью кода, писать тесты, читать контекст. Нет задач — провести код ревью последних изменений. Простой = нарушение.

## Подход к проблемам

- Минимизировать технические вопросы к юзеру. Сам исследовать → сам решать → сам валидировать. Спрашивать только при genuine ambiguity или destructive action.
- Любая проблема (баг, красный CI, falling test): **широкий взгляд первым** → vertical (UI→handler→service→native) + horizontal (соседние модули, producers/consumers, тесты, конфиги) → **корень**, не первый видимый симптом.
- Фиксить корень, не симптом. 3 фикса подряд не помогли → пересмотр гипотезы.
- Структурный fix > точечный patch. Если корень = workflow/процесс, добавить gate (pre-push hook, sentinel test, CI check), а не повторять fix руками.

## Workflow

- main не трогать без явной команды.
- Релизы: тег `v*.*.*` на dev → release.yml соберёт APK.

## Билд

- Один универсальный APK (`assembleRelease`), без dynamic features.
- abiFilters в APK: только arm64-v8a (`app/build.gradle.kts`). libhev/libam-go/libbyedpi/libmtg существуют только под arm64-v8a — расширение ABI без перестройки native = рантайм-краш. Sentinel'ы в release.yml тоже arm64-v8a-only.
- R8 minify+shrink включены, но Log.* НЕ стрипаются (см. proguard-rules.pro).

## Native libs

- `System.loadLibrary` — **только lazy** через идемпотентный `loadOnce()` (см. `hev.TProxyService`, `ByeDpiProxy`). Никогда в `Application.onCreate`, `attachBaseContext`, init-блоках, companion object init. Нарушение → SIGSEGV в JNI_OnLoad на Application bootstrap (v1.0.1 краш). Исключение: per-process eager-load в `OzeroApp.onCreate` через `isEngineWarpProcess()` guard. `libam-go` грузится **только** в `:engine_warp` (Application.onCreate ветка с return после load). `libgojni` грузится **только** в основном процессе. Coexistence двух Go-рантаймов в одном процессе запрещён — конфликт GC signal handlers → SIGABRT (v0.0.12 на Nubia/RedMagic, 6 tombstones). Process-isolation реализован через `android:process=":engine_warp"` на `WarpEngineService` + AIDL `IWarpEngineProcess`. Защищено `OzeroAppProcessIsolationTest`.
- `loadOnce()` для `libhev-socks5-tunnel` вызывать **только из main thread** (через `OzeroVpnService.startVpn` до `serviceScope.launch`). Загрузка с coroutine worker → SIGSEGV в vendor `libglnubia.so` `nubia::Messager::timerLoop` на Nubia/RedMagic (v1.0.3 краш). Правило защищено `OzeroVpnServiceLifecycleTest`.
- `libhev-socks5-tunnel.so` собирать с `APP_CFLAGS=-DPKGNAME=hev` (release.yml + ci.yml). Upstream `src/hev-jni.c` defaults `PKGNAME=hev/htproxy`, без override → `FindClass("hev/htproxy/TProxyService")` = NULL → `RegisterNatives(NULL,...)` → ART `JniAbort` при первом старте VPN (v1.0.2 краш). Защищено `JniContractTest` + step `Assert PKGNAME=hev зашит`.
- `hev.TProxyService` обязан объявлять **все три** `external fun`: `TProxyStartService(String, Int)`, `TProxyStopService()`, `TProxyGetStats(): LongArray`. Upstream `hev-jni.c` регистрирует ровно эти 3 метода через `RegisterNatives`. Отсутствие любого → `NoSuchMethodError` из `Runtime.nativeLoad` → `libhev` не грузится → tunnel не поднимается (v1.0.2 follow-up). Защищено `JniContractTest`.
- `loadOnce()` обязан иметь `catch (e: Throwable)` после специфичных catch-блоков. `Runtime.nativeLoad` может бросить `NoSuchMethodError`, `LinkageError`, `ClassNotFoundException` — все вне `UnsatisfiedLinkError`/`SecurityException`. Без generic catch `loadError` остаётся `null`, диагностика теряется. Защищено `TProxyServiceLogTest`.
- ByeDPI: `ByeDpiEngine.stop()` и `start()` failure path **всегда** вызывают `proxy.forceClose()` после `job.join()`, не conditional на `isActive`. Причина: `jniStopProxy` делает только `shutdown(server_fd, SHUT_RDWR)` без `close()`/`reset`. Upstream byedpi `main()` хранит `server_fd` глобально и при next start видит stale fd → возвращает -1. Регрессия проявляется как серия 10+ `jniStartProxy завершился с кодом -1` подряд (видно в 2026-05-15 01:33–01:40 продовом логе), recovery только после случайного `proxyJob не завершился за 1500ms — jniForceClose`. Sentinel — `start failure clears upstream server_fd so next start can bind same port` + `stop always forceClose after join` в `ByeDpiEngineTest`. Также проявляется как 0% результат в `EvolutionEngine` (600 start/stop циклов = накопление stale fd → все `EvalResult.startFailed=true` → fitness 0).

## Logging

- BootFileLogger (filesDir/debug/boot.log) — persistent, append-only, init из attachBaseContext.
- LogcatReader → in-memory ring buffer для UI Logs tab.
- Boot log tab отдельный (Settings → Boot log), очистка только вручную.
- `PersistentLoggers.error/warn` — для критичных событий, обязанных попасть в boot.log на диск (errors, warnings, JNI pre-blocking checkpoints для hang-диагностики). На success-events запрещено: `Log.i/d` достаточно — UnifiedLogger пишет и в logcat, и в файл через один канал. Дубль `Log.i + PersistentLoggers.info` на success → шум, удалять.

## Коммиты — типовые ловушки

- Удалил вызов → удали импорт. ktlint/detekt режут unused imports как errors.
- Перед commit: `git diff --staged` → проверить нет ли осиротевших import строк.

## Тесты — типовые ловушки

- **Интерфейс изменился** (добавлен/удалён метод) → сразу `grep -r "FakeXxx\|StubXxx\|FakeRepo"` по тестам и обновить все fake-реализации. Compile fail в CI = не обновил.
- **ViewModel/объект с начальным StateFlow-состоянием в `@BeforeEach`**: если тест требует конкретного начального состояния store/repo — создавать VM **внутри теста** ПОСЛЕ `store.setRaw(...)`, не переиспользовать экземпляр из setUp. Иначе coroutine видит null раньше, чем тест успевает установить значение → race → ложный auto-trigger.
- **Material Icons**: использовать только символы из `material-icons-core`. `Icons.Filled.Android`, `Icons.Filled.Apps`, `Icons.Filled.PhoneAndroid` — не существуют в core → compile fail. Placeholder без icon из расширенного набора → `Text("?")`.

## Per-engine UI

- Каждый engine (текущие модули: byedpi, telegram, urnetwork, warp) обязан иметь settings screen в `app/src/main/java/.../ui/settings/engines/` для пользовательского override config (subscription URL, server picker, args, bridges, и т.д.). При добавлении нового `engine-*` модуля — добавить сюда.

## MTProxy / Subprocess-proxy паттерн

- `engine-telegram` — не VPN routing engine, а side-car proxy subprocess. Не реализует `Engine` интерфейс, не регистрируется через `@IntoSet`.
- Subprocess запускается через `ProcessBuilder` из `nativeLibraryDir`. Бинарь (`libmtg.so`) — prebuilt Go binary, помещается прямо в `jniLibs/<abi>/`. **Не** грузить через `System.loadLibrary` — бинарь запускается как отдельный процесс, не как .so.
- Routing через VPN: при WARP (`socksPort == 0`) subprocess наследует UID → трафик через TUN автоматически (`excludeSelf=false`). При SOCKS-engine — передать `--socks5-proxy-url socks5://127.0.0.1:<port>` (loopback минует TUN).
- `TelegramProxyCoordinator` — единственная точка связи VPN state и proxy: наблюдает `TunnelController.state` + `configStore.config()` через `combine`, выбирает upstream, вызывает `start/stop`. Инициализируется в `OzeroApp.onCreate` через Hilt inject + `runCatching`.

## Контекст-файлы (читать в начале каждой сессии)

- `.claude/Контекст/Architect.md` — карта связей между модулями + неочевидные решения (loadOnce/main thread, PKGNAME, два слоя Engine+Delegate, и т.д.). Обновлять при изменении модулей или появлении новых неочевидных инвариантов. Не превращать в ридми — только связи и обоснования.
- `.claude/Контекст/AUDIT.md` — append-only журнал findings (P1…P##). Не суммаризировать в новый план без явной команды юзера.

<!-- === rules:start === -->
## Внешние правила (thesmithmode/rules — отфильтровано под Ozero)

Подтянуто скиллом `/rules`. Из 16 правил в репо — оставлено 5 релевантных Android/Kotlin/Compose проекту. Остальные (Next.js, Python, Postgres jobs, OAuth, billing, web-i18n, web-analytics, payments) **намеренно не скачаны** — для Ozero они дезориентируют. Re-run `/rules` обновит снапшот, но фильтр придётся применить заново вручную.

- [`context7.md`](C:/Soft/Projects/Ozero/.claude/rules/context7.md) — **Закон.** Всегда использовать `context7` MCP при работе с любой библиотекой/SDK: установка, импорты, конфиг, обновления, дебаг. Применять для AndroidX, Compose, Hilt, Kotlin coroutines, Gradle plugins, и т.д.
- [`tests.md`](C:/Soft/Projects/Ozero/.claude/rules/tests.md) — **Закон с поправкой.** AAA-паттерн, exhaustive edge-cases (null/empty/boundary/invalid types/race/auth/pagination). Coverage порог в Ozero **≥95%** (`feedback_tdd_coverage_logs`), target 100%. Чек-лист "что тестировать" применять полностью.
- [`folders.md`](C:/Soft/Projects/Ozero/.claude/rules/folders.md) — **Дух применим, буква нет.** Принцип "разделение по сервисам" в Ozero реализован через 9 gradle-модулей (`app`, `engines-core`, `core-storage`, `common-{vpn,dns,crypto}`, `engine-{byedpi,urnetwork,warp}`). Новый движок = новый `engine-*` модуль, не файлы в `app/`. Текст правила про `frontend/backend/worker/` — игнорить, у нас Android single-APK.
- [`git.md`](C:/Soft/Projects/Ozero/.claude/rules/git.md) — **Только reference, НЕ закон.** В правиле: `develop` default + PR `develop→main`. У Ozero: `dev` default, ветки `feat/`/`fix/` от `dev`, **squash-merge без PR** (см. global `feedback_no_pull_requests`), main только по явной команде. При конфликте — побеждают глобальные/проектные правила.
- [`translate.md`](C:/Soft/Projects/Ozero/.claude/rules/translate.md) — **Только baseline-локали.** Из правила берём список обязательных локалей: `ru`, `en`, `es`, `pt`. Архитектура (Next.js URL-locales, namespaced JSON) **не применима** — у Ozero Android `values-{en,es,pt}/strings.xml`. Сейчас активны только `ru`+`en` (W9.1), расширение до `es`+`pt` в W9.2.

<!-- === rules:end === -->

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
Две knowledge-системы, обе используются:
- `graphify-out/` — структура кода (AST граф): `GRAPH_REPORT.md` (god nodes, communities), `graph.json`, `manifest.json`. Читать `GRAPH_REPORT.md` для ориентирования; для связей — `graphify query "..."`, `graphify path "A" "B"`, `graphify explain "..."`. После правок кода — `graphify update .`. Wiki-subdir у graphify не генерируется в этом проекте, не искать.
- `.memory/knowledge/index.md` — project knowledge (концепции/lessons/connections), compiled из daily logs. Использовать для "почему так сделано", прошлых багов, неочевидных решений.
