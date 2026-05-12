# Архитектура Ozero

Карта модулей, контракт движков, chain orchestration. Native-сборка — `binaries-pipeline.md`. VPN flow — `runtime-flow.md`. Engine параметры — `engines.md`.

---

## 1. Слои

```
┌────────────────────────────────────────────────────────────┐
│  UI (Compose)       MainActivity / MainScreen / MainViewModel│
├────────────────────────────────────────────────────────────┤
│  VPN service        OzeroVpnService (@AndroidEntryPoint)   │
│                     TunnelController (StateFlow)            │
│                     HevTunnelGateway JNI ↔ hev-socks5-tunnel│
├────────────────────────────────────────────────────────────┤
│  Chain              ChainOrchestrator (последовательный     │
│                     старт engines, rollback при отказе)     │
│                     ManualEngineConfigBuilder (chain of 1)  │
├────────────────────────────────────────────────────────────┤
│  Engines            ByeDpiEngine / EngineWarp /             │
│                     EngineUrnetwork                         │
│                     (единый интерфейс EnginePlugin)         │
├────────────────────────────────────────────────────────────┤
│  DI                 Hilt @IntoSet Set<EnginePlugin>         │
│                     @InstallIn(SingletonComponent)          │
└────────────────────────────────────────────────────────────┘
```

## 2. Gradle-модули

**core / common:**

| Модуль | Ответственность |
|--------|----------------|
| `:engines-core` | `EnginePlugin` интерфейс, `ChainOrchestrator`, `IpProbeRoute`, `SettingsRepository`, `SettingsModel`, `Socks5HandshakeProbe`, `Upstream`, `TunFdAcceptor`, `TunSpec`, `EnginePreflight`, `PersistentLoggers` |
| `:common-vpn` | `OzeroVpnService`, `HevTunnelGateway` (JNI→libhev), `TunnelController`, `TunnelState`, `HealthMonitor`, `ManualEngineConfigBuilder`, `hev.TProxyService`, split-tunnel, stats |
| `:common-dns` | DoH client |
| `:common-net` | HTTP utilities |
| `:common-crypto` | Ed25519 verifier (подписки, self-update) |
| `:core-storage` | Room schema: серверы, connection logs |
| `:core-backup` | `AppBackupManager` |
| `buildSrc` | Gradle convention plugins |

**engines:**

| Модуль | Native | Статус |
|--------|--------|--------|
| `:engine-byedpi` | `libbyedpi-<abi>.so` (CMake+NDK) | ✅ |
| `:engine-warp` | AmneziaWG AAR (Go+gomobile) | ✅ |
| `:engine-urnetwork` | URnetwork Go AAR (gomobile) | ✅ |

**app:**

| Модуль | Ответственность |
|--------|----------------|
| `:app` | Hilt host, Compose UI, DI modules, MainActivity, self-update |

## 3. DI-граф (Hilt SingletonComponent)

```
EngineModule        @Provides @IntoSet EnginePlugin
                    → Set<@JvmSuppressWildcards EnginePlugin>
                      (ByeDpiEngine, EngineWarp, EngineUrnetwork)

ChainOrchestrator   ← Set<EnginePlugin>

VpnPipelineModule   HevTunnelGateway + TunnelController
                    + ChainOrchestrator → OzeroVpnService

@HiltAndroidApp     OzeroApp
@AndroidEntryPoint  OzeroVpnService → @Inject ChainOrchestrator
                    MainActivity → @Inject MainViewModel
```

## 4. EnginePlugin контракт

```kotlin
interface EnginePlugin {
    val id: EngineId
    val capabilities: EngineCapabilities
    suspend fun start(config: EngineConfig, upstream: Upstream = Upstream.None): StartResult
    suspend fun stop()
    suspend fun probe(): ProbeResult
    fun stats(): Flow<EngineStats>
    suspend fun tunSpec(): TunSpec? = null
    fun preflight(): EnginePreflight? = null
    suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute = IpProbeRoute.Default
}
```

`EngineCapabilities.supportsUpstreamSocks` — движок принимает upstream только если `true`. `ChainOrchestrator` проверяет перед стартом; terminal-only движок (ByeDPI, WARP) может быть только первым или standalone.

## 5. ChainOrchestrator

Последовательный старт engines; каждый шаг получает `Upstream.Socks5(host, port)` от предыдущего:

```
steps = [EngineA, EngineB]

EngineA.start(configA, Upstream.None) → Success(port=10800)
EngineB.start(configB, Upstream.Socks5("127.0.0.1", 10800)) → Success(port=10801)
→ ChainResult.Success(finalSocksPort=10801)
```

При отказе любого шага: rollback всех ранее запущенных в обратном порядке.

Single-engine = chain из одного шага.

## 6. Расширяемость

Добавить новый engine:
1. `EngineId.NEW` в `engines-core/EngineId.kt`
2. `EngineConfig.New(...)` (sealed) в `engines-core/EngineConfig.kt`
3. Новый Gradle-модуль `engine-new` с `EnginePlugin`-реализацией
4. `@Provides @IntoSet` в `app/di/EngineModule`

Никакого касания `ChainOrchestrator`, `TunnelController`, `OzeroVpnService`.

## 7. IP-resolution контракт

`MainViewModel` показывает текущий exit-IP. Способ резолва разный per-engine, но интерфейс один — `EnginePlugin.ipProbeRoute(socksPort): IpProbeRoute`.

```kotlin
sealed class IpProbeRoute {
    data object Default : IpProbeRoute()
    data class Socks(val host: String, val port: Int) : IpProbeRoute()
    data class StaticLocation(val country: String?, val countryCode: String?) : IpProbeRoute()
    data class Unavailable(val reason: String) : IpProbeRoute()
}
```

Per-engine:
- `ByeDpiEngine` → `Socks("127.0.0.1", port)` — self-трафик роутится через прокси явно
- `EngineUrnetwork` → `StaticLocation` через SDK (Go SDK excludeSelf → self-fetch мимо TUN → real IP)
- `EngineWarp` → `Default` → `IpInfoProvider.fetch()` через TUN (WG socket protect()-ается)

`MainViewModel.resolveOnce` — switch по типу route, без знания EngineId.
