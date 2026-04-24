## Что и почему

<!-- 1–3 предложения: что делает PR и зачем. Ссылка на issue. -->

## Тип изменения

- [ ] FEAT (новая функциональность)
- [ ] FIX (исправление бага)
- [ ] REFACTOR (реструктуризация без изменения поведения)
- [ ] PERF (оптимизация)
- [ ] DOCS (только документация)
- [ ] TEST (только тесты)
- [ ] CHORE (tooling, зависимости, CI)

## Security-sensitive?

- [ ] Нет
- [ ] Да — и я отметил `SECURITY-SENSITIVE:` в title и указал конкретную причину

<!-- Security-sensitive трогает: security/, common-crypto/, common-vpn/, engine-*, build-tools/, subscription-подпись, APK signing -->

## Checklist

- [ ] Ветка `feat/*` или `fix/*` от `dev`
- [ ] Коммиты формата `ПРЕФИКС: описание на русском`
- [ ] Без `Co-Authored-By:` AI-подписей
- [ ] `./gradlew ktlintCheck detekt` зелёные
- [ ] `./gradlew testDebugUnitTest` проходит
- [ ] Coverage ≥ 90% (jacoco)
- [ ] Обновлены `README.md` / `docs/` если меняется архитектура / операции
- [ ] Нет секретов в коде, логах, тестах
- [ ] Threat model (`docs/threat-model.md`) актуальна — или обновлена

## Тестирование

<!-- Как проверялось. Конкретные команды, сценарии, устройства. -->

## Связанные issue / spec

<!-- Закрывает: #123 | См. `Контекст/SPEC.md §X` -->
