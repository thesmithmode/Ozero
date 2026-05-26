---
title: "URnetwork Provide: TUN Requirement Investigation (2026-05-26)"
aliases: [provide-tun, relay-zero-bytes, provide-without-ioloop]
tags: [urnetwork, relay, provide, investigation, architecture]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# URnetwork Provide: требует ли TUN?

## Проблема
Relay coordinator работает с 2026-05-17 (commit 194d7701). `unpaidBytes=0` за все недели. Relay стартует device, регистрирует wallet, но трафик не раздаётся.

## Найденные баги в relay coordinator
1. НЕ вызывал `setProvideControlMode` после start → SDK default, не user setting
2. НЕ вызывал `setProvideNetworkMode` после start → WiFi gate не применялся
3. НЕТ `ConnectivityManager.NetworkCallback` для динамического WiFi/mobile gate
4. НЕТ `WakeLock`/`WifiLock` → CPU/WiFi засыпает → provide прерывается
5. НЕТ retry при fail start
6. Никогда не зовёт `attachTun()` → нет IoLoop

## Upstream architecture (`.claude/Контекст/android/`)
- `MainApplication.updateVpnService()`: `provideEnabled=true` → VPN service start
- `MainService.updatePfd()`: `Sdk.newIoLoop(device, fd)` → IoLoop
- `addNetworkCallback()`: WiFi/mobile gate через `device.providePaused`
- `WakeLock` + `WifiLock` при active provide
- Offline mode: пустой TUN (`addAllowedApplication("pkg.offline")`), IoLoop создан, provide через Go raw sockets

## CLI provider (docs.ur.io/provider)
- Linux/Docker/Windows: `./provider provide` — БЕЗ TUN, БЕЗ root
- Go SDK provide через raw sockets, не через TUN
- Доказывает: provide I/O path не зависит от TUN

## Гипотезы (pending device test)
- **A:** provide через raw sockets работает без IoLoop на Android → fix ~50 строк
- **B:** Go SDK на Android требует IoLoop для init → нужен "пустой TUN" или base layer refactor
- **C:** `providePaused=true` застревает из-за missing NetworkCallback
- **D:** `connectBestAvailable()` не вызывался после `device.start()` → device создавался, но не подключался к mesh → provide не получал peers

## Диагностика
commit 48d0b350: `relayDiagnostics()` логирует `provideEnabled`, `providePaused`, `tunnelStarted`, `provideMode`, `connectEnabled`, `offline`, `unpaidBytes` под тегом `MeshSession`.

Фиксы 1-2 уже в том же коммите. Остальные (3-6) — после device test.
