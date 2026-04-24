# Third-Party Notices

Ozero встраивает и интегрирует следующие компоненты третьих сторон. Каждый компонент сохраняет свою оригинальную лицензию.

## Перечень компонентов

| Компонент | Лицензия | Источник | Роль в Ozero |
|-----------|----------|----------|--------------|
| Xray-core | MPL-2.0 | https://github.com/XTLS/Xray-core | Основной proxy-engine |
| AmneziaWG Android | MIT + GPLv2 | https://github.com/amnezia-vpn/amneziawg-android | WireGuard с мимикрией |
| Tor | BSD-3-Clause | https://torproject.org | Аварийный анонимный engine |
| obfs4proxy | BSD-2-Clause | https://gitlab.com/yawning/obfs4 | Pluggable transport для Tor |
| Snowflake | BSD-3-Clause | https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake | WebRTC-based PT для Tor |
| NaiveProxy | BSD-3-Clause | https://github.com/klzgrad/naiveproxy | HTTP/2 proxy с Chromium-stack |
| ByeDPI | GPLv3 | https://github.com/hufrea/byedpi | Локальный DPI-обход (SNI-фрагментация) |
| hev-socks5-tunnel | MIT | https://github.com/heiher/hev-socks5-tunnel | tun2socks bridge |
| BouncyCastle | MIT-like | https://www.bouncycastle.org/ | Ed25519 crypto для subscription-верификации |
| OkHttp | Apache-2.0 | https://square.github.io/okhttp/ | HTTP client |
| Ktor Client | Apache-2.0 | https://ktor.io/ | Альтернативный HTTP client |
| Kotlin / AndroidX / Jetpack Compose / Hilt / Room | Apache-2.0 | https://developer.android.com/ | Android framework & UI |

## Xray-core (MPL-2.0)

**Источник**: https://github.com/XTLS/Xray-core

**Pin версии**: точный upstream tag фиксируется в `build-tools/versions.lock` (текущий базовый — `v25.10.1`, см. `docs/trust-chain.md`).

**Роль**: Основной proxy-engine проекта. Обеспечивает поддержку протоколов VLESS, Reality, XHTTP, Hysteria2, Trojan и других. Собирается самостоятельно из исходников через gomobile bind в AAR-артефакт.

**Лицензия**: Mozilla Public License 2.0 (MPL-2.0) — совместима с GPLv3.

---

## AmneziaWG Android (MIT + GPLv2)

**Источник**: https://github.com/amnezia-vpn/amneziawg-android

**Роль**: Реализация WireGuard с добавленной мимикрией трафика для обхода DPI-системы. Интегрируется как компонент выбора протокола в Ozero.

**Лицензия**: MIT (основной код) + GPLv2 (отдельные фрагменты). Возможная несовместимость GPLv2-only с GPLv3 требует уточнения (см. `docs/legal.md` раздел «Совместимость лицензий»).

---

## Tor (BSD-3-Clause)

**Источник**: https://torproject.org

**Роль**: Аварийный анонимный engine. Применяется как fallback-механизм при невозможности подключиться через другие протоколы.

**Лицензия**: BSD-3-Clause — совместима с GPLv3.

---

## obfs4proxy (BSD-2-Clause)

**Источник**: https://gitlab.com/yawning/obfs4

**Роль**: Pluggable transport (PT) для Tor. Обеспечивает обфускацию протокола Tor для обхода статистического анализа.

**Лицензия**: BSD-2-Clause — совместима с GPLv3.

---

## Snowflake (BSD-3-Clause)

**Источник**: https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake

**Роль**: WebRTC-based pluggable transport для Tor. Позволяет подключаться к Tor через обычные браузерные соединения (proxy-bridges).

**Лицензия**: BSD-3-Clause — совместима с GPLv3.

---

## NaiveProxy (BSD-3-Clause)

**Источник**: https://github.com/klzgrad/naiveproxy

**Роль**: HTTP/2 proxy с использованием Chromium-stack для маскировки под обычный браузерный трафик. Один из основных протоколов обхода блокировок.

**Лицензия**: BSD-3-Clause — совместима с GPLv3.

---

## ByeDPI (GPLv3)

**Источник**: https://github.com/hufrea/byedpi

**Роль**: Локальный DPI-обход средствами SNI-фрагментации, рассоединения потоков и других техник на уровне TCP. Интегрируется как альтернативный режим обхода.

**Лицензия**: GPLv3 — полностью совместима, так как Ozero сам распространяется под GPLv3.

---

## hev-socks5-tunnel (MIT)

**Источник**: https://github.com/heiher/hev-socks5-tunnel

**Роль**: tun2socks bridge. Обеспечивает трансляцию системного TUN-интерфейса в SOCKS5-трафик для маршрутизации через proxy.

**Лицензия**: MIT — совместима с GPLv3.

---

## BouncyCastle (MIT-like)

**Источник**: https://www.bouncycastle.org/

**Роль**: Крипто-библиотека для Ed25519 подписей. Используется при верификации данных подписки и валидации конфигураций.

**Лицензия**: MIT-like — совместима с GPLv3.

---

## OkHttp (Apache-2.0)

**Источник**: https://square.github.io/okhttp/

**Роль**: HTTP client-библиотека для выполнения сетевых запросов. Используется при загрузке конфигураций и проверке статуса блокировок.

**Лицензия**: Apache License 2.0 — совместима с GPLv3.

---

## Ktor Client (Apache-2.0)

**Источник**: https://ktor.io/

**Роль**: Альтернативный асинхронный HTTP client. Опциональное использование для снижения зависимостей в certain scenarios.

**Лицензия**: Apache License 2.0 — совместима с GPLv3.

---

## Kotlin / AndroidX / Jetpack Compose / Hilt / Room (Apache-2.0)

**Источник**: https://developer.android.com/

**Роль**: Framework и инструменты разработки Android-приложений. Kotlin — язык реализации. AndroidX, Jetpack Compose — UI-фреймворк. Hilt — dependency injection. Room — persistence (база данных).

**Лицензия**: Apache License 2.0 — совместима с GPLv3.

---

## Условия распространения

**Ozero** сам распространяется под **GNU General Public License v3 (GPLv3)**. Все встраиваемые компоненты сохраняют свои оригинальные лицензии.

Полные тексты лицензий третьих сторон будут включены в релизный APK-файл в файл `third_party_licenses.json`, генерируемый плагином gradle `oss-licenses-plugin` при сборке.

Все компоненты используются в соответствии с условиями их оригинальных лицензий. Совместимость лицензий обеспечена выбором GPLv3 в качестве основной лицензии Ozero (см. более подробное обсуждение в `docs/legal.md`).
