package ru.ozero.engineurnetwork

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicReference

class EngineUrnetwork(
    private val configStore: UrnetworkConfigStore,
    private val sdkBridge: UrnetworkSdkBridge,
    private val authService: UrnetworkAuthService,
    private val pluginScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val statsPollIntervalMs: Long = STATS_POLL_INTERVAL_MS,
) : EnginePlugin, TunFdAcceptor {

    private val statsJobRef = AtomicReference<Job?>(null)

    override val id = EngineId.URNETWORK

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = false,
    )

    private val _stats = MutableStateFlow(EngineStats())

    override fun stopTimeoutMs(): Long = URN_STOP_TIMEOUT_MS

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Urnetwork) { "EngineUrnetwork требует EngineConfig.Urnetwork" }
        require(upstream is Upstream.None) {
            "EngineUrnetwork не принимает upstream — supportsUpstreamSocks=false"
        }

        val byJwt = ensureGuestJwt() ?: return StartResult.Failure(
            reason = "URnetwork guest jwt acquire failed — нет интернета или сервер недоступен",
        )

        val byClientJwt = ensureClientJwt(byJwt) ?: return StartResult.Failure(
            reason = "URnetwork client jwt acquire failed — нет интернета или сервер недоступен",
        )

        val wallet = configStore.walletAddress().first()
        val isPreset = wallet == UrnetworkDefaults.PRESET_WALLET
        Log.i(
            TAG,
            "start wallet=${wallet.take(WALLET_LOG_PREFIX_LEN)}… isPreset=$isPreset hasClientJwt=true",
        )

        val bridgeResult = sdkBridge.start(
            walletAddress = wallet,
            apiUrl = UrnetworkDefaults.DEFAULT_API_URL,
            connectUrl = UrnetworkDefaults.DEFAULT_CONNECT_URL,
            byClientJwt = byClientJwt,
        )
        return when (bridgeResult) {
            UrnetworkSdkBridge.StartResult.Success -> {
                runCatching { sdkBridge.setPreferredCountry(config.region) }
                    .onFailure { PersistentLoggers.warn(TAG, "setPreferredCountry threw: ${it.message}") }
                val windowType = configStore.windowType().first()
                val fixedIp = configStore.fixedIpSize().first()
                runCatching { sdkBridge.applyPerformanceProfile(windowType, fixedIp) }
                    .onFailure { PersistentLoggers.warn(TAG, "applyPerformanceProfile threw: ${it.message}") }
                val provideEnabled = configStore.provideEnabled().first()
                runCatching { sdkBridge.setProvidePaused(!provideEnabled) }
                    .onFailure { PersistentLoggers.warn(TAG, "setProvidePaused threw: ${it.message}") }
                val controlMode = configStore.provideControlMode().first()
                runCatching { sdkBridge.setProvideControlMode(controlMode) }
                    .onFailure { PersistentLoggers.warn(TAG, "setProvideControlMode threw: ${it.message}") }
                val networkMode = configStore.provideNetworkMode().first()
                runCatching { sdkBridge.setProvideNetworkMode(networkMode) }
                    .onFailure { PersistentLoggers.warn(TAG, "setProvideNetworkMode threw: ${it.message}") }
                Log.i(
                    TAG,
                    "started OK preferredCountry=${config.region ?: "<auto>"} " +
                        "windowType=${windowType.rawValue} fixedIp=$fixedIp",
                )
                startStatsPolling()
                StartResult.Success(socksPort = 0)
            }
            is UrnetworkSdkBridge.StartResult.Failed -> {
                PersistentLoggers.error(TAG, "start failed: ${bridgeResult.reason}")
                StartResult.Failure(reason = bridgeResult.reason)
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        statsJobRef.getAndSet(null)?.cancel()
        _stats.value = EngineStats()
        sdkBridge.stop()
    }

    override suspend fun recover(): EnginePlugin.RecoverResult {
        if (!sdkBridge.isRunning()) {
            return EnginePlugin.RecoverResult.Failed("bridge not running")
        }
        val location = sdkBridge.selectedLocation()
        return runCatching {
            if (location != null) sdkBridge.connectTo(location) else sdkBridge.connectBestAvailable()
            Log.i(TAG, "recover: re-issued connect (location=${location?.countryCode ?: "<best>"})")
            EnginePlugin.RecoverResult.Success
        }.getOrElse { t ->
            PersistentLoggers.warn(TAG, "recover threw: ${t.message}")
            EnginePlugin.RecoverResult.Failed("recover: ${t.message}")
        }
    }

    private fun startStatsPolling() {
        statsJobRef.getAndSet(null)?.cancel()
        val sessionStart = System.currentTimeMillis()
        val job = pluginScope.launch {
            while (true) {
                val peers = runCatching { sdkBridge.peerCount() }.getOrDefault(0)
                _stats.value = EngineStats(
                    activeConnections = peers,
                    connectedSince = sessionStart,
                )
                delay(statsPollIntervalMs)
            }
        }
        statsJobRef.set(job)
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure(reason = "URnetwork не предоставляет SOCKS-интерфейс")

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override fun preflight(): ru.ozero.enginescore.EnginePreflight = UrnetworkPreflight()

    override suspend fun ipProbeRoute(socksPort: Int): ru.ozero.enginescore.IpProbeRoute {
        val info = sdkBridge.selectedLocationInfo()
            ?: return ru.ozero.enginescore.IpProbeRoute.Unavailable("URnetwork location pending")
        val country = info.country ?: info.name
        return ru.ozero.enginescore.IpProbeRoute.StaticLocation(country, info.countryCode)
    }

    override suspend fun tunSpec(): TunSpec = TunSpec(
        sessionName = "URnetwork",
        mtu = TUN_MTU,
        blocking = false,
        ipv4Address = "169.254.2.1",
        ipv4PrefixLength = TUN_PREFIX,
        dnsServers = listOf("1.1.1.1", "8.8.8.8"),
        allowFamilyV4 = true,
        allowFamilyV6 = false,
        excludeRfc1918 = true,
        routeAllV4 = true,
        routeAllV6 = false,
    )

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        PersistentLoggers.info(TAG, "attachTun fd=$tunFd")
        return when (val r = sdkBridge.attachTun(tunFd)) {
            UrnetworkSdkBridge.AttachResult.Success -> TunAttachResult.Success
            is UrnetworkSdkBridge.AttachResult.Failed -> TunAttachResult.Failure(r.reason)
        }
    }

    private suspend fun ensureGuestJwt(): String? {
        val existing = configStore.byJwt().first()
        if (existing != null) return existing
        PersistentLoggers.info(TAG, "no byJwt in store — auto-creating guest network")
        return when (val r = authService.acquireGuestJwt()) {
            is GuestJwtResult.Success -> {
                configStore.setByJwt(r.byJwt)
                Log.i(TAG, "guest jwt acquired and persisted")
                r.byJwt
            }
            is GuestJwtResult.Error -> {
                PersistentLoggers.error(TAG, "acquireGuestJwt failed: ${r.message}")
                null
            }
        }
    }

    private suspend fun ensureClientJwt(byJwt: String): String? {
        val existing = configStore.byClientJwt().first()
        if (existing != null) return existing
        PersistentLoggers.info(TAG, "no byClientJwt in store — calling authNetworkClient")
        return when (val r = authService.acquireClientJwt(byJwt)) {
            is ClientJwtResult.Success -> {
                configStore.setByClientJwt(r.byClientJwt)
                Log.i(TAG, "client jwt acquired and persisted")
                r.byClientJwt
            }
            is ClientJwtResult.Error -> {
                PersistentLoggers.error(TAG, "acquireClientJwt failed: ${r.message}")
                null
            }
        }
    }

    fun shutdown() {
        statsJobRef.getAndSet(null)?.cancel()
        pluginScope.cancel()
    }

    private companion object {
        const val TAG = "EngineUrnetwork"
        const val WALLET_LOG_PREFIX_LEN = 6
        const val TUN_MTU = 1440
        const val TUN_PREFIX = 32
        const val STATS_POLL_INTERVAL_MS = 2_000L

        const val URN_STOP_TIMEOUT_MS = 5_000L
    }
}
