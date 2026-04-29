# CLAUDE.md — Ozero

Правило само-обучения:
После каждой допущенной ошибки и исправления, или нарушения формата, стиля и так далее, в общей любое вновь открывшееся правило должно быть добавлено в этот файл CLAUDE.md проекта,
чтобы впредь ты не допускал подобные ошибки. Следи, чтобы файл оставался минималистичным, без дублирований и повторений, обобщай одинаковые мысли, сокращай текст при необходимости.
Короткий но содержательный CLAUDE.md залог успеха.

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

## Workflow

- Ветки: короткие `fix/`, `feat/`, `refactor/`. Одна задача = одна ветка от dev.
- **Сразу после `git push` side-ветки → squash-merge в dev → удалить ветку (локально+remote) → ТОЛЬКО ПОТОМ ждать CI на dev.** Никогда не ждать CI на side-ветке.
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

## Per-engine UI

- Каждый engine (Xray, Hy2, Awg, Naive, Tor, ByeDpi) обязан иметь settings screen в `app/src/main/java/.../ui/settings/engines/` для пользовательского override config (subscription URL, server picker, args, bridges, и т.д.).
