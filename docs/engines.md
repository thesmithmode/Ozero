# Engines Ozero

6 встроенных движков обхода + 1 запланирован (URnetwork P2P mesh, фаза E15 — см. приватный `Контекст/ПЛАН.md`).

Каждый engine реализует `ru.ozero.coreapi.Engine` — единый интерфейс. Native-сторона за интерфейсом `Lib<X>Delegate` (тестируемая абстракция, реальная JNI-реализация в RT.6).

---

## 1. ByeDPI

| Что | Значение |
|-----|----------|
| Назначение | Локальный SNI-фрагментатор без сервера, обходит DPI ISP |
| Source | `engine-byedpi/src/main/cpp/byedpi/` (submodule pinned `v0.17.3`) |
| Native | `libbyedpi-<abi>.so`, CMake + NDK (4 ABI) |
| Транспорт | TCP, локальный SOCKS5 |
| Capabilities | TCP=true, UDP=false, localOnly=true, requiresServer=false |
| Build | `build_byedpi.sh` через `Dockerfile.byedpi` |
| Lock tag | `byedpi-<sha8>` |

ByeDPI — последняя линия обороны без bandwidth-оплаты. Работает без backend, идеален как always-on baseline.

## 2. Xray-core (VLESS+Reality+XHTTP)

| Что | Значение |
|-----|----------|
| Назначение | Premium прокси, VLESS+Reality fingerprint mimicry |
| Source | XTLS/Xray-core upstream |
| Native | `libxray.aar` (gomobile bind, multi-ABI) |
| Транспорт | TCP/UDP, локальный SOCKS/HTTP |
| Capabilities | TCP=true, UDP=true, DoH=true, requiresServer=true |
| Build | `build_xray.sh` через `Dockerfile` (Go + gomobile + NDK) |
| Lock tag | `xray-<sha8>` |

Поддерживает множество транспортов: Reality, XHTTP, gRPC, WebSocket. Фаза E2.x добавит конфиг-builder из подписки.

## 3. AmneziaWG 2.0

| Что | Значение |
|-----|----------|
| Назначение | WireGuard с обфускацией junk packets / S1-S2 / H1-H4 |
| Source | amnezia-vpn/amneziawg-go |
| Native | `libamneziawg.aar` (gomobile bind) |
| Транспорт | UDP/WireGuard tun-режим (без локального SOCKS) |
| Capabilities | TCP=true, UDP=true, DoH=true, requiresServer=true |
| Build | `build_amneziawg.sh` через `Dockerfile` |
| Lock tag | `amneziawg-<sha8>` |

В отличие от классического WG, делает peer'а неотличимым от random UDP — DPI не классифицирует. Probe идёт через `delegate.isUp()` (нет socket'а).

## 4. Hysteria2 (native)

| Что | Значение |
|-----|----------|
| Назначение | QUIC/UDP + port hopping + Salamander obfs |
| Source | apernet/hysteria v2 |
| Native | `libhysteria2.aar` (gomobile bind) |
| Транспорт | UDP/QUIC, локальный SOCKS5 |
| Capabilities | TCP=true, UDP=true, DoH=true, requiresServer=true |
| Build | `build_hysteria2.sh` через `Dockerfile` |
| Lock tag | `hysteria2-<sha8>` |

Port hopping + obfs делают трафик трудным для блокировки на L4. Не работает на CGNAT с UDP-фильтром (`StrategyEngine.udpReachable=false` отфильтрует).

## 5. NaiveProxy

| Что | Значение |
|-----|----------|
| Назначение | HTTP/2 (или QUIC) CONNECT через Chromium net stack — fingerprint = Chrome |
| Source | klzgrad/naiveproxy |
| Native | `libnaive-<abi>.so` (4 ABI), извлечён из upstream APK plugin |
| Транспорт | TCP (HTTP/2), локальный SOCKS5 |
| Capabilities | TCP=true, UDP=false, localOnly=false, requiresServer=true |
| Build | `build_naive.sh` (download APK plugin → unzip lib/) |
| Lock tag | `naive-<sha8>` |

NaiveProxy исполняется как subprocess (`Runtime.exec`), не Go-библиотека. TLS-стэк настоящий Chromium → DPI неотличим от обычного браузера.

## 6. Tor + IPtProxy

| Что | Значение |
|-----|----------|
| Назначение | Аварийная анонимность + цензуроустойчивые transports |
| Source | tor-android (Maven Central) + IPtProxy (Maven Central) |
| Native | `libtor-<abi>.so` + `libiptproxy-<abi>.so` (4 ABI каждый) |
| Pluggable transports | obfs4 / snowflake / webtunnel / meek_lite (через IPtProxy 5.4.1: lyrebird+snowflake+dnstt) |
| Транспорт | TCP, локальный SOCKS5 (default 9050) |
| Capabilities | TCP=true, UDP=false, localOnly=false, requiresServer=false |
| Build | `build_tor.sh` (download+extract из Maven AAR, без gomobile) |
| Lock tag | `tor-<sha8>` (несёт оба engine — `tor` + `iptproxy`, два manifest'а) |
| Доставка | On-demand через `:dynamic_tor` (PlayCore SplitInstall, ~200 МБ) |

Tor — last-resort, медленный, но переживает blanket DPI. Bridges подаются в `EngineConfig.Tor.bridges: List<String>`.

Сборка из исходников требует NDK + Go + gomobile + 30+ мин CI; Maven Central — стабильный канал с подписанными AAR. Conjure отложен (gotapdance Android NOT MAINTAINED upstream).

## 7. URnetwork (запланирован, фаза E15)

P2P mesh-VPN от BringYour Inc — last-resort engine для anti-IP-blocklist. Кто-то из пиров пробивает цензуру → mesh раздаёт. Подключение через JWT/GuestMode, MPL-2.0 (Go+gomobile). Под капотом используется в PORTAL TOR (подтверждено через декомпил package paths).

См. `Контекст/ПЛАН.md` раздел 5.2 (E15.1-E15.8).
