package ru.ozero.engineurnetwork

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import ru.ozero.enginescore.settings.SettingsModel
import java.util.concurrent.atomic.AtomicReference

class EngineUrnetwork(
    private val configStore: UrnetworkConfigStore,
    private val sdkBridge: UrnetworkSdkBridge,
    private val jwtBootstrapper: UrnetworkJwtBootstrapper,
    private val pluginScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val statsPollIntervalMs: Long = STATS_POLL_INTERVAL_MS,
    private val startupReadyTimeoutMs: Long = STARTUP_READY_TIMEOUT_MS,
    private val startupReadyPollMs: Long = STARTUP_READY_POLL_MS,
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
        providesLocalSocks = false,
    )

    private val _stats = MutableStateFlow(EngineStats())

    override fun stopTimeoutMs(): Long = URN_STOP_TIMEOUT_MS

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig = EngineConfig.Urnetwork(
        jwtToken = settings?.urnetworkJwt.orEmpty(),
        region = settings?.urnetworkCountryCode,
    )

    override fun statsLabel(stats: EngineStats): String? {
        val snapshot = runCatching { sdkBridge.runtimeSnapshot() }.getOrNull()
        val peers = snapshot?.peers ?: stats.activeConnections
        val status = snapshot?.connectionStatus ?: runCatching { sdkBridge.connectionStatus() }.getOrNull()
        return when {
            peers > 0 -> "$peers peers"
            snapshot?.providerStateAdded?.let { it > 0L } == true -> "connected"
            isConnectedStatus(status) -> "connected"
            else -> null
        }
    }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Urnetwork) { "EngineUrnetwork требует EngineConfig.Urnetwork" }
        require(upstream is Upstream.None) {
            "EngineUrnetwork не принимает upstream — supportsUpstreamSocks=false"
        }

        when (val r = jwtBootstrapper.ensureClientJwt()) {
            UrnetworkJwtBootstrapper.Result.AlreadyPresent,
            UrnetworkJwtBootstrapper.Result.Acquired -> Unit
            is UrnetworkJwtBootstrapper.Result.Failed ->
                return StartResult.Failure(reason = r.reason)
        }
        val byClientJwt = configStore.byClientJwt().first() ?: return StartResult.Failure(
            reason = "URnetwork client jwt missing after bootstrap — race condition",
        )

        val wallet = configStore.walletAddress().first()
        Log.i(TAG, "start hasClientJwt=true")

        val bridgeResult = sdkBridge.start(
            walletAddress = wallet,
            apiUrl = UrnetworkDefaults.DEFAULT_API_URL,
            connectUrl = UrnetworkDefaults.DEFAULT_CONNECT_URL,
            byClientJwt = byClientJwt,
        )
        return when (bridgeResult) {
            UrnetworkSdkBridge.StartResult.Success -> {
                val stored = runCatching { configStore.selectedLocation().first() }.getOrNull()
                val merged = UrnetworkLocationSelection(
                    countryCode = stored?.countryCode ?: config.region,
                    region = stored?.region,
                    city = stored?.city,
                )
                runCatching { sdkBridge.setPreferredLocation(merged.normalized()) }
                    .onFailure { PersistentLoggers.warn(TAG, "setPreferredLocation threw: ${it.message}") }
                val windowType = configStore.windowType().first()
                val fixedIp = configStore.fixedIpSize().first()
                val allowDirect = configStore.allowDirect().first()
                runCatching { sdkBridge.applyPerformanceProfile(windowType, fixedIp, allowDirect) }
                    .onFailure { PersistentLoggers.warn(TAG, "applyPerformanceProfile threw: ${it.message}") }
                val provideEnabled = configStore.provideEnabled().first()
                runCatching { sdkBridge.setProvidePaused(!provideEnabled) }
                    .onSuccess { Log.i(TAG, "setProvidePaused(${!provideEnabled}) — provideEnabled=$provideEnabled") }
                    .onFailure { PersistentLoggers.warn(TAG, "setProvidePaused threw: ${it.message}") }
                val controlMode = configStore.provideControlMode().first()
                runCatching { sdkBridge.setProvideControlMode(controlMode) }
                    .onFailure { PersistentLoggers.warn(TAG, "setProvideControlMode threw: ${it.message}") }
                val networkMode = configStore.provideNetworkMode().first()
                runCatching { sdkBridge.setProvideNetworkMode(networkMode) }
                    .onFailure { PersistentLoggers.warn(TAG, "setProvideNetworkMode threw: ${it.message}") }
                Log.i(
                    TAG,
                    "started OK preferred=${merged.summary()} " +
                        "windowType=${windowType.rawValue} fixedIp=$fixedIp allowDirect=$allowDirect",
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
        return try {
            if (location != null) sdkBridge.connectTo(location) else sdkBridge.connectBestAvailable()
            Log.i(TAG, "recover: re-issued connect (location=${location?.countryCode ?: "<best>"})")
            EnginePlugin.RecoverResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            PersistentLoggers.warn(TAG, "recover threw: ${t.message}")
            EnginePlugin.RecoverResult.Failed("recover: ${t.message}")
        }
    }

    private fun startStatsPolling() {
        statsJobRef.getAndSet(null)?.cancel()
        val sessionStart = System.currentTimeMillis()
        val job = pluginScope.launch {
            while (true) {
                val snapshot = runCatching { sdkBridge.runtimeSnapshot() }
                    .getOrDefault(UrnetworkSdkBridge.RuntimeSnapshot())
                _stats.value = EngineStats(
                    activeConnections = snapshot.peers,
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
        if (sdkBridge.selectedLocation() == null) return ru.ozero.enginescore.IpProbeRoute.AutoSelected
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

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        var polls = 0
        val reached = withTimeoutOrNull(startupReadyTimeoutMs) {
            while (true) {
                val snapshot = runCatching { sdkBridge.runtimeSnapshot() }
                    .getOrDefault(UrnetworkSdkBridge.RuntimeSnapshot())
                if (isStartupReady(snapshot)) {
                    Log.i(
                        TAG,
                        "awaitReady: tunnelStarted=${snapshot.tunnelStarted} " +
                            "connectIssued=${snapshot.connectIssued} status=${snapshot.connectionStatus} " +
                            "peers=${snapshot.peers} providerStateAdded=${snapshot.providerStateAdded} — " +
                            "engine startup ready (after ${polls * startupReadyPollMs}ms)",
                    )
                    return@withTimeoutOrNull Unit
                }
                polls += 1
                if (polls % STARTUP_PROGRESS_LOG_EVERY == 0) {
                    PersistentLoggers.debug(
                        TAG,
                        "awaitReady progress: tunnelStarted=${snapshot.tunnelStarted} " +
                            "connectIssued=${snapshot.connectIssued} " +
                            "status=${snapshot.connectionStatus ?: "<null>"} peers=${snapshot.peers} " +
                            "providerStateAdded=${snapshot.providerStateAdded} " +
                            "elapsed≈${polls * startupReadyPollMs}ms " +
                            "deadline=${startupReadyTimeoutMs}ms",
                    )
                }
                delay(startupReadyPollMs)
            }
        }
        return if (reached != null) {
            EnginePlugin.ReadyResult.Ready
        } else {
            val reason = "URnetwork: TUN attached, but SDK did not confirm tunnel/connect within " +
                "${startupReadyTimeoutMs}ms"
            PersistentLoggers.warn(TAG, "awaitReady timeout — $reason")
            EnginePlugin.ReadyResult.Timeout(reason)
        }
    }

    private fun isStartupReady(snapshot: UrnetworkSdkBridge.RuntimeSnapshot): Boolean =
        snapshot.tunnelStarted && snapshot.connectIssued ||
            snapshot.providerStateAdded > 0L ||
            snapshot.peers > 0 ||
            isConnectedStatus(snapshot.connectionStatus)

    private fun isConnectedStatus(status: String?): Boolean =
        status?.equals(CONNECTION_STATUS_CONNECTED, ignoreCase = true) == true

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        PersistentLoggers.debug(TAG, "attachTun fd=$tunFd")
        return when (val r = sdkBridge.attachTun(tunFd)) {
            UrnetworkSdkBridge.AttachResult.Success -> TunAttachResult.Success
            is UrnetworkSdkBridge.AttachResult.Failed -> TunAttachResult.Failure(r.reason)
        }
    }

    private companion object {
        const val TAG = "EngineUrnetwork"
        const val TUN_MTU = 1440
        const val TUN_PREFIX = 32
        const val STATS_POLL_INTERVAL_MS = 2_000L

        const val URN_STOP_TIMEOUT_MS = 8_000L
        const val STARTUP_READY_TIMEOUT_MS = 8_000L
        const val STARTUP_READY_POLL_MS = 200L
        const val STARTUP_PROGRESS_LOG_EVERY = 10
        const val CONNECTION_STATUS_CONNECTED = "CONNECTED"
    }
}
