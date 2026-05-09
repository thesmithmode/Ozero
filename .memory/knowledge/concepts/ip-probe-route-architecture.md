---
title: "IpProbeRoute — единый IP-resolution контракт"
aliases: [ip-probe-route, engine-ip-probe, ipprobeRoute]
tags: [architecture, vpn, engine, modularity]
sources:
  - "daily/2026-05-09.md"
created: 2026-05-09
updated: 2026-05-09
---

# IpProbeRoute — единый IP-resolution контракт

`MainViewModel` показывает текущий exit-IP в UI. Способ узнать IP физически разный per-engine (SOCKS proxy у ByeDPI, SDK getter у URnetwork, прямой fetch у WARP), но интерфейс ОДИН: `EnginePlugin.ipProbeRoute(socksPort): IpProbeRoute`. App не знает engine-specific деталей. Расширяемость: новый engine — override метода (или Default).

## Key Points

- `engines-core/IpProbeRoute.kt`: sealed class — Default | Socks(host, port) | StaticLocation(country, countryCode) | Unavailable(reason).
- Per-engine реализация инкапсулирована в самом engine, не в `app/`.
- `MainViewModel.resolveOnce(engineId, socksPort)` находит plugin в `Set<EnginePlugin>`, вызывает `ipProbeRoute`, switch по типу route — `fetch()` / `fetchVia(host,port)` / `IpInfo` из StaticLocation / Error из Unavailable.
- Низкая связанность сохранена: `app/` зависит только от `engines-core` контракта (плюс legacy зависимость `MainViewModel → UrnetworkSdkBridge` для peerCount — отдельный долг).

## Per-engine реализации

- **ByeDpiEngine** → `IpProbeRoute.Socks("127.0.0.1", port)`. SOCKS proxy на loopback — app-сокет надо явно роутить через прокси, иначе пинг идёт мимо.
- **EngineUrnetwork** → `IpProbeRoute.StaticLocation` через `sdkBridge.selectedLocation()`. Go SDK обязан excludeSelf из своего TUN ([[concepts/tun-self-exclusion-sdk-engines]]) → self HTTP уходит мимо TUN → real IP, не VPN. SDK сам отдаёт страну peer'а через `ConnectLocation.country/countryCode`.
- **EngineWarp** → не overrides → `Default` → `IpInfoProvider.fetch()` через TUN. WG socket protect()-ается, self-traffic роутится через TUN автоматически. Sentinel `MainViewModelIpInfoChannelTest::fetchOnce_не_использует_fetchViaSocketFactory` запрещает `bindSocketToNetwork` (EPERM на VPN-network).

## Почему "просто пинг" не работает универсально

Юзер спросил "почему нельзя просто пингануть сайт и получить IP?". Ответ — реализация физически разная:

- BYEDPI = SOCKS proxy, не TUN. Self-traffic не идёт через прокси автоматически. Нужен явный routing через SOCKS — иначе пинг покажет реальный IP пользователя.
- URnetwork = TUN-based, но Go SDK не имеет protect-callback для своих сокетов → routing loop если не excludeSelf. Поэтому self HTTP идёт мимо TUN.
- WARP = TUN-based + protect()-ает свой WG socket → self-traffic роутится через TUN корректно. Здесь "просто пинг" работает.

Контракт `IpProbeRoute` инкапсулирует эту разницу. `app/` спрашивает "куда пинговать" вместо "какой IP".

## Anti-pattern (избежали)

В предыдущей итерации был костыль `IpInfoState.Unsupported(engineId)` — `MainViewModel` switch'ился по EngineId, для full-TUN движков показывал "проверь IP в браузере". Юзер критика: "костыль а не решение", "у нас МОДУЛЬНАЯ система с низкой связанностью", "метод определения IP нужен ЕДИНЫЙ для ВСЕХ модулей". Решение: убрать switch из app, перенести знание в engine plugin через единый интерфейс.

## Related Concepts

- [[concepts/tun-self-exclusion-sdk-engines]] — почему URnetwork обязан excludeSelf
- [[concepts/urnetwork-sdk-integration]] — `selectedLocation`/`ConnectLocation` API
- [[concepts/vpn-ip-detection-contract]] — предыдущая итерация архитектурного fix

## Sources

- [[daily/2026-05-09.md]] — рефакторинг костыля Unsupported в IpProbeRoute по требованию юзера о модульности
