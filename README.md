# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![F-Droid](https://img.shields.io/f-droid/v/ru.ozero.app.svg)](https://f-droid.org/packages/ru.ozero.app/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-Channel-26A5E4?logo=telegram)](https://t.me/ozero_app)

**Один клик — интернет без границ.**

Ozero — Android-комбайн обхода блокировок и DPI-цензуры. Один APK, в котором под одной кнопкой работают все актуальные средства:

- **ByeDPI** — локальный DPI-обход без сервера (SNI-фрагментация)
- **Xray-core** — VLESS + Reality + XHTTP / gRPC
- **Hysteria2** — QUIC/UDP + port hopping
- **AmneziaWG 2.0** — WireGuard с полной мимикрией
- **NaiveProxy** — HTTP/2 маскировка под Chrome-стек
- **Tor + obfs4 + Snowflake** — аварийная анонимность

Приложение само выбирает живой метод (параллельный probe 3 кандидатов → первый успешный = активен) и переключается при деградации. Поддерживает **double-hop** (РФ entry → зарубежный exit) для обхода блокировок с двух сторон.

---

## Статус

**Draft / в разработке.** Roadmap — см. `Контекст/ЭТАПЫ.md` и `Контекст/ПЛАН.md` (локально).

## Минимальные требования

- Android 7.0+ (API 24), рекомендуется 10+
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`

## Ключевые принципы

- **Одна кнопка ON/OFF** — ни настройки протоколов, ни ключей
- **Авто-выбор движка** по результатам параллельного probe
- **Internal kill-switch** — при сбое движка трафик блокируется (fail-closed), мимо TUN не идёт
- **Ed25519-подписанные подписки** серверов
- **Security-first**: anti-debug / anti-frida / signature check / R8 full + obfuscator-LLVM
- **Free-to-use**, без Google Play (только F-Droid / GitHub Releases / Telegram)

## Архитектура

См. `Контекст/SPEC.md` (локально) — полная техническая спецификация (стек, интерфейсы, state machines, контракты подписок, security-слой).

## Лицензия

GPLv3 — см. `LICENSE`. Third-party компоненты и их лицензии — см. `NOTICE.md`.

## Установка / сборка

Инструкции по локальной сборке появятся после завершения E0 (setup-фазы).

## Распространение

- **GitHub Releases** — APK + SHA256 + GPG signature (на каждый tag `v*.*.*`)
- **F-Droid** — pending submission
- **Веб**: `ozero.app` (pending)
- **Telegram**: канал анонсов + group поддержки (pending)

## Contribute

Issues и PR — welcome. Перед PR — прочитать `CONTRIBUTING.md`, `docs/threat-model.md`, `SECURITY.md`.
