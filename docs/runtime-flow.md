# Runtime flow Ozero

Что происходит между нажатием Connect и установлением туннеля. Слои — `architecture.md`.

---

## 1. Connect

```
User taps "Connect"
        ↓
MainActivity.onConnectClick()
        ↓
VpnService.prepare(context)
        ↓ если null (permission уже выдан)
        │   ↓
        │   viewModel.onConnectClick() → orchestrator.dispatch(Connect)
        │   startVpnService(ACTION_START)
        │
        └─ если Intent (нужен permission)
            ↓
            vpnPermissionLauncher.launch(intent)
                ↓ user принял в system-dialog
                ↓
                viewModel.onConnectClick()
                viewModel.onVpnPermissionGranted()
                startVpnService(ACTION_START)
```

`VpnService.prepare` возвращает `Intent` если разрешение ещё не выдано — UI рисует system-dialog. Orchestrator переходит в `Probing` ТОЛЬКО после OK от диалога — иначе race: probe бежит параллельно с user-decision.

Запрос POST_NOTIFICATIONS (Android 13+) делает `NotificationPermissionGuard` в Compose отдельно, до Connect.

## 2. OzeroVpnService.startVpn

```
onStartCommand(ACTION_START)
        ↓
startForeground(notification)         ← обязательно ПЕРВЫМ
        │                              (Android 8+: 5-сек deadline,
        │                               pipeline.start превышает)
        ↓
buildTunBuilder().establish()         ← TUN fd получен
        ↓
serviceScope.launch {
    pipeline.start(tunFd = fd.fd)
        ↓
}
```

`establish()` создаёт TUN device через `VpnService.Builder` — `addAddress(10.10.10.10/32)`, `addDnsServer(...)`, `setMetered(false)` (Q+). MTU не задаётся явно — Android берёт link-layer default (≈1500 на cellular). Без `prepare()`-permission `establish()` вернёт null — сервис останавливается.

`serviceScope` = `SupervisorJob() + Dispatchers.IO`. Cancel в `onDestroy` — pipeline.start не выполнится после смерти сервиса.

## 3. VpnEnginePipeline.start

```
pipeline.start(tunFd):
    1. orchestrator.dispatch(Connect)            → Probing
    2. candidates = strategy.buildCandidates()
       (sources: BYEDPI default + extraSources из подписки)
    3. winner = strategy.pickBest(candidates)
       (parallel probe top-3, выбирает первый Success)
    4. if null:
       dispatch(Disconnect, DisconnectComplete)  → Idle
       return Result.NoCandidates
    5. dispatch(ProbeComplete(winner.engineId))   → Connecting
    6. engine = engines[winner.engineId]
    7. engineResult = engine.start(winner.config)
        ├─ Failure → dispatch(ConnectFailed)     → Failed
        │           return Result.EngineFailed
        └─ Success(socksPort)
            ↓
            8. tunnelGateway.start(HevTunnelConfig(tunFd, "127.0.0.1", socksPort))
                ├─ code != 0 → engine.stop ROLLBACK + ConnectFailed → Failed
                │              return Result.TunnelFailed
                └─ code = 0
                    ↓
                    9. tunnelController.onEngineStarted(socksPort)
                    10. dispatch(ConnectSuccess)  → Connected
                    return Result.Connected(engineId, socksPort)
```

Pipeline — pure Kotlin координатор, не зависит от Android. Тестируется через `FakeHevGateway` без `System.loadLibrary`.

**Kill-switch**: если hev-tunnel не поднялся, engine **обязательно** откатывается (`runCatching { engine.stop() }`). Иначе engine продолжает слушать SOCKS-порт без TUN — трафик пользователя может обойти VPN.

## 4. hev-socks5-tunnel

`HevSocksTunnel.start(config)` — JNI вызов в `libhev-socks5-tunnel.so`. Принимает YAML с TUN fd + SOCKS upstream address/port, проксирует все TCP/UDP пакеты из TUN в SOCKS5.

`HevTunnelConfig.toYaml()` фильтрует адреса regex-ом `^[a-zA-Z0-9._:-]+$` — anti-injection (newline в host = YAML injection в нативный конфиг).

## 5. Engine.start (на примере ByeDpi)

```
ByeDpiEngine.start(config: EngineConfig.ByeDpi):
    args = listOf("-p", config.socksPort) + config.args.split()
    code = proxy.jniStartProxy(args)        ← JNI в libbyedpi-<abi>.so
    if code == 0:
        activeSocksPort = socksPort
        return Success(socksPort)
    else:
        return Failure("jniStartProxy код $code")
```

`activeSocksPort` устанавливается ТОЛЬКО после успешного JNI — иначе `probe()` может счесть engine живым до подтверждения старта.

Каждый engine имеет свой `Lib<X>Delegate` interface — реальный JNI / process-spawn / AAR-bind инжектируется через DI. Сейчас в проде Stub-делегаты (RT.6 заменит).

## 6. Probing

`StrategyEngine.pickBest`:
- Берёт первые `parallelProbeCount=3` кандидата по приоритету
- Параллельно `engine.probe()` для каждого
- Возвращает первый по **приоритету** (а не latency) с `ProbeResult.Success`

То есть высокоприоритетный (Hysteria2 native = 11) выигрывает у быстрого ByeDPI (priority=5), даже если ByeDPI ответил быстрее. Это сознательно — приоритет отражает quality of service.

`probe()` каждого engine разный:
- ByeDpi / Xray / Hy2 / Naive: `Socks5HandshakeProbe` к локальному SOCKS
- AmneziaWG: `delegate.isUp()` (нет SOCKS, tun-режим)
- Tor: TCP-connect к SocksPort + `delegate.isBootstrapped()`

## 7. Disconnect

```
User taps "Disconnect" / Quick Tile / OS-revoke
        ↓
MainActivity → startVpnService(ACTION_STOP) (или OS вызов onRevoke)
        ↓
OzeroVpnService.stopVpn:
    serviceScope.launch { pipeline.stop() }   ← async, до tunFd.close
    tunFd.close()
    stopForeground + stopSelf
        ↓
pipeline.stop:
    tunnelGateway.stop()                      ← hev-tunnel освобождает TUN fd
    currentEngine?.stop()                     ← engine закрывает SOCKS
    tunnelController.reset()                  → TunnelState.Idle
    if not Idle: dispatch(Disconnect, DisconnectComplete) → Idle
```

`pipeline.stop` вызывается ДО `tunFd.close()` — hev-tunnel должен корректно освободить FD прежде чем kernel закроет `/dev/tun`. Иначе hev-tunnel JNI thread может попытаться писать в закрытый FD → SIGBUS / native crash.

## 8. Failure modes

| Что упало | FSM | UI |
|-----------|-----|-----|
| Все probe failed | `Idle` (pipeline возвращается) | "Не нашли рабочий путь" |
| engine.start fail | `Failed(engineId, reason)` | "Engine X не запустился" + retry button |
| hev-tunnel.start fail | `Failed` (engine откатили) | "Туннель не поднялся" + retry |
| Engine упал в runtime | `Dead(reason)` (kill-switch) — `TunnelController.onEngineDied` | "VPN мёртв, трафик заблокирован" |
| OS-revoke (другой VPN) | onRevoke → stopVpn → `Idle` | "Другой VPN захватил" |

## 9. State Flows

`TunnelState` (`common-vpn`): Idle / Connected(socksPort) / Disconnecting / Dead(reason).
`OrchestratorState` (`core-orchestrator`): Idle / Probing / Connecting(engineId) / Connected(engineId, socksPort) / Switching / Failed / Disconnecting.

Compose UI подписан на оба через `collectAsState` — UI atomic update. ViewModel прокладка чтобы Compose не зависел напрямую от Orchestrator.
