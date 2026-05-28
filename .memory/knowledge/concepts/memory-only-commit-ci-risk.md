---
title: Memory-only commit CI risk
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Memory-only commit CI risk

## Key Points
- Изменения только в `.memory/` являются текстовыми изменениями вне тестового покрытия и сами по себе не несут существенного CI-риска.
- Даже memory-only dirty file может заблокировать checkout, merge или release workflow.
- `.memory/` всё равно нужно коммитить сразу по правилам репозитория, а push прикреплять к ближайшему рабочему push.
- Проверка `git status` после сессии обязательна, потому что hooks могут сделать `.memory/daily/*` dirty.

## Details

Во время release workflow 2026-05-28 незакоммиченный файл `.memory/daily/2026-05-28.md` заблокировал переход на `main`. Проблема была снята отдельным memory-коммитом в `dev`, после чего workflow продолжился через зелёный CI, fast-forward merge и release monitoring.

Пользователь уточнил, что memory-only commit не требует избыточной перестраховки по CI-рискам, потому что это текстовый файл вне тестового покрытия. Это не отменяет правила о немедленном коммите `.memory/`: цель правила не только CI, но и сохранность контекста и отсутствие блокировки git-процедур.

## Related Concepts
- [[concepts/wiki-knowledge-base]]
- [[concepts/git-active-branch-discipline]]
- [[concepts/github-actions-run-id-monitoring]]

## Sources
- [[daily/2026-05-28]]: session 14:57 зафиксировал memory-only commit `2fba4ca7` как способ снять checkout/merge блокировку.
- [[daily/2026-05-28]]: session 17:22 указал, что `.memory/daily/2026-05-28.md` стала dirty после hook и её нельзя потерять.
- [[daily/2026-05-28]]: session 20:02 зафиксировал отдельный `DOCS` commit для `.memory/` перед merge.
