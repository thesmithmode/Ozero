---
title: Test retention evidence standard
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Test retention evidence standard

## Key Points
- Тесты нельзя удалять без 100% доказательства, что они бесполезны и заметно замедляют CI.
- Приоритет усиления CI: подключать выпавшие module tests и coverage gates, а не сокращать тесты.
- Нужно проверять, что тесты не только существуют, но реально стартуют в GitHub Actions.
- N=0 или skipped modules являются false-green риском даже при зелёном общем CI.

## Details

После вопроса о гарантиях для URnetwork, ByeDPI и sing-box пользователь разрешил добавлять интеграционные/E2E тесты, если это усилит CI и не сломает существующие проверки. При этом было уточнено, что удалять тесты можно только при одновременном доказательстве бесполезности и существенного замедления CI.

Расширение CI подтвердило системную дыру: часть module tests существовала, но не попадала в основной `CI` job на `dev`. Новый job для extra modules вскрыл реальные latent failures и coverage gaps, включая `singbox-config`, `engine-singbox`, `singbox-room`, `singbox-subscription`, `singbox-fmt`, `shared-warp-settings`, `engine-masterdns` и другие модули.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-module-test-coverage-gap]]
- [[connections/extra-module-ci-exposes-stale-fakes]]

## Sources
- [[daily/2026-05-28]]: session 18:24 сформулировал запрет удалять тесты без доказательства бесполезности и CI-замедления.
- [[daily/2026-05-28]]: session 18:24 подтвердил риск tests-written-but-not-started для ряда модулей.
- [[daily/2026-05-28]]: sessions 19:02-19:39 показали, что новый CI job вскрыл скрытые stale fakes, compile setup gaps и coverage gaps.
