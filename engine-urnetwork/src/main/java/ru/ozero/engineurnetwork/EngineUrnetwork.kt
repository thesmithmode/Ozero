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
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.DeviceWalletJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.engineurnetwork.auth.UrnetworkDeviceIdentity
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
    private val authService: UrnetworkAuthService,
    private val deviceIdentity: UrnetworkDeviceIdentity?,
    private val networkNameGenerator: () -> String = { defaultNetworkName() },
    private val pluginScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val statsPollIntervalMs: Long = STATS_POLL_INTERVAL_MS,
    private val peerReadyTimeoutMs: Long = PEER_READY_TIMEOUT_MS,
    private val peerReadyPollMs: Long = PEER_READY_POLL_MS,
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

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig = EngineConfig.Urnetwork(
        jwtToken = settings?.urnetworkJwt.orEmpty(),
        region = settings?.urnetworkCountryCode,
    )

    override fun statsLabel(stats: EngineStats): String? {
        val peers = stats.activeConnections
        return if (peers > 0) "$peers peers" else null
    }

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
        val reached = withTimeoutOrNull(peerReadyTimeoutMs) {
            while (true) {
                val peers = runCatching { sdkBridge.peerCount() }.getOrDefault(0)
                if (peers > 0) {
                    Log.i(TAG, "awaitReady: peers=$peers — engine ready (after ${polls * peerReadyPollMs}ms)")
                    return@withTimeoutOrNull Unit
                }
                polls += 1
                if (polls % PEER_PROGRESS_LOG_EVERY == 0) {
                    PersistentLoggers.info(
                        TAG,
                        "awaitReady progress: peers=0 elapsed≈${polls * peerReadyPollMs}ms " +
                            "deadline=${peerReadyTimeoutMs}ms",
                    )
                }
                delay(peerReadyPollMs)
            }
        }
        return if (reached != null) {
            EnginePlugin.ReadyResult.Ready
        } else {
            val reason = "URnetwork: нет пиров за ${peerReadyTimeoutMs}ms"
            PersistentLoggers.warn(TAG, "awaitReady timeout — $reason — peer watchdog возьмёт")
            EnginePlugin.ReadyResult.Timeout(reason)
        }
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        PersistentLoggers.info(TAG, "attachTun fd=$tunFd")
        return when (val r = sdkBridge.attachTun(tunFd)) {
            UrnetworkSdkBridge.AttachResult.Success -> TunAttachResult.Success
            is UrnetworkSdkBridge.AttachResult.Failed -> TunAttachResult.Failure(r.reason)
        }
    }

    private suspend fun ensureGuestJwt(): String? {
        val existing = configStore.byJwt().first()
        val pubkey = configStore.devicePubkey().first()
        if (existing != null && !pubkey.isNullOrBlank()) return existing
        val deviceJwt = tryAcquireDeviceJwt(legacyMigration = existing != null)
        if (deviceJwt != null) return deviceJwt
        if (existing != null) {
            PersistentLoggers.info(TAG, "device walletAuth unavailable — keeping legacy guest byJwt")
            return existing
        }
        PersistentLoggers.info(TAG, "device walletAuth unavailable — fallback to guest network")
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

    // walletAuth migration критична: каждый Ozero-юзер должен иметь свою URnetwork-network
    // (per-device Ed25519 identity), payout = PRESET_WALLET общий. БЕЗ migration legacy guest
    // юзеры остаются на shared guest network → НЕ становятся per-user providers → выплаты не идут.
    //
    // Заметка о startBalance > 34 GiB: НЕ баг клиента. Бэкенд добавляет строку в transfer_balance
    // при каждом ежедневном кроне (RefreshTransferBalanceDuration=30ч, крон=каждые 24ч). SubscriptionBalance
    // суммирует ВСЕ активные строки. При создании сети незадолго до полуночи UTC → 3 строки могут
    // быть активны одновременно (overlap window ~5ч) → startBalance = 3×34 = 102 GiB. Само падает.
    // UX fix: кэпировать отображение на 34 GiB (free tier) в UrnetworkBalanceCard.
    private suspend fun tryAcquireDeviceJwt(legacyMigration: Boolean): String? {
        val identity = deviceIdentity ?: return null
        val storedName = configStore.deviceNetworkName().first()
        val networkName = storedName?.takeIf { it.isNotBlank() } ?: networkNameGenerator()
        PersistentLoggers.info(
            TAG,
            if (legacyMigration) {
                "device walletAuth — migrating legacy guest byJwt to per-device keypair"
            } else {
                "device walletAuth — acquiring jwt via per-device keypair"
            },
        )
        return when (val r = authService.acquireDeviceWalletJwt(identity, networkName)) {
            is DeviceWalletJwtResult.Success -> {
                val pubkey = runCatching { identity.pubkeyBase58() }.getOrNull()
                configStore.update { cfg ->
                    cfg.copy(
                        byJwt = r.byJwt,
                        byClientJwt = if (legacyMigration) null else cfg.byClientJwt,
                        devicePubkey = pubkey ?: cfg.devicePubkey,
                        deviceNetworkName = networkName,
                    )
                }
                Log.i(
                    TAG,
                    "device walletAuth jwt acquired isNew=${r.isNewNetwork} " +
                        "migration=$legacyMigration " +
                        "pubkey=${pubkey?.take(PUBKEY_LOG_PREFIX_LEN) ?: "?"}…",
                )
                r.byJwt
            }
            is DeviceWalletJwtResult.Error -> {
                PersistentLoggers.warn(TAG, "device walletAuth failed: ${r.message}")
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

    private companion object {
        const val TAG = "EngineUrnetwork"
        const val PUBKEY_LOG_PREFIX_LEN = 8
        const val TUN_MTU = 1440
        const val TUN_PREFIX = 32
        const val STATS_POLL_INTERVAL_MS = 2_000L
        const val NETWORK_NAME_RANDOM_BYTES = 8

        const val URN_STOP_TIMEOUT_MS = 8_000L
        const val PEER_READY_TIMEOUT_MS = 45_000L
        const val PEER_READY_POLL_MS = 200L
        const val PEER_PROGRESS_LOG_EVERY = 15

        private fun defaultNetworkName(): String {
            val rnd = java.security.SecureRandom()
            val bytes = ByteArray(NETWORK_NAME_RANDOM_BYTES).also { rnd.nextBytes(it) }
            val hex = bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
            return "n$hex"
        }
    }
}
