# CLAUDE.md — Ozero

Правило само-обучения:
После каждой допущенной ошибки и исправления, или нарушения формата, стиля и так далее, в общей любое вновь открывшееся правило должно быть добавлено в этот файл CLAUDE.md проекта,
чтобы впредь ты не допускал подобные ошибки. Следи, чтобы файл оставался минималистичным, без дублирований и повторений, обобщай одинаковые мысли, сокращай текст при необходимости.
Короткий но содержательный CLAUDE.md залог успеха.

## Что такое Ozero (видение продукта)

Ozero — **обёртка-аггрегатор над набором рабочих VPN/anti-DPI движков** (ByeDPI, Xray VLESS+Reality, Hysteria2, AmneziaWG2, NaiveProxy, Tor с PT, URnetwork P2P и т.д.). Не «ещё один VPN», а единый APK который содержит несколько проверенных решений и даёт юзеру:

1. **Выбрать конкретный движок** для обхода блокировок (manual mode).
2. **Выстраивать каскад из нескольких движков последовательно** (chained engines) — вручную или автоматически. Пример: `ByeDPI (DPI bypass) → Xray по RU-прокси (geo-binding) → внешний прокси (финальный outbound)`. Каждое звено решает свою задачу.
3. **Динамически перенастраивать каскад** под условия среды (ТСПУ зажат → ByeDPI первым; целевой сайт блочит RU IP → exit не-РФ; и т.д.).

Архитектурно это значит:
- Каждый движок — изолированный «плагин» (как микросервис) со стабильным контрактом, своими native libs, независимым обновлением. Падение одного не валит соседей. Удалить движок = убрать одну папку.
- `Engine.start(config, upstream)` принимает upstream proxy → следующее звено каскада маршрутизируется через предыдущее.
- Single-engine — частный случай chain длины 1.

Текущее состояние (2026-04-30): single-engine архитектура, реально работает только ByeDPI, остальные — stub. Каскад не реализован. См. `.claude/Контекст/Architect.md` (раздел «Альтернативная архитектура») и `AUDIT.md` для деталей.

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

- Ветки: короткие `fix/`, `feat/`, `refactor/`. Одна задача = одна ветка от dev.
- **Сразу после `git push` side-ветки → squash-merge в dev → удалить ветку (локально+remote) → ТОЛЬКО ПОТОМ ждать CI на dev.** Никогда не ждать CI на side-ветке. Удаление обязательно — мёртвые ветки не оставлять.
- main не трогать без явной команды.
- Релизы: тег `v*.*.*` на dev → release.yml соберёт APK.

## Билд

- Один универсальный APK (`assembleRelease`), без dynamic features.
- abiFilters: arm64-v8a, armeabi-v7a, x86_64.
- R8 minify+shrink включены, но Log.* НЕ стрипаются (см. proguard-rules.pro).

## Native libs

- `System.loadLibrary` — **только lazy** через идемпотентный `loadOnce()` (см. `hev.TProxyService`, `ByeDpiProxy`). Никогда в `Application.onCreate`, `attachBaseContext`, init-блоках, companion object init. Нарушение → SIGSEGV в JNI_OnLoad на Application bootstrap (v1.0.1 краш).
- `loadOnce()` для `libhev-socks5-tunnel` вызывать **только из main thread** (через `OzeroVpnService.startVpn` до `serviceScope.launch`). Загрузка с coroutine worker → SIGSEGV в vendor `libglnubia.so` `nubia::Messager::timerLoop` на Nubia/RedMagic (v1.0.3 краш). Правило защищено `OzeroVpnServiceLifecycleTest`.
- `libhev-socks5-tunnel.so` собирать с `APP_CFLAGS=-DPKGNAME=hev` (release.yml + ci.yml). Upstream `src/hev-jni.c` defaults `PKGNAME=hev/htproxy`, без override → `FindClass("hev/htproxy/TProxyService")` = NULL → `RegisterNatives(NULL,...)` → ART `JniAbort` при первом старте VPN (v1.0.2 краш). Защищено `JniContractTest` + step `Assert PKGNAME=hev зашит`.
- `hev.TProxyService` обязан объявлять **все три** `external fun`: `TProxyStartService(String, Int)`, `TProxyStopService()`, `TProxyGetStats(): LongArray`. Upstream `hev-jni.c` регистрирует ровно эти 3 метода через `RegisterNatives`. Отсутствие любого → `NoSuchMethodError` из `Runtime.nativeLoad` → `libhev` не грузится → tunnel не поднимается (v1.0.2 follow-up). Защищено `JniContractTest`.
- `loadOnce()` обязан иметь `catch (e: Throwable)` после специфичных catch-блоков. `Runtime.nativeLoad` может бросить `NoSuchMethodError`, `LinkageError`, `ClassNotFoundException` — все вне `UnsatisfiedLinkError`/`SecurityException`. Без generic catch `loadError` остаётся `null`, диагностика теряется. Защищено `TProxyServiceLogTest`.

## Logging

- BootFileLogger (filesDir/debug/boot.log) — persistent, append-only, init из attachBaseContext.
- LogcatReader → in-memory ring buffer для UI Logs tab.
- Boot log tab отдельный (Settings → Boot log), очистка только вручную.
- `PersistentLoggers.error/warn` — для критичных событий, обязанных попасть в boot.log на диск (errors, warnings, JNI pre-blocking checkpoints для hang-диагностики). На success-events запрещено: `Log.i/d` достаточно — UnifiedLogger пишет и в logcat, и в файл через один канал. Дубль `Log.i + PersistentLoggers.info` на success → шум, удалять.

## Тесты — типовые ловушки

- **Интерфейс изменился** (добавлен/удалён метод) → сразу `grep -r "FakeXxx\|StubXxx\|FakeRepo"` по тестам и обновить все fake-реализации. Compile fail в CI = не обновил.
- **ViewModel/объект с начальным StateFlow-состоянием в `@BeforeEach`**: если тест требует конкретного начального состояния store/repo — создавать VM **внутри теста** ПОСЛЕ `store.setRaw(...)`, не переиспользовать экземпляр из setUp. Иначе coroutine видит null раньше, чем тест успевает установить значение → race → ложный auto-trigger.
- **Material Icons**: использовать только символы из `material-icons-core`. `Icons.Filled.Android`, `Icons.Filled.Apps`, `Icons.Filled.PhoneAndroid` — не существуют в core → compile fail. Placeholder без icon из расширенного набора → `Text("?")`.

## Per-engine UI

- Каждый engine (Xray, Hy2, Awg, Naive, Tor, ByeDpi) обязан иметь settings screen в `app/src/main/java/.../ui/settings/engines/` для пользовательского override config (subscription URL, server picker, args, bridges, и т.д.).

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

**Не скачаны как нерелевантные:** `frontend.md` (Next.js), `python.md` (uv/beartype), `api.md` (HTTP `/llms.txt`), `auth.md` (Google OAuth+JWT), `analytics.md` (Plausible+GTM+YM web), `jobs.md` (Postgres queue), `limits-and-credits.md` (credit ledger), `payments.md`+`yookassa.md`+`lemonsqueezy.md` (биллинг), `main.md` (манифест web-стека).
<!-- === rules:end === -->
