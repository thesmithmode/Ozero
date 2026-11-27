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
| sing-box | протоколы и транспорты libbox | Профили, подписки, автоматический выбор и цепочки |
| Opera Proxy | TCP SOCKS5 | Выбор региона и сервера; VPN или прокси |
| MASQUE | CONNECT-IP / HTTP/3 или HTTP/2 | Отдельный профиль WARP с автоматическим fallback |
| Telegram WSS | TLS WebSocket + WARP | Telegram DC через WSS, остальной трафик через WARP |
| Adaptive VPN | VPN / TCP | Ручной выбор режима, региона и транспорта |

Каждый движок изолирован в отдельном Gradle-модуле и подключается через интерфейс `EnginePlugin`.

## Архитектура

- Модульная архитектура: каждый транспорт — отдельный Gradle-модуль `engine-*`
- Единый интерфейс `EnginePlugin` — приложение не зависит от деталей транспорта
- Расширяемая система плагинов
- Настраиваемый kill-switch в режиме VPN; при отказе движка блокирует трафик согласно политике маршрутизации.
- URnetwork также может работать как фоновый relay при подключении через другой движок; режим и вознаграждение зависят от настроек и сервиса.
- Релизы устанавливаются вручную. Код проверки Ed25519 пока не подключён к интерфейсу или фоновой проверке. См. [форматы подписей](docs/build.md#подписи-опубликованного-обновления).
- Hardening сборки: R8 minify + shrink, обфускация классов
- Per-engine UI с настройками, без общего конфигурационного экрана

## Сборка

```bash
git clone --branch dev https://github.com/thesmithmode/Ozero.git
cd Ozero
```

Перед сборкой нужно подготовить нативные библиотеки и SDK-артефакты; для release также нужны переменные подписи APK и публичный ключ обновлений. Полный порядок — [сборка](docs/build.md). Простого клонирования недостаточно для подготовки всех движков.

## Документация

- [Архитектура и жизненный цикл движков](docs/architecture.md)
- [Сборка, нативные артефакты и выпуск APK](docs/build.md)
- Движки: [WARP](engine-warp/README.md), [URnetwork](engine-urnetwork/README.md), [FPTN](engine-fptn/README.md), [Opera Proxy](engine-opera/README.md), [MASQUE](engine-masque/README.md), [Telegram WSS](engine-tgwss/README.md), [Adaptive VPN](engine-gfvpn/README.md)
- [Текущий план](PLAN.md)

Разработка ведётся в `dev`. GitHub открывает `main` по умолчанию; изменения для разработки следует направлять в `dev`. Push в `main` запускает выпуск APK.

## Лицензия

GPLv3 — см. [LICENSE](LICENSE).
