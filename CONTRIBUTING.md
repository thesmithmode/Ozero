# Contributing to Ozero

Спасибо, что хотите помочь. Проект критичен для пользователей в цензурированных юрисдикциях — к вкладам относимся серьёзно.

## Перед PR — обязательное чтение

1. `README.md` — что такое Ozero и его принципы
2. `docs/threat-model.md` — STRIDE модель угроз
3. `docs/legal.md` — что можно/нельзя копировать из сторонних проектов
4. `Контекст/SPEC.md` (локально) — техническая спецификация
5. `SECURITY.md` — политика раскрытия уязвимостей

## Рабочий процесс

### 1. Ветки
- `main` — prod, защищена
- `dev` — integration, все фичи идут сюда
- `feat/<короткое-имя>` или `fix/<короткое-имя>` — ваша работа

**Каждая крупная фича = отдельная ветка.** Мелкие фиксы — прямо в `dev`.

### 2. Коммиты
Формат: `ПРЕФИКС: описание`

- Префикс — EN (`FEAT`, `FIX`, `CHORE`, `DOCS`, `REFACTOR`, `TEST`, `PERF`)
- Описание — на русском
- Без `Co-Authored-By:` подписей AI
- Atomic commits — один логический блок = один коммит

Пример: `FEAT: добавить probe-режим в XrayEngine`

### 3. Качество кода
- Kotlin style: `ktlint` (`./gradlew ktlintCheck`)
- Static analysis: `detekt` (`./gradlew detekt`)
- Android Lint: `./gradlew lint`
- Max cyclomatic complexity: 10 на метод

### 4. Тесты
- **TDD-first**: тест пишется ДО реализации
- **Coverage ≥ 90%** line + branch (jacoco gate)
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumentation: `./gradlew connectedDebugAndroidTest`
- Обильное логирование во всех спорных местах (Timber tree)

### 5. Security-sensitive изменения
Любые изменения в:
- `security/` (anti-debug / anti-frida / signature check)
- `common-crypto/` (Ed25519, AES, keys)
- `common-vpn/` (TUN setup, kill-switch)
- `engine-*` (обход, fingerprints)

требуют явной отметки в PR description: `SECURITY-SENSITIVE: <причина>`. Ревью будет строгим.

### 6. Третьесторонние зависимости
- **Новые production deps** — только с approval maintainer
- GPLv3-compatible лицензии (см. `NOTICE.md` + `docs/legal.md`)
- Без tracking, без proprietary blob'ов (F-Droid requirement)

### 7. PR
- PR в `dev` (не `main`)
- Green CI обязателен (ktlint + detekt + tests + coverage ≥90% + build)
- Squash merge (один коммит в `dev` история)
- Resolved review comments перед мержем

## Локальный setup

```bash
git clone https://github.com/thesmithmode/ozero
cd ozero
# Требования:
# - JDK 17
# - Android SDK + NDK r27+
# - Android Studio 2024.1+ (Ladybug) рекомендован

# Активировать git hooks (автоматические проверки при коммите)
./.githooks/install.sh

./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Code of Conduct

Будьте вежливы. Technical critique — welcome, personal attacks — нет. Модерация жёсткая.

## Вопросы

- Telegram группа Ozero (pending)
- GitHub Discussions
- Security: см. `SECURITY.md`
