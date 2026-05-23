Русский | [English](README.en.md) | [Español](README.es.md) | [Português](README.pt.md)

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Бесплатный Android VPN-клиент с поддержкой нескольких транспортных протоколов под единым интерфейсом.

## Требования

- Android 7.0+ (API 24); рекомендуется Android 10+
- ABI: `arm64-v8a`

## Архитектура

- Модульная архитектура: каждый транспорт изолирован в отдельном Gradle-модуле
- Единый интерфейс `Engine` — приложение не зависит от деталей транспорта
- Расширяемая система плагинов движков
- Internal kill-switch: при отказе транспорта трафик блокируется (fail-closed)
- Подписки серверов верифицируются Ed25519
- Hardening сборки: R8 minify + shrink

## Сборка

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

Сборка требует переменных окружения для подписи APK и публичного ключа обновлений.

## Лицензия

GPLv3 — см. [LICENSE](LICENSE).
