---
title: CI Current Run Batch Failure Triage
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-07-20
---
# CI Current Run Batch Failure Triage

## Key Points
- Текущий `dev` CI нужно разбирать по свежему run и всем failing jobs сразу, а не по истории красных коммитов.
- Compile, style, assertion и coverage failures могут идти разными независимыми корнями в одном run.
- Устаревший green run или локальный commit/push не доказывают состояние `dev`; доказательство — terminal `success` свежего GitHub Actions run.
- Первый красный job полезен для порядка, но пакетный список падений нужен, чтобы не чинить один симптом за итерацию.

## Details

В сессиях 2026-06-04 краснота `dev` неоднократно выглядела как один общий провал, но свежие runs показывали разные failing areas: `buildSrc`, `common-vpn`, `engine-warp`, `singbox + extra modules`, `singbox-config`, `singbox-fmt`, `singbox-subscription` и `shared-warp-settings`. Это требовало агрегировать все текущие failures перед фиксом, иначе один исправленный слой открывал следующий и создавал ощущение движения по кругу.

Правильная диагностика строится от актуального run ID, job summaries, артефактов и точных сигналов вроде `FAILED`, `Coverage`, `violation`, `Exception` и Gradle task boundaries. Исторические заметки и предыдущие SHA помогают как список кандидатов, но не заменяют свежие логи текущего run.

The 2026-06-04 sequence also established a priority rule inside the batch: compile blockers must be removed before interpreting absent JaCoCo reports, while style, assertion, and coverage failures that already have evidence can be grouped into the same repair push. This preserves the complete failure map without confusing an early compiler stop with a coverage deficit.

## Related Concepts
- [[concepts/ci-terminal-success-fresh-run-contract]]
- [[concepts/ci-failure-batch-analysis-before-push]]
- [[concepts/dev-ci-root-cause-sequencing-loop]]
- [[connections/dev-ci-first-failure-sequencing-loop]]

## Sources
- [[daily/2026-06-04]]: sessions around 16:21, 16:36, 17:53, 19:35, 20:39 and 20:50 record that `dev` stayed red across several jobs and that fixes had to be based on fresh run logs rather than old status.
- [[daily/2026-06-04]]: the user clarified that `dev` CI is green only after a completed GitHub Actions run with terminal `success`.
