# Ozero — Roadmap

Публичная карта этапов. Полная техническая декомпозиция — в приватных `Контекст/ЭТАПЫ.md`, `Контекст/SPEC.md`, `Контекст/PRD.md`.

## Принципы

- **TDD-first** — тесты до реализации, coverage ≥90% (jacoco gate)
- **Одна крупная фича = одна ветка** `feat/*` → squash merge в `dev`
- **Каждый этап** завершается зелёным CI + рабочим сценарием на реальном устройстве
- **Security-first** — threat model и hardening параллельны фичам, не «напоследок»

## Фазы

### E0 — Setup ✅ (в процессе)
Git repo, GPLv3, docs (threat model, legal, backend, key rotation, trust chain), build-tools (build_xray.sh, Dockerfile), Gradle skeleton, CI skeleton, coverage gate 90%, ktlint/detekt.

### E1 — MVP «одна кнопка + ByeDPI»
YouTube открывается через ByeDPI после одной кнопки. VpnService + TUN + hev-socks5-tunnel. Internal kill-switch (fail-closed). Persistent notification. QuickTile. BootReceiver.

### E2 — Subscriptions + DoH
Ed25519-подписанные подписки из `sub.ozero.app`. URI парсеры (vless, hy2, ss, trojan, tuic, vmess). DoH клиент. ServersScreen.

### E3 — Xray-ядро
VLESS + Reality + XHTTP / gRPC. Параллельный probe (3 кандидата одновременно → первый живой = активный). Xray internal DoH.

### E4 — Hysteria2 + port hopping
QUIC/UDP, port range 20000–50000, obfs Salamander. CGNAT detection → fallback на VLESS.

### E5 — AmneziaWG 2.0
WireGuard с полной мимикрией (JunkPackets / S1-S2 / H1-H4).

### E6 — NaiveProxy
HTTP/2 CONNECT через Chromium network stack (fingerprint = Chrome).

### E7 — Tor + Pluggable Transports
Tor 0.4.x + obfs4proxy + snowflake + conjure. Dynamic feature module (`:dynamic-tor`) — скачивается по запросу (~50 МБ).

### E8 — Double-hop
Entry (РФ) → Exit (foreign) Xray outbound chain. UI для выбора пары.

### E9 — DNS + split-tunnel
DoH интегрирован во все движки. Per-app split-tunnel (ALL / BYPASS_LAN / ALLOWLIST / BLOCKLIST).

### E10 — Security hardening
Anti-debug (TracerPid), anti-frida (`/proc/self/maps`), anti-emulator, APK signature verify, R8 full, obfuscator-LLVM, string encryption (AES-128), manifest hardening (allowBackup=false, networkSecurityConfig).

### E11 — UI финал + диагностика + i18n
SettingsScreen (все опции). DiagnosticsScreen (тест 20 URL). Локализация RU + EN. Accessibility (TalkBack).

### E12 — Self-update
GitHub Releases API → Ed25519-verified APK download → PackageInstaller prompt.

### E13 — Финальное тестирование
Ручной чеклист на провайдерах РФ (МТС, МегаФон, Билайн, Ростелеком, Yota, T2). Soak-test 8 часов. Security audit (JADX + Frida + emulator + signature patch). iperf3 ≥100 Mbps.

### E14 — Release
GitHub Actions на tag push `v*.*.*`. Signed APK + SHA256 + GPG. F-Droid submission. Канал Telegram.

## Таймлайн

~8 недель для 1 full-time разработчика (~120 атомарных подзадач, каждая ≤1 SP).

## Статус фаз

| Фаза | Статус | Ветка |
|------|--------|-------|
| E0 | ✅ Done | `feat/e0-setup` |
| E1 | ✅ Done | `feat/e1-mvp-byedpi` |
| E2 | ✅ Done | `feat/e2-subscriptions` |
| E3 | ✅ Done (AAR + DI отложены до интеграции рантайма) | `feat/e3-xray` |
| E4 | ✅ Done (AAR + DI отложены до интеграции рантайма) | `feat/e4-hysteria2` |
| E5 | ✅ Done (AAR + DI отложены до интеграции рантайма) | `feat/e5-amneziawg` |
| E6 | ✅ Done (binary + DI отложены до интеграции рантайма) | `feat/e6-naive` |
| E7 | ✅ Done (PT-бинари + DI + PlayCore SplitInstall отложены) | `feat/e7-tor` |
| E8 | ✅ Done (UI выбора пары — в E11) | `feat/e8-double-hop` |
| E9 | В процессе | `feat/e9-dns-split-tunnel` |
| … | … | … |

## Nice-to-have (v2.0+)

- P2P-распространение конфигов (BitTorrent DHT)
- Shadowsocks-2022 + plugin
- Trojan + ShadowTLS v3
- iOS версия
- Десктоп (Windows / Linux / macOS)

## Contributing

См. `CONTRIBUTING.md`.
