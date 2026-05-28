---
title: GitHub Actions run-level polling
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# GitHub Actions run-level polling

## Key Points
- `gh run watch` может падать на jobs endpoint 404 и не годится как единственный watcher.
- Надёжный мониторинг должен быть привязан к конкретному run ID и poll-ить run-level status до terminal event.
- Check-runs и artifacts нужны для детализации после terminal failure, но не заменяют run anchor.
- Ошибка watcher-а опасна: CI может уже упасть, пока агент считает мониторинг активным.

## Details

В сессии 2026-05-28 `gh run watch` падал на jobs endpoint 404, поэтому мониторинг был переведён на polling через `gh run view` по конкретному run ID. Позже пользователь указал критическую ошибку: CI уже около десяти минут был terminal failure, а ассистент всё ещё считал мониторинг активным.

После этого CI разбор вёлся через конкретные run IDs: `26583636181`, `26584493929`, `26585604206`, `26586870628`, `26587662879`. Для падений скачивались logs/artifacts и test reports, а успешность подтверждалась отдельно для CI и Gradle Wrapper Validation.

## Related Concepts
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/gh-run-list-watcher-race]]
- [[concepts/ci-gradle-log-reading]]

## Sources
- [[daily/2026-05-28]]: session 16:54 зафиксировал переход с `gh run watch` на run-level polling из-за jobs endpoint 404.
- [[daily/2026-05-28]]: session 19:02 зафиксировал ошибку watcher-а и необходимость следить за конкретным run ID до terminal status.
- [[daily/2026-05-28]]: session 19:39 подтвердил зелёные CI и Wrapper Validation run IDs.
