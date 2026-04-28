# Ozero — Roadmap

Публичная карта этапов разработки.

## Принципы

- TDD-first — тесты до реализации, coverage ≥90% (jacoco gate)
- Одна крупная фича = одна ветка `feat/*` → squash merge в `dev`
- Каждый этап завершается зелёным CI и проверкой на реальном устройстве
- Hardening и security-задачи параллельны фичам, не «напоследок»

## Фазы

### E0 — Setup
Git repo, GPLv3, базовая документация (architecture, threat model, trust chain, key rotation), build-tools (`build_xray.sh`, Dockerfile), Gradle skeleton, CI skeleton, coverage gate 90%, ktlint/detekt.

### E1 — MVP с ByeDPI
Базовый сценарий «одна кнопка — туннель работает». VpnService + TUN + hev-socks5-tunnel. Internal kill-switch (fail-closed). Persistent notification, QuickTile, BootReceiver.

### E2 — Подписки + DoH
Ed25519-подписанные подписки серверов. Парсеры URI (`vless`, `hy2`, `ss`, `trojan`, `tuic`, `vmess`). DoH-клиент. ServersScreen.

### E3 — Xray
VLESS + Reality + XHTTP / gRPC. Параллельный probe (3 кандидата одновременно → первый живой = активный). Xray internal DoH.

### E4 — Hysteria2
QUIC/UDP, port range 20000–50000, обфускация Salamander. CGNAT detection → fallback на VLESS.

### E5 — AmneziaWG 2.0
WireGuard-вариант с расширениями (JunkPackets / S1-S2 / H1-H4).

### E6 — NaiveProxy
HTTP/2 CONNECT через Chromium network stack.

### E7 — Tor + Pluggable Transports
Tor 0.4.x + obfs4proxy + snowflake + conjure. Dynamic feature module (`:dynamic-tor`) — скачивается по запросу (~50 МБ).

### E8 — Multi-hop
Цепочка outbound через несколько Xray-узлов. UI выбора пары.

### E9 — DNS + split-tunnel
DoH интегрирован во все движки. Per-app split-tunnel (ALL / BYPASS_LAN / ALLOWLIST / BLOCKLIST).

### E10 — Security hardening
Anti-debug (TracerPid), anti-frida (`/proc/self/maps`), anti-emulator, APK signature verify, R8 full, обфускация строк (AES-128), manifest hardening (`allowBackup=false`, `networkSecurityConfig`).

### E11 — UI и диагностика, i18n
SettingsScreen (полный набор опций). DiagnosticsScreen. Локализация RU + EN. Accessibility (TalkBack).

### E12 — Self-update
GitHub Releases API → Ed25519-verified APK download → PackageInstaller prompt.

### E13 — Финальное тестирование
Регрессионные сценарии на реальных устройствах. Soak-test 8 часов. Security audit (JADX + Frida + emulator + signature patch). iperf3 ≥100 Mbps.

### E14 — Release
GitHub Actions на push тега `v*.*.*`. Signed APK + SHA256 + GPG.

## Статус фаз

| Фаза | Статус | Ветка |
|------|--------|-------|
| E0 | Done | `feat/e0-setup` |
| E1 | Done | `feat/e1-mvp-byedpi` |
| E2 | Done | `feat/e2-subscriptions` |
| E3 | Done (AAR + DI отложены до интеграции рантайма) | `feat/e3-xray` |
| E4 | Done (AAR + DI отложены до интеграции рантайма) | `feat/e4-hysteria2` |
| E5 | Done (AAR + DI отложены до интеграции рантайма) | `feat/e5-amneziawg` |
| E6 | Done (binary + DI отложены до интеграции рантайма) | `feat/e6-naive` |
| E7 | Done (PT-бинари + DI + PlayCore SplitInstall отложены) | `feat/e7-tor` |
| E8 | Done (UI выбора пары — в E11) | `feat/e8-multi-hop` |
| E9 | Done (split-tunnel ядро; UI настроек в E11) | `feat/e9-dns-split-tunnel` |
| E10 | Done (obfuscator-LLVM отложен — требует custom NDK plugin) | `feat/e10-security` |
| E11 | Partial — i18n RU+EN, DiagnosticsTester, strings; Compose UI отложен | `feat/e11-ui` |
| E12 | Done (PackageInstaller invocation отложен — Activity-bound) | `feat/e12-self-update` |
| **RT** | Pending — блокер E13: AAR/binary артефакты, DI Hilt всех движков, VpnService→Engine pipeline, Compose UI, PlayCore SplitInstall для `:dynamic-tor`, manifest для Android 13/14/15 | `feat/rt-runtime-integration` |
| E13 | Pending — gated by RT | `feat/e13-final-tests` |
| E14 | Pending | `feat/e14-release` |

## Nice-to-have (v2.0+)

- P2P-распространение конфигов (BitTorrent DHT)
- Shadowsocks-2022 + plugin
- Trojan + ShadowTLS v3
- iOS-вариант
- Десктоп (Windows / Linux / macOS)

## Contributing

См. `CONTRIBUTING.md`.
