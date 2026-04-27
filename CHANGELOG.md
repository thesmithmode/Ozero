# Changelog

Формат основан на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), версионирование — [SemVer](https://semver.org/).

## [Unreleased]

---

## [1.0.0] — 2026-04-27

Первый публичный релиз. Server-less архитектура: 7 транспортов, double-hop, Ed25519 self-update, hardening.

### Added
- E0 setup: GPLv3, 14 Gradle модулей, jacoco 90% gate, ktlint+detekt+pre-commit, CI
- E1+E2 MVP: ByeDPI VPN через Orchestrator, подписки OkHttp+Ed25519, DoH resolver
- E3 Xray: XrayEngine, XrayConfigBuilder, StrategyEngine, SocksProber, DnsResolverChain
- E4 Hysteria2 + port hopping: PortHopper (HMAC-SHA256), CgnatDetector, Hy2CandidateSource
- E5 AmneziaWG 2.0: AwgConfigBuilder (Jc/Jmin/Jmax/S1-S2/H1-H4), awg:// URI парсер
- E6 NaiveProxy: naive+https/quic URI, NaiveEngine + LibNaiveExec
- E7 Tor + Pluggable Transports: obfs4/snowflake/webtunnel/meek_lite/conjure, dynamic-tor split
- E8 Double-hop: XrayConfigBuilder.buildChain (entry→exit), ServerEntity.pairId (db v2)
- E9 DNS + split-tunnel: SplitTunnelMode ALL/BYPASS_LAN/ALLOWLIST/BLOCKLIST, LanRoutes
- E11 i18n RU+EN (47 строк), DiagnosticsTester (OkHttp HEAD via SOCKS, parallel 5)
- E12 Self-update: GithubReleaseFetcher, ApkUpdateVerifier (Ed25519), pinning api.github.com
- E15 URnetwork P2P engine (squash merge feat/e15-urnetwork)
- E16 server-less архитектура (PLAN v4): bootstrap snapshot 50 URI + harvest tool
- RT.2 Hilt DI граф для 6 engines (multibinding @IntoMap)
- RT.3 VpnEnginePipeline координатор VPN→Engine→tunnel + wiring в OzeroVpnService
- RT.4 UI Compose: Settings, Diagnostics, Servers (double-hop), SplitTunnel + ViewModels
- RT.5 PlayCore SplitInstall для :dynamic_tor + verify SHA-256 после установки
- RT.6 Update flow: PackageInstallerLauncher (Receiver + bus) в SettingsScreen
- RT.1.7 binary artifact pipeline для всех engines (byedpi/xray/naive/awg/hy2/tor+IPtProxy)
- E13/E14 release pipeline: signing через env, signed APK + SHA256 + GPG, F-Droid metadata
- E13 automation: iperf3, RU probe, soak tests, security audit
- Release keystore + GPG signing infrastructure (Этап 3)
- Ed25519 self-update keypair + PEM loader (Этап 2)
- SecurityWatchdog — периодическая re-проверка SecurityGuard
- common-json модуль вместо 3 копий JsonWriter

### Changed
- xray из upstream prebuilt (2dust/AndroidLibXrayLite v26.4.25) вместо локальной сборки
- shrinkResources + release checklist (RT.7.3-RT.12.1)
- Manifest hardening + SilentPackageInstaller

### Fixed
- VPN/DNS leaks: IPv6 в TUN, AAAA queries, kill-switch race
- Tor hardening: ControlPort 127.0.0.1, dataDir required, Unknown bridge handling
- Self-update hardening: Mutex, downgrade protection, https-only
- Subscriptions hardening: body limit, JSONArray parser, Semaphore
- Orchestrator races: HealthMonitor.stop suspend+cancelAndJoin
- Engine lifecycle: runCatching stop, lazy loadLibrary
- Production hardening: security, lifecycle, FSM, simplify (review-driven)
- VPN starting reset, sanitize, streaming (review hardening)
- URnetwork safety: sanitize Failure reason, hard-locked consumer
- App glue: HarvestWorker backoff, CrashLogStore rotation+sort, BootReceiver goAsync
- App/UI: rememberSaveable, MainScreen i18n + a11y, NotificationPermission guard
- core-api jacoco coverage 0.82 → ≥0.90 (Hysteria2/Amnezia/Tor)
- CI gradle --continue — все ошибки за один прогон
- probe SOCKS5 handshake fix
- Множество ktlint/detekt cleanup-фиксов

### Security
- E10 hardening: AntiDebugCheck (TracerPid), AntiFridaCheck (/proc/self/maps), AntiEmulatorCheck
- ApkSignatureVerifier, SecurityGuard, network_security_config, R8 full + Log strip
- Security leaks: SecurityException без причин, Build.TYPE strict
- Crypto domain separation + RuntimeException catch
- API contracts: JWT mask in toString, ProbeResult.cause
- Build config: failBuildOnCVSS=7, NewApi enabled, release-fail-when-error
- Tools secrets: umask 077, keytool env passwords, no source for keys
- CI supply chain: release perms, security-audit gate, soak inputs
- Сформулирована STRIDE threat model и политика responsible disclosure

### Removed
- build_xray.sh (заменён upstream prebuilt AAR)

---

## Формат будущих записей

```
## [1.0.0] — 2026-XX-XX
### Added
- Первый релиз MVP: ByeDPI-обход, одна кнопка, kill-switch, ...

### Fixed
- ...

### Security
- CVE-YYYY-NNNNN — описание
```

Ссылки на diff (добавить после первого релиза):
<!-- [Unreleased]: https://github.com/thesmithmode/ozero/compare/v1.0.0...HEAD -->
