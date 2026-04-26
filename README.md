# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![F-Droid](https://img.shields.io/f-droid/v/ru.ozero.app.svg)](https://f-droid.org/packages/ru.ozero.app/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-Channel-26A5E4?logo=telegram)](https://t.me/ozero_app)

**Один клик — интернет без границ.**

Ozero — Android-комбайн обхода блокировок и DPI-цензуры. Один APK, в котором под одной кнопкой работают все актуальные средства:

| Engine | Транспорт | Назначение |
|--------|-----------|------------|
| **ByeDPI** | TCP, локальный SOCKS5 | DPI-обход без сервера (SNI-фрагментация) |
| **Xray-core** | TCP/UDP | VLESS+Reality+XHTTP/gRPC, premium-канал |
| **Hysteria2** | UDP/QUIC | port hopping + Salamander obfs |
| **AmneziaWG 2.0** | UDP | WireGuard + полная мимикрия (junk packets / S1-S2 / H1-H4) |
| **NaiveProxy** | TCP HTTP/2 | Chromium net stack — fingerprint = настоящий Chrome |
| **Tor + IPtProxy** | TCP, on-demand | obfs4 / snowflake / webtunnel — аварийная анонимность |

Приложение само выбирает живой метод (параллельный probe 3 кандидатов → первый успешный = активен) и переключается при деградации. Поддерживает **double-hop** (РФ entry → зарубежный exit) для обхода блокировок с двух сторон.

---

## Статус

**Draft / в разработке.** Roadmap — `docs/roadmap.md` (публичный) + `Контекст/ЭТАПЫ.md`, `Контекст/ПЛАН.md` (приватные).

## Минимальные требования

- Android 7.0+ (API 24), рекомендуется 10+
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`

## Ключевые принципы

- **Одна кнопка ON/OFF** — ни настройки протоколов, ни ключей
- **Авто-выбор движка** по результатам параллельного probe (`StrategyEngine.pickBest`)
- **Internal kill-switch** — при сбое engine трафик блокируется (fail-closed), мимо TUN не идёт
- **Ed25519-подписанные подписки** серверов (Ed25519 LTK + ротируемый SK — `docs/key-rotation.md`)
- **Security-first**: anti-debug / anti-frida / signature check / R8 full + obfuscator-LLVM
- **Free-to-use**, без Google Play (только F-Droid / GitHub Releases / Telegram)

## Документация

| Документ | О чём |
|----------|-------|
| [`docs/architecture.md`](docs/architecture.md) | Слои, Gradle модули, Hilt DI-граф, расширяемость |
| [`docs/engines.md`](docs/engines.md) | Все 6 встроенных engines + URnetwork (план) |
| [`docs/runtime-flow.md`](docs/runtime-flow.md) | Connect → probe → engine.start → hev-tunnel → FSM |
| [`docs/binaries-pipeline.md`](docs/binaries-pipeline.md) | Native-сборка, lock-файл, sha256, `ozero.binaries` plugin |
| [`docs/build.md`](docs/build.md) | Локальная сборка APK + (опционально) native |
| [`docs/threat-model.md`](docs/threat-model.md) | STRIDE + assumptions + mitigations |
| [`docs/trust-chain.md`](docs/trust-chain.md) | Source → reproducible build → signed APK |
| [`docs/key-rotation.md`](docs/key-rotation.md) | Ed25519 subscription key — модель, процедура, лог |
| [`docs/keystore-setup.md`](docs/keystore-setup.md) | Release keystore — генерация, backup |
| [`docs/backend.md`](docs/backend.md) | Subscription backend (`servers.json` + sig) |
| [`docs/legal.md`](docs/legal.md) | Юридический контракт reverse-engineering |
| [`docs/security-todo.md`](docs/security-todo.md) | Открытые security-задачи |
| [`docs/roadmap.md`](docs/roadmap.md) | Публичная карта этапов |

## Сборка

См. [`docs/build.md`](docs/build.md). Tl;dr:

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleDebug
```

Native-бинари (libxray.aar, libbyedpi.so, …) скачиваются автоматически по `binaries.lock.yaml` через `ozero.binaries` Gradle plugin (sha256-pinned). Подробнее — [`docs/binaries-pipeline.md`](docs/binaries-pipeline.md).

## Распространение

- **GitHub Releases** — APK + SHA256 + Ed25519 signature (на каждый tag `v*.*.*`)
- **F-Droid** — pending submission
- **Веб**: GitHub Pages на этом репо (pending; собственный домен не планируется — server-less архитектура)
- **Telegram**: канал анонсов + группа поддержки (pending)

Google Play **не планируется** — censorship + блокировка VPN-приложений в политике.

## Лицензия

GPLv3 — см. [`LICENSE`](LICENSE). Third-party компоненты и их лицензии — см. [`NOTICE.md`](NOTICE.md).

## Contribute

Issues и PR — welcome. Перед PR прочитать:
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — flow веток, commit-конвенция, код-стайл
- [`docs/threat-model.md`](docs/threat-model.md) — что мы защищаем и от кого
- [`SECURITY.md`](SECURITY.md) — приватный disclosure
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)
