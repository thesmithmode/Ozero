# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Ozero — Android VPN-клиент с поддержкой нескольких транспортных протоколов под единым интерфейсом.

## Поддерживаемые движки

| Engine | Транспорт | Краткое описание |
|--------|-----------|------------------|
| **ByeDPI** | TCP, локальный SOCKS5 | Локальный TCP-прокси, фрагментация SNI |
| **Xray-core** | TCP/UDP | VLESS + Reality + XHTTP/gRPC |
| **Hysteria2** | UDP/QUIC | QUIC-протокол с port hopping и Salamander obfs |
| **AmneziaWG 2.0** | UDP | WireGuard с расширениями (junk packets / S1-S2 / H1-H4) |
| **Tor + IPtProxy** | TCP, on-demand | obfs4 / snowflake / webtunnel |

Дополнительно реализован параллельный probe нескольких кандидатов и автоматическое переключение между движками при деградации соединения.

## Технические требования

- Android 7.0+ (API 24); рекомендуется Android 10+
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`

## Архитектурные принципы

- Единый интерфейс `Engine` для всех транспортов
- Probe-движок выбирает рабочий transport автоматически
- Internal kill-switch: при сбое engine трафик не идёт в обход TUN (fail-closed)
- Подписки серверов верифицируются Ed25519
- Hardening сборки: R8 minify+shrink, anti-debug, signature check

## Сборка

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleDebug
```

Native-артефакты (`libxray.aar`, `libbyedpi.so`, …) скачиваются автоматически по `binaries.lock.yaml` через Gradle plugin `ozero.binaries` (sha256-pinned). Подробнее — [`docs/binaries-pipeline.md`](docs/binaries-pipeline.md).

## Документация

| Документ | Содержание |
|----------|------------|
| [`docs/architecture.md`](docs/architecture.md) | Слои, Gradle модули, Hilt DI-граф |
| [`docs/engines.md`](docs/engines.md) | Технические параметры engine-модулей |
| [`docs/runtime-flow.md`](docs/runtime-flow.md) | Поток Connect → probe → engine.start → tunnel |
| [`docs/binaries-pipeline.md`](docs/binaries-pipeline.md) | Native-сборка, lock-файл, sha256 |
| [`docs/build.md`](docs/build.md) | Локальная сборка APK |
| [`docs/trust-chain.md`](docs/trust-chain.md) | Source → reproducible build → signed APK |
| [`docs/key-rotation.md`](docs/key-rotation.md) | Ed25519 subscription key — модель и процедура |
| [`docs/keystore-setup.md`](docs/keystore-setup.md) | Release keystore — генерация, backup |
| [`docs/privacy.md`](docs/privacy.md) | Политика приватности |
| [`docs/roadmap.md`](docs/roadmap.md) | Карта этапов разработки |

## Лицензия

GPLv3 — см. [`LICENSE`](LICENSE).
