Русский | [English](README.en.md) | [Español](README.es.md) | [Português](README.pt.md)

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Open-source Android VPN-клиент с поддержкой нескольких транспортных движков и обфускации трафика под единым интерфейсом.

## Требования

- Android 7.0+ (API 24); рекомендуется Android 10+
- ABI: `arm64-v8a`

## Поддерживаемые движки

| Движок | Транспорт | Назначение |
|--------|-----------|------------|
| ByeDPI | локальный TCP-прокси | Фрагментация SNI, обфускация TLS-handshake |
| WARP (AmneziaWG) | WireGuard/UDP | Cloudflare WARP с расширенными junk/S1-S2/H1-H4 полями |
| FPTN | HTTPS + SNI Reality | Обфускация TLS-handshake под популярные домены |
| URnetwork | P2P mesh | Анонимизация через peer-сеть провайдеров |
| MasterDNS | DNS-over-UDP | Экстренный fallback, развёртывается на своём VPS в 1 клик |

Каждый движок изолирован в отдельном Gradle-модуле и подключается через интерфейс `EnginePlugin`.

## Архитектура

- Модульная архитектура: каждый транспорт — отдельный Gradle-модуль `engine-*`
- Единый интерфейс `EnginePlugin` — приложение не зависит от деталей транспорта
- Расширяемая система плагинов
- Internal kill-switch: при отказе транспорта трафик блокируется (fail-closed)
- Подписки серверов верифицируются Ed25519
- Hardening сборки: R8 minify + shrink, обфускация классов
- Per-engine UI с настройками, без общего конфигурационного экрана

## Сборка

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

Сборка требует переменных окружения для подписи APK и публичного ключа обновлений. Подробности — `.claude/Контекст/Architect.md`.

## Лицензия

GPLv3 — см. [LICENSE](LICENSE).
