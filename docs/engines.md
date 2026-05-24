# Engines Ozero

Каждый engine реализует `EnginePlugin` из `:engines-core`. Детали архитектуры — `architecture.md`.

## Статус

| Engine | Статус | Модуль |
|--------|--------|--------|
| ByeDPI | ✅ работает | `engine-byedpi` |
| WARP (AmneziaWG) | ✅ работает | `engine-warp` |
| URnetwork | ✅ работает | `engine-urnetwork` |
| FPTN | ✅ работает | `engine-fptn` |
| MasterDNS | ✅ работает | `engine-masterdns` |
| Xray-core | ⏳ stub | — |
| Hysteria2 | ⏳ stub | — |
| NaiveProxy | ⏳ stub | — |
| Tor + IPtProxy | ⏳ stub | — |

---

## ByeDPI

| | |
|---|---|
| Назначение | Локальный TCP-прокси с фрагментацией SNI, без удалённого сервера |
| Транспорт | TCP, локальный SOCKS5 |
| Native | `libbyedpi-<abi>.so` (CMake + NDK) |
| Source | `engine-byedpi/src/main/cpp/byedpi/` (submodule pinned v0.17.3) |
| Capabilities | TCP=true UDP=false localOnly=true requiresServer=false supportsUpstreamSocks=false |

Terminal proxy — upstream chain не поддерживает. Подходит как always-on baseline без внешнего сервера.

`waitSocksReady`: после старта JNI движок опрашивает SOCKS5 порт через `withTimeoutOrNull(5 000)` с retry 100 мс, проверяя живость handshake.

## WARP (AmneziaWG)

| | |
|---|---|
| Назначение | Cloudflare WARP через AmneziaWG с защитой от DPI (junk packets / S1-S2 / H1-H4) |
| Транспорт | UDP/WireGuard TUN-режим |
| Native | AmneziaWG Go AAR (gomobile bind) |
| Source | amnezia-vpn/amneziawg-go |
| Capabilities | TCP=true UDP=true localOnly=false requiresServer=true supportsUpstreamSocks=false |

Config: raw INI (WireGuard Quick Config format) с расширенными полями Jc/Jmin/Jmax/S1/S2/H1-H4. Параметры подбираются автоматически (`WarpAutoConfig`). Конфигурации хранятся в `WarpConfigSlotStore` (слоты).

TUN-режим: не создаёт SOCKS-интерфейс, TUN fd передаётся напрямую через `TunFdAcceptor.attachTun`.

## URnetwork

| | |
|---|---|
| Назначение | Decentralized P2P provider mesh, анонимизация через peer-сеть |
| Транспорт | P2P (Go SDK), TUN-режим |
| Native | URnetwork Go AAR (gomobile) |
| Source | urnetwork/sdk (MPL-2.0) |
| Capabilities | TCP=true UDP=true localOnly=false requiresServer=true supportsUpstreamSocks=false |

Аутентификация: guest JWT → client JWT (persisted в `UrnetworkConfigStore`). Локация выбирается через `setPreferredLocation(UrnetworkLocationSelection)` с приоритетом city > region > country. Performance profile — через `applyPerformanceProfile(windowType, fixedIp)`.

TUN fd передаётся через `attachTun`. SDK excludeSelf из своего TUN — self-трафик обходит туннель (нет routing loop). IP определяется через `selectedLocationInfo()` (country+countryCode из SDK без внешнего запроса).

## FPTN

| | |
|---|---|
| Назначение | HTTPS-туннель с SNI Reality / TLS-fingerprint обфускацией под популярные домены |
| Транспорт | HTTPS (WebSocket), Reality TLS handshake |
| Native | `libfptn_native_lib.so` (CMake+NDK, Conan2 для зависимостей) |
| Source | `engine-fptn/src/main/cpp/` + camouflage-tls fork |
| Capabilities | TCP=true UDP=false localOnly=false requiresServer=true supportsUpstreamSocks=false |

Аутентификация: токен `fptn:…` / `fptnb:…` через POST `/api/v1/login` (HTTPS+SNI). Список серверов зашифрован внутри токена. Bypass-метод выбирается per-server: `SNI` (default, подмена домена), `OBFUSCATION` (TLS-обфускация), `SNI_REALITY_*` (xtls-uTLS профили: Chrome 145–147, Firefox 149, Safari 26, Yandex 24–26). SNI домен — любой публичный (по умолчанию `ads.x5.ru`).

## MasterDNS

| | |
|---|---|
| Назначение | DNS-туннель — экстренный fallback при недоступности стандартных протоколов |
| Транспорт | UDP/53 (DNS-over-UDP с шифрованием) |
| Native | subprocess `libmdnsvpn.so` через `ProcessBuilder` (НЕ System.loadLibrary) |
| Source | upstream [MasterDnsVPN](https://github.com/masterking32/MasterDnsVPN) |
| Capabilities | TCP=true UDP=false localOnly=false requiresServer=true supportsUpstreamSocks=true |

Subprocess-pattern: бинарь запускается отдельным процессом, наследует UID main-процесса. Routing — через WARP (TUN авто, без SOCKS) или поверх другого SOCKS-engine (`--socks5-proxy-url`).

Авторазвёртывание сервера: юзер вводит IP+login+password VPS → SSH-сессия по шагам ставит Docker, собирает образ из upstream скрипта, открывает 53/udp в firewall, извлекает encryption key. Прогресс % + лог шагов отображаются в UI. После Done клиент полностью настроен — resolvers, encrypt key, server IP заполняются автоматически. Подробности — [`masterdns-server-setup.md`](masterdns-server-setup.md).

Скорость ~1–3 Мбит/с, латентность 200–500 мс — DNS не предназначен для bulk-трафика. Подходит только как auto-fallback в chain или при полной недоступности других движков.

---

## Запланированные движки (stub, модуль отсутствует)

### Xray-core (VLESS+Reality+XHTTP)
- Native: `libxray.aar` (gomobile bind, XTLS/Xray-core)
- Capabilities: TCP=true UDP=true requiresServer=true **supportsUpstreamSocks=true** (proxySettings.tag)
- Единственный движок пригодный для middle-звена в chain

### Hysteria2
- Native: `libhysteria2.aar` (gomobile, apernet/hysteria v2)
- Транспорт: UDP/QUIC + port hopping + Salamander obfs
- Не работает на CGNAT с UDP-фильтром

### NaiveProxy
- Native: `libnaive-<abi>.so` (Chromium net stack, HTTP/2 CONNECT)
- Запускается как subprocess через `Runtime.exec`

### Tor + IPtProxy
- Native: `libtor.so` + `libiptproxy.so` (Maven Central AAR)
- Pluggable transports: obfs4 / snowflake / webtunnel
- Планировался как Dynamic Feature Module (PlayCore), будет обычной library
