---
title: Release regression self-review gate
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Release regression self-review gate

## Key Points
- Перед релизом regression-fix должен пройти explicit self-review checklist по исходному промпту.
- Зелёный CI не доказывает архитектурную корректность фикса, если решение похоже на timeout или gap.
- Для каждого движка нужен root-cause/evidence: код, лог, reference behavior или sentinel.
- После пользовательского стопа `main` и release не трогать, пока review-fix не пройдёт `dev` CI.

## Details

В сессиях 2026-05-28 первый CI для release regression fixes был признан преждевременным: часть пунктов исходного промпта была покрыта кодом, но не доказана checklist/evidence перед запуском. После публикации `v1.0.3` пользователь остановил процесс и потребовал проверить, не являются ли решения костылями.

Повторное ревью подтвердило разные классы решений. ByeDPI timeout был признан owning-layer контрактом, потому что `ByeDpiEngine.stop()` имеет две фазы drain. URnetwork timeout оказался недостаточным как root fix: readiness должен учитывать SDK `connectionStatus=CONNECTED`, а не только peer count. Для sing-box был найден обходной path auto-chain без тех же валидаторов, что у auto-select.

## Related Concepts
- [[concepts/release-regression-evidence-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]
- [[connections/release-engine-fix-contract-vs-timeout]]

## Sources
- [[daily/2026-05-28]]: session 16:11 признал, что перед первым CI не все пункты исходного промпта были доказательно закрыты.
- [[daily/2026-05-28]]: sessions 17:20 и 17:22 описали post-release stop и архитектурное ревью ByeDPI, URnetwork и sing-box.
- [[daily/2026-05-28]]: session 17:30 сформулировал правило докладывать только доказанные проблемы с file/line/contract evidence.
