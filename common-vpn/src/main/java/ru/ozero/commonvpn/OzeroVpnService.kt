package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commondns.PublicDnsServers
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ChainResult
import ru.ozero.enginescore.ChainStep
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

fun interface ProcessKiller {
    fun kill(pid: Int)
}

private data class TunnelStatsReadResult(val rxBytes: Long, val txBytes: Long, val source: String)

@AndroidEntryPoint
@Suppress("TooManyFunctions", "LargeClass")
class OzeroVpnService : android.net.VpnService() {

    @Inject lateinit var chainOrchestrator: ChainOrchestrator

    @Inject lateinit var tunnelGateway: HevTunnelGateway

    @Inject lateinit var tunnelController: TunnelController

    @Inject lateinit var settingsRepository: ru.ozero.enginescore.settings.SettingsRepository

    @Inject lateinit var sessionStatsRecorder: SessionStatsRecorder

    @Inject lateinit var splitTunnelRulesProvider: SplitTunnelRulesProvider

    @Inject lateinit var healthMonitor: HealthMonitor

    @Inject lateinit var enginePlugins: Set<@JvmSuppressWildcards ru.ozero.enginescore.EnginePlugin>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionIdRef = AtomicReference<Long>(-1L)
    private val sessionStartMsRef = AtomicReference<Long>(0L)

    internal var processKiller: ProcessKiller = ProcessKiller { pid -> Process.killProcess(pid) }

    companion object {
        const val ACTION_START = "ru.ozero.vpn.ACTION_START"
        const val ACTION_STOP = "ru.ozero.vpn.ACTION_STOP"
        const val CHANNEL_ID = "ozero_vpn"
        const val NOTIFICATION_ID = 1
        const val TUN_ADDRESS = "10.10.10.10"
        const val TUN_PREFIX_LENGTH = 32
        const val TUN_ADDRESS_V6 = "fd00::1"
        const val TUN_PREFIX_LENGTH_V6 = 128
        val TUN_DNS_SERVERS: List<String> = PublicDnsServers.IPV4
        private const val SESSION_NAME = "Ozero"
        private const val TAG = "OzeroVpnService"
        private const val CHAIN_START_TIMEOUT_MS = 30_000L
        private const val CHAIN_STOP_TIMEOUT_MS = 3_000L
        private const val NATIVE_STOP_TIMEOUT_MS = 3_000L
        private const val SHUTDOWN_JOIN_TIMEOUT_MS = 7_000L
        private const val STATS_SAMPLE_INTERVAL_MS = 1_000L
        private const val STATS_LOG_EVERY = 30
        private const val STATS_NOTIFY_EVERY = 1
        private const val SETTINGS_READ_TIMEOUT_MS = 1_500L
        private const val ON_DESTROY_SHUTDOWN_TIMEOUT_MS = 4_000L
        private const val PREFLIGHT_HARD_TIMEOUT_MS = 7_000L
        private const val PEER_WATCHDOG_POLL_MS = 5_000L
        private const val PEER_WATCHDOG_TIMEOUT_MS = 30_000L
    }

    override fun onCreate() {
        PersistentLoggers.info(TAG, "onCreate before super")
        try {
            super.onCreate()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "super.onCreate threw — Hilt graph failure: ${t.message}", t)
            throw t
        }
        socketProtector = ru.ozero.enginescore.VpnSocketProtector { fd -> protect(fd) }
        ru.ozero.enginescore.VpnSocketProtectorHolder.bind(socketProtector!!)
        PersistentLoggers.info(TAG, "onCreate after super (Hilt inject done)")
    }

    private var socketProtector: ru.ozero.enginescore.VpnSocketProtector? = null

    private val tunFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val tunIfaceNameRef = AtomicReference<String?>(null)
    private val startJobRef = AtomicReference<Job?>(null)
    private val statsJobRef = AtomicReference<Job?>(null)
    private val shutdownJobRef = AtomicReference<Job?>(null)
    private val healthWatchJobRef = AtomicReference<Job?>(null)
    private val lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val peerWatchJobRef = AtomicReference<Job?>(null)
    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val stopSignal = AtomicBoolean(false)
    private val lastStopStartId = AtomicInteger(-1)

    @Volatile
    private var killswitchCached: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PersistentLoggers.info(TAG, "onStartCommand action=${intent?.action} startId=$startId")
        val foregroundOk = enterForegroundOrLog()
        if (!foregroundOk) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return try {
            if (!::chainOrchestrator.isInitialized) {
                PersistentLoggers.error(TAG, "chainOrchestrator not injected — Hilt graph failure")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            when (intent?.action) {
                ACTION_STOP -> {
                    lastStopStartId.set(startId)
                    stopVpn()
                }
                ACTION_START, null -> {
                    stopping.set(false)
                    startVpn()
                }
            }
            START_STICKY
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "onStartCommand threw: ${t.message}")
            runCatching { stopVpn() }
            START_NOT_STICKY
        }
    }

    private fun startVpn() {
        if (!starting.compareAndSet(false, true)) return
        stopSignal.set(false)
        PersistentLoggers.info(TAG, "startVpn entry")
        runCatching { tunFdRef.getAndSet(null)?.close() }
            .onFailure { PersistentLoggers.warn(TAG, "startVpn: stale tunFd close threw: ${it.message}") }
        tunnelController.onKillswitchReleased()

        val tName = Thread.currentThread().name
        val isMain = android.os.Looper.myLooper() === android.os.Looper.getMainLooper()
        PersistentLoggers.info(TAG, "loadOnce begin thread=$tName main=$isMain")
        runCatching { hev.TProxyService.loadOnce() }
            .onFailure { PersistentLoggers.warn(TAG, "TProxyService.loadOnce threw: ${it.message}") }
        PersistentLoggers.info(TAG, "loadOnce done libraryLoaded=${hev.TProxyService.libraryLoaded}")

        val job = serviceScope.launch {
            try {
                shutdownJobRef.getAndSet(null)?.let { prev ->
                    PersistentLoggers.info(TAG, "startVpn: ожидание завершения предыдущего shutdown")
                    runCatching {
                        withTimeoutOrNull(SHUTDOWN_JOIN_TIMEOUT_MS) { prev.join() }
                    }
                    PersistentLoggers.info(TAG, "startVpn: предыдущий shutdown завершён → продолжаем старт")
                }
                if (stopping.get()) {
                    PersistentLoggers.warn(TAG, "startVpn: stopping всё ещё активен после join — отказ старта")
                    return@launch
                }
                runStartSequence()
            } finally {
                starting.set(false)
            }
        }
        startJobRef.set(job)
    }

    private suspend fun runStartSequence() {
        if (stopping.get()) return
        val settings = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
            runCatching { settingsRepository.settings.first() }.getOrNull()
        }
        val splitPackages = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
            runCatching { splitTunnelRulesProvider.activePackages() }.getOrNull()
        } ?: emptySet()
        val splitConfig = ru.ozero.commonvpn.split.SplitTunnelConfig(
            mode = settings?.splitMode ?: ru.ozero.enginescore.settings.SplitTunnelMode.ALL,
            packages = splitPackages,
        )
        killswitchCached = settings?.killswitchEnabled ?: false
        if (killswitchCached) {
            val startupIpv6 = settings?.ipv6Enabled ?: false
            val startupDns = settings?.customDnsServers.orEmpty()
            runCatching { buildTunBuilder(splitConfig, startupIpv6, startupDns).establish() }
                .onSuccess { fd ->
                    if (fd != null) {
                        lockdownStartupFdRef.set(fd)
                        PersistentLoggers.info(TAG, "instant lockdown TUN — engine pick pending")
                    }
                }
                .onFailure { PersistentLoggers.error(TAG, "lockdown startup TUN failed: ${it.message}") }
        }

        val manualEngine = settings?.manualEngine
        val pick = if (manualEngine != null) {
            val cfg = buildEngineConfig(manualEngine, settings)
            if (cfg == null) null else manualEngine to cfg
        } else {
            pickAutoCandidateWithPreflight(settings)
        }
        if (pick == null) {
            val mode = if (manualEngine == null) "auto" else "manual"
            val targetForUi = if (manualEngine != null) manualEngine else EngineId.BYEDPI
            PersistentLoggers.error(
                TAG,
                "no engine reachable ($mode mode) — отказ старта",
            )
            tunnelController.onEngineDied(targetForUi, "no engine reachable ($mode mode)")
            stopVpn()
            return
        }
        val activeEngineId = pick.first
        val activeConfig = pick.second
        tunnelController.onProbing(activeEngineId)
        val usesCustomTun = engineNeedsCustomTun(activeEngineId)
        val ipv6Enabled = settings?.ipv6Enabled ?: false
        val fd = if (usesCustomTun) {
            establishTunForEngine(activeEngineId, splitConfig, ipv6Enabled) ?: return
        } else {
            establishTun(
                splitConfig,
                ipv6 = ipv6Enabled,
                customDns = settings?.customDnsServers.orEmpty(),
            ) ?: return
        }
        lockdownStartupFdRef.getAndSet(null)?.runCatching { close() }
        if (stopping.get()) {
            runCatching { fd.close() }
            tunFdRef.compareAndSet(fd, null)
            return
        }
        val chainResult = startChain(activeEngineId, activeConfig) ?: return
        if (!routeTrafficForEngine(activeEngineId, fd, chainResult.finalSocksPort)) return

        tunnelController.onEngineStarted(activeEngineId, chainResult.finalSocksPort)
        val nowMs = System.currentTimeMillis()
        sessionStartMsRef.set(nowMs)
        sessionIdRef.set(
            runCatching { sessionStatsRecorder.startSession(activeEngineId.name, nowMs) }.getOrDefault(-1L),
        )
        if (!usesCustomTun) {
            runCatching { healthMonitor.start(chainResult.finalSocksPort) }
                .onFailure { t ->
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    PersistentLoggers.warn(TAG, "healthMonitor.start threw: ${t.message}")
                }
        }
        if (!usesCustomTun) startHealthKillswitchWatcher(activeEngineId)
        if (usesCustomTun) startPeerWatchdog(activeEngineId)
        startStatsLogger()
    }

    private fun startHealthKillswitchWatcher(engineId: EngineId) {
        healthWatchJobRef.getAndSet(null)?.cancel()
        val job = serviceScope.launch {
            try {
                healthMonitor.status
                    .filter { it == HealthMonitor.Status.DEGRADED }
                    .first()
                if (killswitchCached && tunFdRef.get() != null && !stopping.get()) {
                    PersistentLoggers.warn(
                        TAG,
                        "health degraded → killswitch fire engine=$engineId",
                    )
                    enterKillswitchMode(engineId, "health degraded")
                } else {
                    PersistentLoggers.warn(
                        TAG,
                        "health degraded но killswitch off (cached=$killswitchCached) — без partial-shutdown",
                    )
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "health killswitch watcher threw: ${t.message}")
            }
        }
        healthWatchJobRef.set(job)
    }

    private fun startPeerWatchdog(engineId: EngineId) {
        peerWatchJobRef.getAndSet(null)?.cancel()
        val plugin = enginePlugins.firstOrNull { it.id == engineId } ?: return
        val job = serviceScope.launch {
            try {
                var zeroPeersSince = 0L
                var hadPeers = false
                while (isActive) {
                    delay(PEER_WATCHDOG_POLL_MS)
                    val peers = plugin.stats().first().activeConnections
                    if (peers > 0) {
                        hadPeers = true
                        zeroPeersSince = 0L
                    } else if (hadPeers) {
                        val now = System.currentTimeMillis()
                        if (zeroPeersSince == 0L) {
                            zeroPeersSince = now
                        } else if (now - zeroPeersSince > PEER_WATCHDOG_TIMEOUT_MS) {
                            handleEngineFailure(engineId, "no URnetwork peers for ${PEER_WATCHDOG_TIMEOUT_MS / 1000}s")
                            return@launch
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "peer watchdog threw: ${t.message}")
            }
        }
        peerWatchJobRef.set(job)
    }

    private suspend fun engineNeedsCustomTun(engineId: EngineId): Boolean {
        val plugin = enginePlugins.firstOrNull { it.id == engineId } ?: return false
        return plugin is ru.ozero.enginescore.TunFdAcceptor
    }

    private suspend fun establishTunForEngine(
        engineId: EngineId,
        splitConfig: ru.ozero.commonvpn.split.SplitTunnelConfig,
        ipv6Enabled: Boolean,
    ): ParcelFileDescriptor? {
        val plugin = enginePlugins.firstOrNull { it.id == engineId } ?: return null
        val spec = plugin.tunSpec() ?: return null
        val builder = applyEngineTunSpec(spec, ipv6Enabled)
        ru.ozero.commonvpn.split.TunBuilderConfigurator(packageName).apply(builder, splitConfig)
        val before = TunInterfaceStats.snapshotTunInterfaces()
        val pfd = try {
            builder.establish()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "engine TUN establish threw: ${t.message}")
            stopVpn()
            return null
        }
        if (pfd == null) {
            PersistentLoggers.error(TAG, "engine TUN establish returned null — permission revoked?")
            stopVpn()
            return null
        }
        tunFdRef.set(pfd)
        captureTunIfaceName(before)
        val iface = tunIfaceNameRef.get()
        Log.i(
            TAG,
            "engine TUN established fd=${pfd.fd} engineId=$engineId mtu=${spec.mtu} iface=$iface",
        )
        return pfd
    }

    private fun captureTunIfaceName(before: Set<String>) {
        val after = TunInterfaceStats.snapshotTunInterfaces()
        val picked = TunInterfaceStats.pickNewTunInterface(before, after)
        tunIfaceNameRef.set(picked)
        if (picked == null) {
            PersistentLoggers.warn(TAG, "tun interface discovery failed — stats logger будет получать null")
        }
    }

    private fun buildEngineConfig(
        engineId: EngineId,
        settings: ru.ozero.enginescore.settings.SettingsModel?,
    ): EngineConfig? = ManualEngineConfigBuilder.build(engineId, settings)

    private fun autoCandidates(
        settings: ru.ozero.enginescore.settings.SettingsModel?,
    ): List<Pair<EngineId, EngineConfig>> {
        val priority = settings?.engineAutoPriority
            ?: ru.ozero.enginescore.settings.SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY
        return priority.mapNotNull { id ->
            val cfg = buildEngineConfig(id, settings) ?: return@mapNotNull null
            id to cfg
        }
    }

    private suspend fun pickAutoCandidateWithPreflight(
        settings: ru.ozero.enginescore.settings.SettingsModel?,
    ): Pair<EngineId, EngineConfig>? {
        val candidates = autoCandidates(settings)
        if (candidates.isEmpty()) {
            PersistentLoggers.warn(TAG, "auto-mode: нет валидных кандидатов")
            return null
        }
        val protector = ru.ozero.enginescore.SocketProtector { _ -> true }
        for ((id, cfg) in candidates) {
            val plugin = enginePlugins.firstOrNull { it.id == id }
            val preflight = plugin?.preflight()
            if (preflight == null) {
                Log.i(TAG, "auto-mode preflight skip (null) engine=$id — берём как кандидата")
                return id to cfg
            }
            tunnelController.onProbing(id)
            val result = withTimeoutOrNull(PREFLIGHT_HARD_TIMEOUT_MS) {
                runCatching { preflight.probe(protector) }
                    .getOrElse {
                        ru.ozero.enginescore.EnginePreflight.Result.Fail(
                            "preflight threw: ${it.message}",
                        )
                    }
            } ?: ru.ozero.enginescore.EnginePreflight.Result.Fail(
                "preflight hard-timeout ${PREFLIGHT_HARD_TIMEOUT_MS}ms",
            )
            when (result) {
                is ru.ozero.enginescore.EnginePreflight.Result.Ok -> {
                    Log.i(TAG, "auto-mode preflight OK engine=$id")
                    return id to cfg
                }
                is ru.ozero.enginescore.EnginePreflight.Result.Fail -> {
                    PersistentLoggers.warn(TAG, "auto-mode preflight FAIL engine=$id reason=${result.reason}")
                }
            }
        }
        return null
    }

    private fun establishTun(
        splitConfig: ru.ozero.commonvpn.split.SplitTunnelConfig,
        ipv6: Boolean,
        customDns: List<String>,
    ): ParcelFileDescriptor? {
        val before = TunInterfaceStats.snapshotTunInterfaces()
        val fd = try {
            buildTunBuilder(splitConfig = splitConfig, ipv6Enabled = ipv6, customDnsServers = customDns)
                .establish()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "establish threw: ${t.message}")
            stopVpn()
            return null
        }
        if (fd == null) {
            PersistentLoggers.error(TAG, "establish returned null — permission revoked?")
            stopVpn()
            return null
        }
        tunFdRef.set(fd)
        captureTunIfaceName(before)
        Log.i(TAG, "TUN established fd=${fd.fd} iface=${tunIfaceNameRef.get()}")
        return fd
    }

    private suspend fun startChain(engineId: EngineId, config: EngineConfig): ChainResult.Success? {
        val chainResult = withTimeoutOrNull(CHAIN_START_TIMEOUT_MS) {
            try {
                chainOrchestrator.start(listOf(ChainStep(engineId, config)))
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "chain.start threw: ${t.message}")
                null
            }
        }
        Log.i(TAG, "chain result=$chainResult engineId=$engineId")
        if (chainResult !is ChainResult.Success) {
            handleEngineFailure(engineId, chainResult?.toString() ?: "timeout")
            return null
        }
        tunnelController.onConnecting(engineId)
        return chainResult
    }

    private suspend fun routeTrafficForEngine(
        engineId: EngineId,
        fd: ParcelFileDescriptor,
        socksPort: Int,
    ): Boolean {
        val engine = enginePlugins.firstOrNull { it.id == engineId }
        if (engine is ru.ozero.enginescore.TunFdAcceptor) {
            val rawDupFd = fd.dup().detachFd()
            val result = try {
                engine.attachTun(rawDupFd)
            } catch (t: Throwable) {
                runCatching { tunFdRef.getAndSet(null)?.close() }
                PersistentLoggers.error(TAG, "attachTun threw, fd closed: ${t.message}")
                runCatching { chainOrchestrator.stop() }
                handleEngineFailure(engineId, "attachTun threw: ${t.message}")
                return false
            }
            return when (result) {
                ru.ozero.enginescore.TunAttachResult.Success -> {
                    true
                }
                is ru.ozero.enginescore.TunAttachResult.Failure -> {
                    runCatching { tunFdRef.getAndSet(null)?.close() }
                    PersistentLoggers.error(TAG, "attachTun failed: ${result.reason}")
                    runCatching { chainOrchestrator.stop() }
                    handleEngineFailure(engineId, "attachTun: ${result.reason}")
                    false
                }
            }
        }
        return startNativeTunnel(engineId, fd, socksPort)
    }

    private suspend fun startNativeTunnel(
        engineId: EngineId,
        fd: ParcelFileDescriptor,
        socksPort: Int,
    ): Boolean {
        val code = try {
            tunnelGateway.start(
                HevTunnelConfig(tunPfd = fd, socksAddress = "127.0.0.1", socksPort = socksPort),
            )
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "tunnelGateway.start threw: ${t.message}")
            runCatching { chainOrchestrator.stop() }
            stopVpn()
            return false
        }
        if (code != 0) {
            PersistentLoggers.error(TAG, "tunnel start failed code=$code")
            runCatching { chainOrchestrator.stop() }
            handleEngineFailure(engineId, "tunnel code=$code")
            return false
        }
        return true
    }

    private fun startStatsLogger() {
        statsJobRef.getAndSet(null)?.cancel()
        val job = serviceScope.launch {
            var prevTx = 0L
            var prevRx = 0L
            var tickCount = 0
            try {
                while (true) {
                    delay(STATS_SAMPLE_INTERVAL_MS)
                    if (stopSignal.get()) return@launch
                    val iface = tunIfaceNameRef.get()
                    val read = if (iface != null) {
                        TunInterfaceStats.readTunStats(iface)?.let {
                            TunnelStatsReadResult(it.rxBytes, it.txBytes, "iface=$iface")
                        }
                    } else {
                        null
                    } ?: UidTrafficStats.read()?.let {
                        TunnelStatsReadResult(it.rxBytes, it.txBytes, "uid")
                    }
                    if (read == null) {
                        if (tickCount % STATS_LOG_EVERY == 0) {
                            PersistentLoggers.warn(TAG, "TunnelStats: ни iface, ни uid stats недоступны")
                        }
                        tickCount++
                        continue
                    }
                    val rxBytes = read.rxBytes
                    val txBytes = read.txBytes
                    val source = read.source
                    val snapshot = TunnelStats(
                        txPackets = 0L,
                        txBytes = txBytes,
                        rxPackets = 0L,
                        rxBytes = rxBytes,
                        timestampMs = System.currentTimeMillis(),
                    )
                    tunnelController.updateStats(snapshot)
                    tickCount++
                    if (tickCount % STATS_NOTIFY_EVERY == 0) {
                        updateNotificationWithStats(tunnelController.stats.value)
                    }
                    if (tickCount % STATS_LOG_EVERY == 0) {
                        val dTx = txBytes - prevTx
                        val dRx = rxBytes - prevRx
                        Log.i(
                            TAG,
                            "TunnelStats[$source] tx=${BytesFormatter.humanReadable(txBytes)} " +
                                "rx=${BytesFormatter.humanReadable(rxBytes)} " +
                                "Δtx=${BytesFormatter.humanReadable(dTx)} Δrx=${BytesFormatter.humanReadable(dRx)}",
                        )
                        prevTx = txBytes
                        prevRx = rxBytes
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "stats logger threw: ${t.message}")
            }
        }
        statsJobRef.set(job)
    }

    private fun updateNotificationWithStats(snapshot: TunnelStats?) {
        if (stopSignal.get() || snapshot == null) return
        runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val text = NotificationStatsFormatter.format(snapshot, engineExtras())
            val n = buildNotification(text)
            nm.notify(NOTIFICATION_ID, n)
        }.onFailure { PersistentLoggers.warn(TAG, "updateNotificationWithStats: ${it.message}") }
    }

    private fun engineExtras(): String {
        if (!::chainOrchestrator.isInitialized) return ""
        return runCatching {
            chainOrchestrator.activeEngines()
                .mapNotNull { plugin ->
                    val flow = plugin.stats() as? kotlinx.coroutines.flow.StateFlow<*>
                    val stats = flow?.value as? ru.ozero.enginescore.EngineStats
                    val peers = stats?.activeConnections ?: 0
                    when {
                        plugin.id == EngineId.URNETWORK && peers > 0 -> "$peers peers"
                        peers > 0 -> "$peers conns"
                        else -> null
                    }
                }
                .joinToString(" · ")
        }.getOrDefault("")
    }

    private fun handleEngineFailure(engineId: EngineId, reason: String) {
        val fdAlive = tunFdRef.get() != null
        if (killswitchCached && fdAlive) {
            enterKillswitchMode(engineId, reason)
        } else {
            tunnelController.onEngineDied(engineId, reason)
            stopVpn()
        }
    }

    private fun enterKillswitchMode(engineId: EngineId, reason: String) {
        PersistentLoggers.warn(TAG, "killswitch engaging: engine=$engineId reason=$reason")
        tunnelController.onKillswitchEngaged(engineId, reason)
        statsJobRef.getAndSet(null)?.cancel()
        healthWatchJobRef.getAndSet(null)?.cancel()
        peerWatchJobRef.getAndSet(null)?.cancel()
        serviceScope.launch {
            runCatching { chainOrchestrator.stop() }
                .onFailure { t ->
                    PersistentLoggers.warn(TAG, "killswitch: chainOrchestrator.stop threw: ${t.message}")
                }
        }
        runCatching { healthMonitor.stop() }
        runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return@runCatching
            nm.notify(NOTIFICATION_ID, buildNotification("Killswitch активен — трафик заблокирован"))
        }.onFailure { PersistentLoggers.warn(TAG, "killswitch: notification update threw: ${it.message}") }
        starting.set(false)
    }

    private fun stopVpn() {
        if (!stopping.compareAndSet(false, true)) return
        stopSignal.set(true)
        PersistentLoggers.info(TAG, "stopVpn entry")
        runCatching { tunnelController.onKillswitchReleased() }
        val priorState = tunnelController.state.value
        tunnelController.onDisconnecting()
        startJobRef.getAndSet(null)?.cancel()
        statsJobRef.getAndSet(null)?.cancel()
        healthWatchJobRef.getAndSet(null)?.cancel()
        peerWatchJobRef.getAndSet(null)?.cancel()
        lockdownStartupFdRef.getAndSet(null)?.runCatching { close() }
        runCatching { healthMonitor.stop() }
        val endStatus = if (priorState is TunnelState.Failed) {
            SessionStatsRecorder.Status.FAILED
        } else {
            SessionStatsRecorder.Status.DISCONNECTED
        }
        recordSessionEnd(endStatus)
        val job = serviceScope.launch { performShutdown() }
        shutdownJobRef.set(job)
    }

    private fun recordSessionEnd(status: SessionStatsRecorder.Status) {
        val id = sessionIdRef.getAndSet(-1L)
        if (id < 0) return
        val startMs = sessionStartMsRef.getAndSet(0L)
        val nowMs = System.currentTimeMillis()
        val durationMs = if (startMs > 0L) (nowMs - startMs).coerceAtLeast(0L) else 0L
        val lastStats = tunnelController.stats.value
        val rxBytes = lastStats?.rxBytes ?: 0L
        val txBytes = lastStats?.txBytes ?: 0L
        serviceScope.launch {
            runCatching {
                sessionStatsRecorder.endSession(
                    id = id,
                    endedAt = nowMs,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                    durationMs = durationMs,
                    status = status,
                )
            }.onFailure { PersistentLoggers.warn(TAG, "endSession threw: ${it.message}") }
        }
    }

    private suspend fun performShutdown(callStopSelf: Boolean = true) {
        PersistentLoggers.info(TAG, "performShutdown begin")
        try {
            statsJobRef.getAndSet(null)?.cancel()

            val chainStopJob = serviceScope.launch {
                runCatching { chainOrchestrator.stop() }
                    .onFailure { PersistentLoggers.warn(TAG, "chainOrchestrator.stop threw: ${it.message}") }
            }
            val chainOk = withTimeoutOrNull(CHAIN_STOP_TIMEOUT_MS) { chainStopJob.join() }
            if (chainOk == null) {
                PersistentLoggers.warn(TAG, "chainOrchestrator.stop hung > ${CHAIN_STOP_TIMEOUT_MS}ms")
            } else {
                Log.i(TAG, "chainOrchestrator.stop completed")
            }

            val nativeStopThread = Thread({
                runCatching { tunnelGateway.stop() }
                    .onFailure { PersistentLoggers.warn(TAG, "tunnelGateway.stop threw: ${it.message}") }
            }, "ozero-native-stop")
            nativeStopThread.isDaemon = true
            nativeStopThread.start()
            nativeStopThread.join(NATIVE_STOP_TIMEOUT_MS)
            if (nativeStopThread.isAlive) {
                PersistentLoggers.warn(
                    TAG,
                    "native tunnel stop hung > ${NATIVE_STOP_TIMEOUT_MS}ms — abandon thread",
                )
            } else {
                Log.i(TAG, "native tunnel stop completed")
            }

            runCatching { tunFdRef.getAndSet(null)?.close() }
                .onFailure { PersistentLoggers.warn(TAG, "tunFd.close threw: ${it.message}") }
        } finally {
            tunnelController.reset()
            starting.set(false)
            stopping.set(false)
            stopSignal.set(false)
            tunIfaceNameRef.set(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            if (callStopSelf) stopSelf(lastStopStartId.get())
            PersistentLoggers.info(TAG, "performShutdown end")
        }
    }

    internal fun applyEngineTunSpec(spec: ru.ozero.enginescore.TunSpec, ipv6Enabled: Boolean): Builder {
        val builder = Builder()
            .setSession(spec.sessionName)
            .setMtu(spec.mtu)
            .setBlocking(spec.blocking)
            .addAddress(spec.ipv4Address, spec.ipv4PrefixLength)
        applyLockdown(builder, "applyEngineTunSpec")
        spec.dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { PersistentLoggers.warn(TAG, "spec addDnsServer rejected '$dns': ${it.message}") }
        }
        if (spec.allowFamilyV4) builder.allowFamily(android.system.OsConstants.AF_INET)
        if (spec.allowFamilyV6) builder.allowFamily(android.system.OsConstants.AF_INET6)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { builder.setMetered(false) }
        }
        if (spec.routeAllV4) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && spec.excludeRfc1918) {
                builder.addRoute("0.0.0.0", 0)
                runCatching {
                    builder.excludeRoute(
                        android.net.IpPrefix(java.net.InetAddress.getByName("10.0.0.0"), 8),
                    )
                    builder.excludeRoute(
                        android.net.IpPrefix(java.net.InetAddress.getByName("172.16.0.0"), 12),
                    )
                    builder.excludeRoute(
                        android.net.IpPrefix(java.net.InetAddress.getByName("192.168.0.0"), 16),
                    )
                }.onFailure { PersistentLoggers.warn(TAG, "excludeRoute RFC1918 failed: ${it.message}") }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }
        }
        val v6 = spec.ipv6Address
        if (ipv6Enabled && spec.allowFamilyV6 && v6 != null) {
            builder.addAddress(v6, spec.ipv6PrefixLength)
            if (spec.routeAllV6) builder.addRoute("::", 0)
        } else {
            blackholeIpv6(builder, "applyEngineTunSpec")
        }
        return builder
    }

    private fun applyLockdown(builder: Builder, callerTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            runCatching { builder.setUnderlyingNetworks(null) }
                .onFailure { t ->
                    PersistentLoggers.warn(
                        TAG,
                        "$callerTag: setUnderlyingNetworks(null) failed: ${t.message}",
                    )
                }
        }
    }

    private fun blackholeIpv6(builder: Builder, callerTag: String) {
        runCatching {
            builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            builder.addRoute("::", 0)
        }.onFailure {
            PersistentLoggers.warn(TAG, "$callerTag: blackhole IPv6 failed: ${it.message}")
        }
    }

    internal fun buildTunBuilder(
        splitConfig: ru.ozero.commonvpn.split.SplitTunnelConfig =
            ru.ozero.commonvpn.split.SplitTunnelConfig(),
        ipv6Enabled: Boolean = false,
        customDnsServers: List<String> = emptyList(),
    ): Builder {
        val builder = Builder()
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .setSession(SESSION_NAME)
        applyLockdown(builder, "buildTunBuilder")
        if (ipv6Enabled) {
            builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            builder.addRoute("::", 0)
        } else {
            blackholeIpv6(builder, "buildTunBuilder")
        }
        val dnsServers = if (customDnsServers.isNotEmpty()) customDnsServers else TUN_DNS_SERVERS
        dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { PersistentLoggers.warn(TAG, "addDnsServer rejected '$dns': ${it.message}") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { builder.setMetered(false) }
        }
        ru.ozero.commonvpn.split.TunBuilderConfigurator(packageName).apply(builder, splitConfig)
        return builder
    }

    private fun buildNotification(contentText: String? = null): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            android.app.PendingIntent.getActivity(
                this, 0, it,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val stopIntent = Intent(this, OzeroVpnService::class.java).setAction(ACTION_STOP)
        val stopPending = android.app.PendingIntent.getService(
            this, 1, stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending,
        ).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ozero VPN", NotificationManager.IMPORTANCE_LOW),
            )
            return Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ozero VPN активен")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(stopAction)
                .apply {
                    if (contentText != null) {
                        val firstLine = contentText.substringBefore('\n')
                        setContentText(firstLine)
                        setStyle(Notification.BigTextStyle().bigText(contentText))
                    }
                    if (contentIntent != null) setContentIntent(contentIntent)
                }
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(this)
            .setContentTitle("Ozero VPN активен")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(stopAction)
            .apply {
                if (contentText != null) {
                    val firstLine = contentText.substringBefore('\n')
                    setContentText(firstLine)
                    @Suppress("DEPRECATION")
                    setStyle(Notification.BigTextStyle().bigText(contentText))
                }
                if (contentIntent != null) setContentIntent(contentIntent)
            }
            .build()
    }

    override fun onRevoke() {
        PersistentLoggers.warn(TAG, "onRevoke — VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        PersistentLoggers.info(TAG, "onDestroy entry")
        socketProtector?.let { ru.ozero.enginescore.VpnSocketProtectorHolder.unbind(it) }
        if (stopping.compareAndSet(false, true)) {
            runBlocking(Dispatchers.IO) {
                val ok = withTimeoutOrNull(ON_DESTROY_SHUTDOWN_TIMEOUT_MS) { performShutdown(callStopSelf = false) }
                if (ok == null) {
                    PersistentLoggers.warn(
                        TAG,
                        "onDestroy shutdown timeout > ${ON_DESTROY_SHUTDOWN_TIMEOUT_MS}ms — abandon",
                    )
                }
            }
        }
        runCatching { healthMonitor.stop() }
        serviceScope.cancel()
        runCatching { tunFdRef.getAndSet(null)?.close() }
        super.onDestroy()
    }

    private fun enterForegroundOrLog(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val n = buildNotification()
                val su = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                val fb = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                try {
                    startForeground(NOTIFICATION_ID, n, su)
                } catch (t: Throwable) {
                    PersistentLoggers.warn(TAG, "startForeground SPECIAL_USE rejected, fallback to MANIFEST type", t)
                    startForeground(NOTIFICATION_ID, n, fb)
                }
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            Log.i(TAG, "startForeground OK")
            true
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "startForeground threw: ${t.message}", t)
            false
        }
    }
}
