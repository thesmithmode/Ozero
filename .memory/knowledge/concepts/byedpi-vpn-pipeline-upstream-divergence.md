---
title: "ByeDPI VPN Pipeline — Upstream Divergence Map (полный)"
aliases: [byedpi-pipeline-divergence, byedpi-builder-diff, byedpi-tun-divergence]
tags: [byedpi, hev, vpn, builder, qfic, udp, architecture, gotcha]
sources:
  - "daily/2026-05-20.md"
created: 2026-05-20
updated: 2026-05-20
---

# ByeDPI VPN Pipeline — Upstream Divergence Map

Полный аудит расхождений нашего ByeDPI pipeline (Ozero) vs upstream ByeByeDPI 1.7.5 reference. Контекст: user runtime evidence доказал что **identical byedpi strategy args дают разный результат — upstream YouTube+Insta OK, наш только Insta**. Корень — UDP/QUIC pipeline, не args.

Это **продолжение** memory `byedpi-hev-pipeline-upstream-parity` (2026-05-19). Тот аудит был scoped ТОЛЬКО на YAML — это покрытие подтверждено байт-в-байт parity. Но pipeline разрезается на **6+ слоёв** помимо YAML: VPN Builder, init order, native binary, stats poller, health monitor, killswitch infrastructure. Этот документ — **полная карта** всех слоёв.

## Verdict (что виновато / что нет)

### TOP SUSPECTS (UDP/QUIC failure)

1. **`setUnderlyingNetworks(null)` ВСЕГДА** — `TunBuilderHelper.kt:83-93` `applyLockdown`. Upstream НИКОГДА не вызывает. Единственный явный addition в TUN Builder. Mechanism: Android при `null` не tracks underlying network → outgoing UDP socket в byedpi process может потерять корректный routing context → QUIC packets dropped. TCP менее affected (kernel cache route per-socket для long-lived connections). **Fix scoped per-engine**: ByeDPI override `applyUnderlying=false`, WARP/URnetwork сохраняют `true` (killswitch invariant P37 actual для них).

2. **Init order reversal** — `StartSequenceCoordinator.kt:103-119`. У нас TUN fd open ДО byedpi ready (~5+ секунд через preflight + chain start + awaitReady). У upstream `ByeDpiVpnService.kt:120-123` — обратное (startProxy → startTun2Socks). Drop window: TUN принимает packets, hev/byedpi не готовы → drops. QUIC чувствительнее TCP (короче handshake). **Secondary fix** если #1 не решит.

3. **`TProxyGetStats()` polling 5s** — `HevTunnelGateway.kt:77-123` `startStatsPoller()`. Upstream **никогда** не дёргает stats — `TProxyGetStats` объявлен, не вызывается. Lock contention в hev internal mutex теоретически возможна. Hev 2.7.0 использует атомики для stats, но при contention один UDP packet может тормозиться. **Tertiary fix**.

### ИСКЛЮЧЕНО

- **HEV YAML** — байт-в-байт parity (mtu=8500, task-stack-size=81920, udp: udp unquoted). Подтверждено memory `byedpi-hev-pipeline-upstream-parity`.
- **IPv6 blackhole** — для byedpi pipeline (через `buildTunBuilder`) НЕТ unconditional blackhole. `blackholeIpv6()` function определена, но вызывается только из `applyEngineTunSpec` (WARP/URnetwork). Memory fix 2026-05-19 применён правильно.
- **MTU mismatch** — оба не вызывают `Builder.setMtu` (default 1500). Hev YAML mtu=8500. Parity.
- **excludeSelf / addDisallowedApplication** — оба исключают self из TUN. Parity.
- **DNS count** — оба 1 DNS через `.take(1)`. Parity.
- **Process isolation** — byedpi pipeline у нас в main process (как upstream). Parity. `:engine_warp` только для WARP (нерелевантно).
- **FOREGROUND_SERVICE_TYPE_SPECIAL_USE + manifest property** — оба используют. Parity.
- **byedpi/hev native binary version** — pin `ba532298` (паритет с ByeByeDPI 1.7.5). Production APK auto-downloads через `ozero.binaries.gradle.kts`. Не корень.
- **PKGNAME** — разный namespace by design (наш `hev`, upstream `io/github/romanvht/byedpi/core`). Не сравнимо binary-wise, но functionally OK.

## Полная diff карта (все слои)

### Layer 1: VpnService.Builder

| Параметр | Наш (TunBuilderHelper.buildTunBuilder) | Upstream (createBuilder) | DIFF | UDP impact |
|---|---|---|---|---|
| `addAddress(10.10.10.10, 32)` | да | да | parity | none |
| `addRoute(0.0.0.0, 0)` | через `TunBuilderConfigurator.SplitTunnelMode.ALL` | прямой вызов | parity | none |
| `addAddress(fd00::1, 128)` | только если `ipv6Enabled=true` | только если `ipv6=true` | parity | none |
| `addRoute(::, 0)` | только если `ipv6Enabled=true` | только если `ipv6=true` | parity | none |
| `addDnsServer(dns)` | один DNS (`.take(1)`) | один DNS (`dns_ip` pref) | parity | none |
| `setMtu(...)` | НЕТ | НЕТ | parity | none |
| `setBlocking(...)` | НЕТ | НЕТ | parity | none |
| `allowFamily(...)` | НЕТ | НЕТ | parity | none |
| `setMetered(false)` | API Q+ | API Q+ | parity | none |
| `setConfigureIntent(PendingIntent)` | НЕТ | ДА (MainActivity) | minor | none (UX only) |
| `addDisallowedApplication(packageName)` | через `excludeSelfFromTun()` (excludeSelf=true) | прямой вызов | parity | none |
| **`setUnderlyingNetworks(null)`** | **ДА (applyLockdown)** | **НЕТ** | **CRITICAL** | **high** |

### Layer 2: Init order

| Шаг | Наш (StartSequenceCoordinator) | Upstream (ByeDpiVpnService) |
|---|---|---|
| 1 | preflight | startProxy (jniStartProxy byedpi) |
| 2 | lockdownStartupFdRef.set (если killswitch) | startTun2Socks (establish + TProxyStartService) |
| 3 | pickEngine | — |
| 4 | establishTun (TUN fd создаётся) | — |
| 5 | startChain (jniStartProxy byedpi) | — |
| 6 | startNativeTunnel (hev TProxyStartService) | — |

**DIFF**: TUN fd у нас live от шага 4 до 6 (~5s). Upstream — TUN fd создаётся в одном sync flow с TProxyStartService (~ms gap).

### Layer 3: Background pollers

| Поллер | Наш | Upstream |
|---|---|---|
| `TProxyGetStats()` 5s | да (`HevTunnelGateway.startStatsPoller`) | НЕТ |
| `Socks5HandshakeProbe` 30s | да (`HealthMonitor`) | НЕТ |
| `EngineWatchdog peerWatchdog` 5s polls | да (только usesCustomTun) | НЕТ |
| `TunnelStatsLogger` | да | НЕТ |

### Layer 4: Native binary

| Параметр | Наш | Upstream |
|---|---|---|
| byedpi commit | `ba532298` (паритет с 1.7.5) | ту же или близкая (submodule pin в snapshot пустой, точно не verified) |
| hev version | tag `v2.7.0` | submodule (pin не verified) |
| PKGNAME | `hev` | `io/github/romanvht/byedpi/core` |
| ABI APK | arm64-v8a only | universal (4 ABI) |
| -O level | -O2 | -O3 |
| argv[0] prepend в native-lib.c | `"byedpi"` | НЕТ (передаёт Kotlin args 1:1) |
| CAS guard `g_proxy_running` | atomic_int | int |
| `emergencyReset` JNI | да | НЕТ |
| `forceClose` semantics | server_fd snapshot | без snapshot |

**Production APK auto-download** через `buildSrc/.../ozero.binaries.gradle.kts` task `downloadBinaries` (depends on `preBuild`) → из `binaries.lock.yaml` URL. Production контракт корректный.

### Layer 5: Killswitch infrastructure (НЕТ у upstream)

- `lockdownStartupFdRef` — preliminary TUN до pick engine при killswitch ON
- `EngineWatchdogCoordinator.startHealthKillswitchWatcher` + `startPeerWatchdog`
- `HealthMonitor` DEGRADED → `enterKillswitchMode`
- `chainOrchestrator.stop` при kill

При **killswitch OFF** эти пути не активны (lockdownStartupFdRef.set не вызывается). User может проверить отключив killswitch.

## Mechanism deep-dive: setUnderlyingNetworks(null) и QUIC

### Что делает API

`VpnService.setUnderlyingNetworks(networks: Array<Network>?)`:
- `null` (наш): "VPN не указывает underlying — Android system выбирает default"
- `arrayOf(...)`: VPN явно перечисляет какие networks использует
- Не вызвать вообще (upstream): Android tracks default network через NetworkCallback автоматически

### Что ломается у нас для QUIC

1. byedpi process открывает outgoing UDP socket для QUIC packet к youtube (через SOCKS5 UDP_ASSOCIATE).
2. socket должен иметь `VpnService.protect(fd)` — чтобы НЕ маршрутизироваться обратно через TUN.
3. Kernel при отправке UDP packet смотрит routing table для destination.
4. С `setUnderlyingNetworks(null)` — система не имеет authoritative underlying — может направить через wrong interface (или vice versa).
5. UDP socket → packets dropped или routed incorrectly → QUIC handshake fails.
6. TCP менее affected: kernel cache'ит established route per-socket.

### Почему upstream работает

Upstream НЕ вызывает `setUnderlyingNetworks` → Android default behavior: автоматическое tracking через NetworkCallback → underlying network известен → UDP routing работает.

### Почему мы добавили `null` (P37 incident)

WiFi→Mobile transition в killswitch режиме (WARP/URnetwork) ломал lockdown — TUN терял route. `setUnderlyingNetworks(null)` исправлял. Sentinel закрепил для ВСЕХ engines globally. **Это была over-correction** — реально нужно только для killswitch-aware engines, не для ByeDPI (которое работает по upstream parity без него).

## Per-engine fix (architectural)

```kotlin
// TunBuilderHelper.applyLockdown
private fun applyLockdown(builder: VpnService.Builder, callerTag: String, applyUnderlying: Boolean) {
    if (!applyUnderlying) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        runCatching { builder.setUnderlyingNetworks(null) }
            .onFailure { /* warn */ }
    }
}

// buildTunBuilder (ByeDPI) — upstream parity, no lockdown
applyLockdown(builder, "buildTunBuilder", applyUnderlying = false)

// applyEngineTunSpec (WARP/URnetwork) — killswitch invariant
applyLockdown(builder, "applyEngineTunSpec", applyUnderlying = true)
```

**Trade-off**: ByeDPI на WiFi→Mobile transition может потерять TUN → user reconnect. Это **upstream behavior**, приемлемо. WARP/URnetwork сохраняют lockdown.

## Sentinels (под per-engine semantics)

- `applyLockdown существует` — keep
- `applyLockdown вызывает setUnderlyingNetworks(null) ПРИ applyUnderlying=true` — update assertion
- `buildTunBuilder вызывает applyLockdown(..., applyUnderlying=false)` — NEW, ByeDPI parity sentinel
- `applyEngineTunSpec вызывает applyLockdown(..., applyUnderlying=true)` — NEW, killswitch sentinel
- `setUnderlyingNetworks вызывается ТОЛЬКО с null` (P37) — keep (вызов только в applyEngineTunSpec ветке)
- `OzeroVpnService не регистрирует NetworkCallback` (P37) — keep

## Verification protocol

1. После push устройство-тест:
   - ByeDPI engine, killswitch OFF
   - YouTube → должен работать в пределах 30s+
   - Insta → должен работать (regression check)
   - WiFi→Mobile transition → ByeDPI может разорваться (acceptable, upstream behavior)
2. Если YouTube заработал → root cause **confirmed** → close investigation
3. Если нет → escalate к hypothesis #2 (init order swap для byedpi) — отдельный PR
4. WARP/URnetwork отдельно тестировать на killswitch lockdown (P37 invariant)

## Related Concepts

- [[concepts/byedpi-hev-pipeline-upstream-parity]] — YAML parity (scope: только YAML, не Builder)
- [[concepts/tun-mtu-dual-layer]] — Builder MTU vs hev YAML MTU
- [[concepts/tun-self-exclusion-sdk-engines]] — excludeSelf semantics
- [[concepts/vpnservice-builder-traps]] — Builder API gotchas

## Sources

- daily/2026-05-20.md — user runtime evidence + agent investigation
- upstream: `.claude/Контекст/ByeByeDPI-v.1.7.5/app/src/main/java/io/github/romanvht/byedpi/services/ByeDpiVpnService.kt`
- наш: `common-vpn/src/main/java/ru/ozero/commonvpn/TunBuilderHelper.kt`, `HevTunnelGateway.kt`, `StartSequenceCoordinator.kt`
