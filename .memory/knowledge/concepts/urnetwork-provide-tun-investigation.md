---
title: "URnetwork Provide: IoLoop Requirement — Root Cause + Fix (2026-05-27)"
aliases: [provide-tun, relay-zero-bytes, provide-without-ioloop, dummy-ioloop]
tags: [urnetwork, relay, provide, architecture, fix]
sources:
  - "daily/2026-05-26.md"
  - "daily/2026-05-27.md"
created: 2026-05-26
updated: 2026-05-27
---
<!-- updated 2026-05-27: full upstream source analysis, dummy IoLoop fix committed -->

# URnetwork Provide: требует IoLoop на Android

## Проблема
Relay coordinator работает с 2026-05-17 (commit 194d7701). `unpaidBytes=0` за 10+ дней. Приложение 24/7, модуль WARP, provide настроен правильно.

## Root Cause Analysis (2026-05-27)

Прошёл ВЕСЬ стек от Kotlin до Go исходников:
- `urnetwork/sdk`: `device_local.go`, `device_local_provider.go`, `device_local_ioloop.go`, `network_space.go`
- `urnetwork/connect`: `transport.go`, `transfer_contract_manager.go`, `provider/main.go`
- `urnetwork/android`: `MainApplication.kt`, `MainService.kt`, `DeviceManager.kt`

### Go SDK: tunnelStarted НЕ гейтит provide напрямую

```go
// device_local.go
func (self *DeviceLocal) GetProvideEnabled() bool {
    return self.remoteUserNatProvider != nil  // только проверка объекта
}
// SetProvidePaused тоже не зависит от tunnelStarted
```

### CLI provider ДОКАЗЫВАЕТ: provide без TUN возможен

`provider/main.go` в `urnetwork/connect`:
```go
// БЕЗ TUN, БЕЗ IoLoop, БЕЗ root:
localUserNat := connect.NewLocalUserNat(ctx, id, settings)
remoteUserNatProvider := connect.NewRemoteUserNatProvider(client, localUserNat, settings)
client.ContractManager().SetProvideModesWithReturnTraffic(provideModes)
// PlatformTransport подключается к mesh через QUIC/H3 + WebSocket fallback
```

### НО upstream Android app ВСЕГДА создаёт IoLoop

`MainService.kt`:
```kotlin
// Даже в offline/provide-only режиме:
builder.addAllowedApplication("${packageName}.offline")  // пустой TUN
builder.setBlocking(false)                                // non-blocking fd
val ioLoop = Sdk.newIoLoop(deviceLocal, pfd.detachFd())   // IoLoop ВСЕГДА
device.tunnelStarted = true                                // ВСЕГДА true
```

`MainApplication.kt` → `updateVpnService()`:
```kotlin
if (provideEnabled || connectEnabled || !routeLocal) {
    startVpnService()  // стартует VPN даже для provide-only
}
```

**Upstream НИКОГДА не запускает provide без IoLoop.** Хотя Go SDK формально позволяет.

### Почему IoLoop нужен на практике

1. **`deviceLocal.receiveCallbacks`** — IoLoop регистрирует callback для записи пакетов. Без IoLoop callback list пуст → `deviceLocal.receive()` дропает все пакеты от `provider.LocalUserNat()`
2. **`tunnelStarted=true`** — хотя не гейтит provide напрямую, может влиять на внутренние оптимизации SDK (keep-alive, transport priorities)
3. **Go runtime netpoller** — non-blocking fd интегрируется с epoll через Go runtime, blocking fd блокирует OS-thread

## Fix: pipe-based dummy IoLoop (commit f275e88, b44baec)

```kotlin
// UrnetworkRelayCoordinator.kt — после bridge.start():
val pipe = ParcelFileDescriptor.createPipe()
// Set non-blocking (как upstream setBlocking(false)):
Os.fcntlInt(readEnd.fileDescriptor, F_SETFL, flags or O_NONBLOCK)
val rawFd = readEnd.detachFd()
bridge.attachTun(rawFd)  // → Sdk.newIoLoop(device, fd) → tunnelStarted=true
pipeWriteEndRef.set(writeEnd)  // keep alive, close on stop
```

Паттерн: Go горутина IoLoop паркуется на empty pipe через epoll (0 CPU). Provider transport работает независимо через QUIC/H3.

## Баги найденные и исправленные ранее (2026-05-17 — 2026-05-26)

1. ✅ `setProvideControlMode` не вызывался → SDK default
2. ✅ `setProvideNetworkMode` не вызывался → WiFi gate
3. ✅ `RelayNetworkMonitor` — `ConnectivityManager.NetworkCallback`
4. ✅ `RelayLockManager` — `WakeLock` + `WifiLock`
5. ✅ Retry при fail start (3 attempts: 5s, 30s, 90s)
6. ✅ **Dummy IoLoop** — pipe-based, non-blocking (этот fix)
7. ✅ `connectBestAvailable()` — вызывается после start
8. ✅ `provideSecretKeys` — listener persistence (commit cc9e3c67)
9. ✅ `addJwtRefreshListener` — stale JWT fix

## Сравнение upstream vs Ozero (после fix)

| Аспект | Upstream | Ozero | Совпадает |
|--------|----------|-------|-----------|
| `newDeviceLocalWithDefaults` params | (space, jwt, desc, spec, ver, id, false) | Идентичные | ✅ |
| `applyDeviceFields` порядок | providePaused→routeLocal→provideMode→...→performanceProfile | Идентичный | ✅ |
| provideMode при ALWAYS | `ProvideModePublic` | `ProvideModePublic` | ✅ |
| IoLoop | Dummy TUN + IoLoop | Pipe + IoLoop | ✅ |
| `tunnelStarted` | `true` | `true` | ✅ |
| fd mode | `setBlocking(false)` | `O_NONBLOCK` | ✅ |
| PlatformTransport | `wss://connect.ur.network` auto-start, 5s retry | Тот же SDK | ✅ |
| NetworkSpace | `ur.network` / `main` | `ur.network` / `main` | ✅ |

## Что остаётся непроверенным

- **Transport через WARP**: QUIC/H3 + WebSocket идут через WARP tunnel. Должно работать, но нужен device test с `relayDiagnostics()` логами
- **Mesh demand**: если URnetwork mesh не маршрутизирует трафик через наш provider → 0 bytes при идеальной настройке
- **PlatformTransport settings**: `HttpConnectTimeout=15s`, `QuicHandshakeTimeout=15s`, `PingTimeout=5s`, `ReconnectTimeout=5s` — все дефолты SDK
