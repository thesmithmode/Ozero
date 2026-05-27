---
title: "URnetwork Provide: data flow architecture"
aliases: [provide-data-flow, relay-traffic-flow, provide-transport]
tags: [urnetwork, architecture, provide, transport]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# URnetwork Provide: data flow

## Два независимых data path в DeviceLocal

### Client path (VPN routing)
```
User app → TUN → IoLoop.read() → device.SendPacket() → remoteUserNatClient → mesh → provider → internet
Internet → provider → mesh → remoteUserNatClient → device.receive() → receiveCallbacks → IoLoop.write() → TUN → user app
```
Требует TUN + IoLoop.

### Provider path (relay)
```
Mesh peer → PlatformTransport (QUIC/H3 or WS) → connect.Client → RemoteUserNatProvider → LocalUserNat → net.Dial() → internet
Internet → LocalUserNat → RemoteUserNatProvider → connect.Client → PlatformTransport → mesh peer
```
НЕ требует TUN. Работает через transport layer.

CLI provider (`urnetwork/connect/provider/main.go`) доказывает: provide path полностью независим от TUN.

## Provider инфраструктура (из Go SDK)

```go
// device_local.go → setProvideModeWithLock(ProvideModePublic):
remoteUserNatProviderLocalUserNat = connect.NewLocalUserNatWithDefaults(client.Ctx(), clientId)
remoteUserNatProvider = connect.NewRemoteUserNatProviderWithDefaults(client, localUserNat)
// ContractManager advertises provider to mesh:
client.ContractManager().SetProvideModesWithReturnTraffic(provideModes)
```

## PlatformTransport (из connect/transport.go)

- Стартует СРАЗУ при создании (`go HandleError(transport.run, cancel)`)
- Поддерживает H3 (QUIC/UDP) и H1 (WebSocket) с fallback
- Retry: `ReconnectTimeout = 5s`
- Таймауты: `HttpConnect=15s`, `QuicHandshake=15s`, `Ping=5s`, `Read=30s`
- Auth: `ByJwt` + `AppVersion` + `InstanceId` в заголовках (H1) или фрейме (H3)
- После подключения: `routeManager.UpdateTransport()` регистрирует send/receive каналы
- platformUrl: `wss://connect.ur.network` (derived from NetworkSpace host="ur.network" env="main")

## Почему upstream всё равно создаёт IoLoop для provide

1. `provider.LocalUserNat().AddReceivePacketCallback(deviceLocal.receive)` — bridge между provider NAT и device. Без IoLoop `receiveCallbacks` пуст
2. Android VPN service lifecycle — foreground priority для background provide
3. Unified architecture — один VPN service для connect + provide
4. `tunnelStarted=true` как сигнал что device полностью инициализирован

## ContractManager provide frame

```go
// transfer_contract_manager.go:
func (self *ContractManager) provideFrame() {
    // Если providePaused=true → только ProvideMode_Stream (return traffic)
    // Если providePaused=false → все modes из SetProvideModesWithReturnTraffic
    controlSyncProvide.Send(frame, nil, ackCallback)  // через transport
}
```

Mesh получает provideFrame → регистрирует нас как provider → маршрутизирует relay traffic.
