# CLAUDE.md — Ozero

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

## Logging

- BootFileLogger (filesDir/debug/boot.log) — persistent, append-only, init из attachBaseContext.
- LogcatReader → in-memory ring buffer для UI Logs tab.
- Boot log tab отдельный (Settings → Boot log), очистка только вручную.
