---
title: Release audit tag and SHA grounding
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Release audit tag and SHA grounding

## Key Points
- Аудит между релизами начинается с проверки фактических тегов и SHA, а не с предположений о локальном состоянии.
- Если локального тега нет, нужно fetch tags или явно зафиксировать, какой SHA считается последним релизом.
- Масштаб diff должен быть указан, потому что сотни изменённых файлов повышают регрессионный риск.
- Аудит без правок должен оставаться read-only и отделяться от последующего fix-коммита.

## Details

При аудите `v0.2.11 -> v1.0.3` локально не оказалось тега `v1.0.3`. Это было признано блокером точного релизного сравнения: перед выводами нужно синхронизировать теги или явно назвать фактический release SHA.

После grounding аудит был выполнен без правок и выделил регрессии: URnetwork readiness только по peer count, sing-box auto-chain без фильтра unsupported transports, а также риск legacy DNS outbound в chain config. Отдельно был отмечен масштаб diff около 285 файлов, что само по себе требует приоритизации P0/P1 по доказанным фактам.

## Related Concepts
- [[concepts/github-release-asset-name-verification]]
- [[connections/release-status-vs-asset-verification]]
- [[concepts/release-process]]

## Sources
- [[daily/2026-05-28]]: session 17:27 указал, что локально нет тега `v1.0.3`, поэтому нужен fetch tags или явный release SHA.
- [[daily/2026-05-28]]: session 17:27 описал аудит `v0.2.11 -> v1.0.3` без правок и масштаб diff `285 files changed`.
- [[daily/2026-05-28]]: session 17:47 повторно зафиксировал неопределённость локального тега и необходимость подтвердить последний релиз.
