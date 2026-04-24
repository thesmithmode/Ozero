# Политика безопасности Ozero

## Поддерживаемые версии

Актуальна последняя минорная версия в GitHub Releases. Предыдущие минорные версии получают критические патчи 90 дней, затем EOL.

| Версия | Статус |
|--------|--------|
| 1.x (latest minor) | Active support |
| 1.x – 1 | Critical patches 90 дней |
| < (latest-1) | EOL |

## Responsible disclosure

**Ozero критичен для безопасности пользователей в юрисдикциях с цензурой. Ответственное раскрытие критично.**

### Как сообщить об уязвимости

- **Email:** `security@ozero.app` (после запуска домена — пока используй GitHub Security Advisory)
- **GitHub Security Advisory** (предпочтительно): `https://github.com/thesmithmode/ozero/security/advisories/new` — приватный репорт, скрыт до публикации
- **PGP key:** будет опубликован в `docs/pgp-security.asc` _(pending, до v1.0.0)_

### Что включить в репорт

- Подробное описание уязвимости
- Proof-of-concept (код/скриншоты/логи) — **не эксплойтить на чужих устройствах**
- Affected component (app/engine-*/common-*/security)
- Версия Ozero и Android SDK, на которых воспроизвели
- Предложенная митигация (если есть)

### SLA

| Severity | Первый ответ | Патч в main | Релиз |
|----------|:------------:|:-----------:|:-----:|
| Critical (RCE, bypass kill-switch, raw-traffic leak) | 24 часа | 48 часов | 72 часа |
| High (DoS, partial info leak, signature bypass) | 72 часа | 7 дней | 14 дней |
| Medium (hardening gap, UX-based leak) | 7 дней | 30 дней | next minor |
| Low (best-practice improvement) | 14 дней | best-effort | next minor |

### Coordinated disclosure

После первого ответа разработчик и reporter договариваются о сроке публичного раскрытия. Default — 90 дней от подтверждения (индустриальная норма). Сокращение срока — при активной эксплуатации.

## Что in-scope

- APK приложения (release и debug flavours)
- Subscription backend (`sub.ozero.app`) — rate-limit abuse, YAML injection, подпись без валидации
- Update channel (GitHub Releases) — supply chain, tampering
- Native библиотеки (`.so` / `.aar`) — уязвимости анализа сборки (reproducibility break)

## Что out-of-scope

- Уязвимости в upstream проектах (Xray, Tor, AmneziaWG и т.д.) — reportить в их соответствующие channels
- Third-party серверы в пользовательских подписках (не наш scope)
- Device-level root attacks (game-over по threat model)
- Social engineering пользователей

## Публичное раскрытие

- GitHub Security Advisories — публикация CVE-эквивалентных записей после патча
- Канал Telegram Ozero — уведомление о критических patches
- `CHANGELOG.md` — запись с CVE/advisory ID

## Поощрения

Bounty-программы нет. Благодарность в `SECURITY-HALL-OF-FAME.md` _(файл создаётся после первого валидного репорта)_ + упоминание в релизе с патчем.

## Команда безопасности

- Solo maintainer: `@thesmithmode`
- Sec-audit: внешний (pending, после E10)

---

См. также:
- `docs/threat-model.md` — STRIDE модель угроз и митигации
- `docs/key-rotation.md` — процедура ротации subscription Ed25519-ключа
