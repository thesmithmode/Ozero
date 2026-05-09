# Архитектура Ozero

Высокоуровневая карта модулей, DI-графа, runtime-потока. Детали native-сборки — `binaries-pipeline.md`. VPN flow — `runtime-flow.md`. Engines — `engines.md`.

---

## 1. Слои

```
┌────────────────────────────────────────────────────────────────────┐
│  UI (Compose)         MainActivity / MainScreen / MainViewModel    │
│                       NotificationPermissionGuard                  │
├────────────────────────────────────────────────────────────────────┤
│  VPN service          OzeroVpnService  (@AndroidEntryPoint)        │
│                       TunnelController (StateFlow)                 │
│                       HevSocksTunnel JNI ↔ hev-socks5-tunnel       │
├────────────────────────────────────────────────────────────────────┤
│  Pipeline             VpnEnginePipeline                            │
│                       (probe → engine.start → tunnel.start → FSM)  │
├────────────────────────────────────────────────────────────────────┤
│  Orchestrator         Orchestrator (FSM 13 переходов)              │
│                       StrategyEngine (probe parallel, pickBest)    │
├────────────────────────────────────────────────────────────────────┤
│  Engines              ByeDpiEngine / XrayEngine / AwgEngine /      │
│                       Hy2Engine / NaiveEngine / TorEngine          │
│                       (общий интерфейс ru.ozero.coreapi.Engine)    │
├────────────────────────────────────────────────────────────────────┤
│  Native delegates     LibXxxDelegate interface →                   │
│                       JNI / process-spawn / AAR-bind               │
├────────────────────────────────────────────────────────────────────┤
│  DI                   Hilt @InstallIn(SingletonComponent)          │
│                       EngineModule (multibinding @IntoMap)         │
│                       OrchestratorModule + VpnPipelineModule +     │
│                       TorModule (DynamicTorInstaller)              │
└────────────────────────────────────────────────────────────────────┘
```

## 2. Gradle модули

Полный список — см. `settings.gradle.kts`. По ролям:

**core/common (без Android UI):**
- `core-api` — `Engine`, `EngineId`, `EngineConfig`, `StartResult`, `ProbeResult`, `EngineCapabilities`, `EngineStats`
- `core-orchestrator` — `Orchestrator` (FSM), `StrategyEngine`, `Candidate`, `CandidateSource`, `HealthMonitor`, `Socks5HandshakeProbe`
- `core-storage` — Room schema подписок
- `core-subscriptions` — `servers.json` + Ed25519 подпись (engine-агностично)
- `common-vpn` — `OzeroVpnService`, `TunnelController`, `HevSocksTunnel`, `VpnEnginePipeline`, split-tunnel
- `common-crypto` — Ed25519 verifier для подписок и self-update
- `common-dns` — DoH client скелет
- `common-json` — типобезопасный JSON writer (anti-injection для нативных конфигов)

**engines (один на каждый протокол):**
- `engine-byedpi` — нативный CMake/JNI (`libbyedpi-<abi>.so`)
- `engine-xray` — gomobile bind AAR (`libxray.aar`)
- `engine-amnezia` — AmneziaWG 2.0 AAR (`libamneziawg.aar`)
- `engine-hysteria2` — Hysteria2 AAR (`libhysteria2.aar`)
- `engine-naive` — NaiveProxy CLI binary, извлечён из upstream APK
- `engine-tor` — `libtor.so` + `libiptproxy.so` (lyrebird+snowflake+dnstt) из Maven Central AAR

**app + dynamic features:**
- `app` — entry point, DI граф, MainActivity + Compose UI, self-update
- Tor поставляется статически в релизных APK/AAB без PlayCore SplitInstall.
- `security` — anti-debug / anti-frida / signature-check
- `buildSrc` — Gradle convention plugins (`ozero.android.application`, `ozero.android.library`, `ozero.dynamic.feature`, `ozero.binaries`)

## 3. DI-граф (Hilt SingletonComponent)

```
EngineModule          @IntoMap @EngineKey(EngineId.X)
                      → Map<EngineId, Engine>  (6 engines)
                      ↑
                      Stub*Delegates (JNI заменит в RT.6)

OrchestratorModule    Orchestrator + StrategyEngine
                      ← Map<EngineId, @JvmSuppressWildcards Engine>

VpnPipelineModule     TunnelController + HevTunnelGateway
                      → VpnEnginePipeline
                      ← все вышеперечисленное

@HiltAndroidApp       OzeroApp
@AndroidEntryPoint    OzeroVpnService → @Inject VpnEnginePipeline
                      MainActivity → @Inject MainViewModel
```

`@JvmSuppressWildcards` обязателен на `Map<EngineId, Engine>` — иначе Kotlin генерирует `Map<EngineId, ? extends Engine>` и Hilt не разрешает мульти-биндинг.

## 4. Engine-контракт

```kotlin
interface Engine {
    val id: EngineId
    val capabilities: EngineCapabilities
    suspend fun start(config: EngineConfig): StartResult
    suspend fun stop()
    suspend fun probe(): ProbeResult
    fun stats(): Flow<EngineStats>
}
```

`EngineConfig` — sealed class с вариантом на каждый engine (`ByeDpi`, `Xray`, `Hysteria2`, `Amnezia`, `Tor`, `Naive`). Каждый engine require'ит свой вариант (исключение — иначе StartResult.Failure).

## 5. Расширяемость

Добавить новый engine = 5 шагов:
1. `EngineId.NEW_ENGINE` в `core-api`
2. `EngineConfig.NewEngine(...)` (sealed)
3. Новый Gradle-модуль `engine-newone` с `Engine` реализацией
4. `@Provides @IntoMap @EngineKey(NEW_ENGINE)` в `EngineModule`
5. `EngineModuleTest` рефлексией убедится что покрытие полное (тест упадёт без п.4)

Никакого touching `OrchestratorModule` или `VpnPipelineModule`.

## 6. IP-resolution контракт (engine-agnostic)

`MainViewModel` показывает текущий exit-IP в UI. Способ узнать IP физически разный per-engine — но интерфейс ОДИН: `EnginePlugin.ipProbeRoute(socksPort): IpProbeRoute`.

```kotlin
sealed class IpProbeRoute {
    data object Default : IpProbeRoute()
    data class Socks(host: String, port: Int) : IpProbeRoute()
    data class StaticLocation(country: String?, countryCode: String?) : IpProbeRoute()
    data class Unavailable(reason: String) : IpProbeRoute()
}
```

Per-engine реализация:
- `ByeDpiEngine` → `Socks("127.0.0.1", port)`. SOCKS proxy, app-сокет надо явно роутить через прокси, иначе пинг идёт мимо.
- `EngineUrnetwork` → `StaticLocation` через `sdkBridge.selectedLocation()` (country+countryCode). Go SDK требует excludeSelf из своего же TUN (иначе routing loop, нет коннекта, sentinel защищает) → self-fetch уходит мимо TUN → real IP, не VPN. SDK сам отдаёт страну peer'а.
- `EngineWarp` → не overrides → `Default` → обычный `IpInfoProvider.fetch()` через TUN (WG socket protect-ается, self-traffic роутится через TUN).

`MainViewModel.resolveOnce(engineId, socksPort)` находит plugin в `Set<EnginePlugin>`, вызывает `ipProbeRoute`, по типу route делает один из трёх действий: `fetch()` / `fetchVia(host,port)` / собирает IpInfo из StaticLocation. App не знает engine-specific деталей.

Расширяемость: новый engine — override `ipProbeRoute` если нужно специфичное поведение, иначе Default. Никаких switch/if по EngineId в `app/`.
