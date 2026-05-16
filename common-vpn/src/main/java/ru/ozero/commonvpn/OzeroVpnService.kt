package ru.ozero.commonvpn

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ChainResult
import ru.ozero.enginescore.ChainStep
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

fun interface ProcessKiller {
    fun kill(pid: Int)
}

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

    @Inject lateinit var enginePlugins: Set<@JvmSuppressWildcards EnginePlugin>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionIdRef = AtomicReference<Long>(-1L)
    private val sessionStartMsRef = AtomicReference<Long>(0L)

    internal var processKiller: ProcessKiller = ProcessKiller { pid -> Process.killProcess(pid) }

    private val notificationFactory by lazy {
        OzeroNotificationFactory(this, OzeroVpnService::class.java)
    }
    private val tunBuilderHelper by lazy { TunBuilderHelper(this) }
    private val statsLogger by lazy {
        TunnelStatsLogger(
            scope = serviceScope,
            tunnelController = tunnelController,
            notificationFactory = notificationFactory,
            tunIfaceNameRef = tunIfaceNameRef,
            stopSignal = stopSignal,
            statsJobRef = statsJobRef,
            engineExtras = ::engineExtras,
        )
    }
    private val engineWatchdog by lazy {
        EngineWatchdogCoordinator(
            scope = serviceScope,
            healthMonitor = healthMonitor,
            enginePlugins = enginePlugins,
            tunnelController = tunnelController,
            chainOrchestrator = chainOrchestrator,
            notificationFactory = notificationFactory,
            tunFdRef = tunFdRef,
            statsJobRef = statsJobRef,
            stopping = stopping,
            starting = starting,
            killswitchProvider = { killswitchCached },
            stopVpnRequest = { stopVpn() },
        )
    }

    companion object {
        const val ACTION_START = "ru.ozero.vpn.ACTION_START"
        const val ACTION_STOP = "ru.ozero.vpn.ACTION_STOP"
        const val TUN_ADDRESS = TunBuilderHelper.TUN_ADDRESS
        const val TUN_PREFIX_LENGTH = TunBuilderHelper.TUN_PREFIX_LENGTH
        const val TUN_ADDRESS_V6 = TunBuilderHelper.TUN_ADDRESS_V6
        const val TUN_PREFIX_LENGTH_V6 = TunBuilderHelper.TUN_PREFIX_LENGTH_V6
        val TUN_DNS_SERVERS: List<String> = TunBuilderHelper.TUN_DNS_SERVERS
        private const val TAG = "OzeroVpnService"
        private const val CHAIN_START_TIMEOUT_MS = 30_000L
        private const val PARALLEL_STOP_TIMEOUT_MS = 4_000L
        private const val SHUTDOWN_JOIN_TIMEOUT_MS = 7_000L
        private const val SETTINGS_READ_TIMEOUT_MS = 1_500L
        private const val ON_DESTROY_SHUTDOWN_TIMEOUT_MS = 5_000L
        private const val PREFLIGHT_HARD_TIMEOUT_MS = 7_000L
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
    private val lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val stopSignal = AtomicBoolean(false)
    private val latestStartId = AtomicInteger(-1)

    @Volatile
    private var killswitchCached: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PersistentLoggers.info(TAG, "onStartCommand action=${intent?.action} startId=$startId")
        val foregroundOk = notificationFactory.enterForeground(this)
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
            latestStartId.set(startId)
            when (intent?.action) {
                ACTION_STOP -> stopVpn()
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
        val splitConfig = readSplitConfig(
            settings?.splitMode ?: ru.ozero.enginescore.settings.SplitTunnelMode.ALL,
        )
        killswitchCached = settings?.killswitchEnabled ?: false
        if (killswitchCached) {
            val startupIpv6 = settings?.ipv6Enabled ?: false
            val startupDns = settings?.customDnsServers.orEmpty()
            runCatching { tunBuilderHelper.buildTunBuilder(splitConfig, startupIpv6, startupDns).establish() }
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
            val targetForUi = resolveTargetForUi(manualEngine, settings) ?: run {
                PersistentLoggers.error(TAG, "no plugins registered — отказ старта")
                stopVpn()
                return
            }
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

        awaitEngineReady(activeEngineId)

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
        if (!usesCustomTun) engineWatchdog.startHealthKillswitchWatcher(activeEngineId)
        if (usesCustomTun) engineWatchdog.startPeerWatchdog(activeEngineId)
        statsLogger.start()
    }

    private suspend fun readSplitConfig(
        mode: ru.ozero.enginescore.settings.SplitTunnelMode,
    ): ru.ozero.commonvpn.split.SplitTunnelConfig {
        val allowlist = if (mode == ru.ozero.enginescore.settings.SplitTunnelMode.ALLOWLIST) {
            val r = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                runCatching { splitTunnelRulesProvider.allowlistPackages() }.getOrNull()
            }
            if (r == null) PersistentLoggers.warn(TAG, "allowlist read timeout — fallback emptySet")
            r ?: emptySet()
        } else {
            emptySet()
        }
        val blocklist = if (mode == ru.ozero.enginescore.settings.SplitTunnelMode.BLOCKLIST) {
            val r = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                runCatching { splitTunnelRulesProvider.blocklistPackages() }.getOrNull()
            }
            if (r == null) PersistentLoggers.warn(TAG, "blocklist read timeout — fallback emptySet")
            r ?: emptySet()
        } else {
            emptySet()
        }
        return ru.ozero.commonvpn.split.SplitTunnelConfig(mode = mode, allowlist = allowlist, blocklist = blocklist)
    }

    private suspend fun awaitEngineReady(engineId: EngineId) {
        val plugin = enginePlugins.firstOrNull { it.id == engineId } ?: return
        val result = plugin.awaitReady()
        if (result is ru.ozero.enginescore.EnginePlugin.ReadyResult.Timeout) {
            PersistentLoggers.warn(
                TAG,
                "awaitReady timeout for $engineId: ${result.reason} — watchdog will catch",
            )
        }
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
        val builder = tunBuilderHelper.applyEngineTunSpec(spec, ipv6Enabled)
        // excludeSelf=true обязателен для всех движков: split tunnel ALL требует addDisallowedApplication(),
        // иначе Android не активирует per-app VPN mode → recursive loopback ломает upstream.
        // IP-probe routing per engine — через EnginePlugin.ipProbeRoute(), не через split tunnel.
        ru.ozero.commonvpn.split.TunBuilderConfigurator(packageName).apply(
            builder,
            splitConfig,
            excludeSelf = true,
        )
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
            Log.d(TAG, "tun interface discovery failed — stats logger будет получать null")
        }
    }

    private fun buildEngineConfig(
        engineId: EngineId,
        settings: ru.ozero.enginescore.settings.SettingsModel?,
    ): EngineConfig? = enginePlugins.firstOrNull { it.id == engineId }?.buildManualConfig(settings)

    private fun resolveTargetForUi(
        manualEngine: EngineId?,
        settings: ru.ozero.enginescore.settings.SettingsModel?,
    ): EngineId? = manualEngine
        ?: settings?.engineAutoPriority?.firstOrNull()
        ?: enginePlugins.firstOrNull()?.id

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
            tunBuilderHelper.buildTunBuilder(
                splitConfig = splitConfig,
                ipv6Enabled = ipv6,
                customDnsServers = customDns,
            ).establish()
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
            engineWatchdog.handleEngineFailure(engineId, chainResult?.toString() ?: "timeout")
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
                runCatching { ParcelFileDescriptor.adoptFd(rawDupFd).close() }
                runCatching { tunFdRef.getAndSet(null)?.close() }
                PersistentLoggers.error(TAG, "attachTun threw, fd closed: ${t.message}")
                runCatching { chainOrchestrator.stop() }
                engineWatchdog.handleEngineFailure(engineId, "attachTun threw: ${t.message}")
                return false
            }
            return when (result) {
                ru.ozero.enginescore.TunAttachResult.Success -> {
                    true
                }
                is ru.ozero.enginescore.TunAttachResult.Failure -> {
                    runCatching { ParcelFileDescriptor.adoptFd(rawDupFd).close() }
                    runCatching { tunFdRef.getAndSet(null)?.close() }
                    PersistentLoggers.error(TAG, "attachTun failed: ${result.reason}")
                    runCatching { chainOrchestrator.stop() }
                    engineWatchdog.handleEngineFailure(engineId, "attachTun: ${result.reason}")
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
            engineWatchdog.handleEngineFailure(engineId, "tunnel code=$code")
            return false
        }
        return true
    }


    private fun engineExtras(): String {
        if (!::chainOrchestrator.isInitialized) return ""
        return runCatching {
            chainOrchestrator.activeEngines()
                .mapNotNull { plugin ->
                    val flow = plugin.stats() as? kotlinx.coroutines.flow.StateFlow<*>
                    val stats = flow?.value as? ru.ozero.enginescore.EngineStats ?: return@mapNotNull null
                    plugin.statsLabel(stats)
                }
                .joinToString(" · ")
        }.getOrDefault("")
    }

    private fun stopVpn() {
        if (!stopping.compareAndSet(false, true)) return
        stopSignal.set(true)
        PersistentLoggers.info(TAG, "stopVpn entry")
        runCatching { tunnelController.onKillswitchReleased() }
        val priorState = tunnelController.state.value
        tunnelController.onDisconnecting()
        startJobRef.getAndSet(null)?.cancel()
        statsLogger.cancel()
        engineWatchdog.cancelWatchers()
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
            statsLogger.cancel()

            val chainStopJob = serviceScope.launch {
                runCatching { chainOrchestrator.stop() }
                    .onFailure { PersistentLoggers.warn(TAG, "chainOrchestrator.stop threw: ${it.message}") }
            }
            val nativeStopThread = Thread({
                runCatching { tunnelGateway.stop() }
                    .onFailure { PersistentLoggers.warn(TAG, "tunnelGateway.stop threw: ${it.message}") }
            }, "ozero-native-stop").also {
                it.isDaemon = true
                it.start()
            }
            val stopStart = System.currentTimeMillis()
            val chainOk = withTimeoutOrNull(PARALLEL_STOP_TIMEOUT_MS) { chainStopJob.join() }
            if (chainOk == null) {
                PersistentLoggers.warn(TAG, "chainOrchestrator.stop hung > ${PARALLEL_STOP_TIMEOUT_MS}ms")
            } else {
                Log.i(TAG, "chainOrchestrator.stop completed")
            }
            val remaining = maxOf(0L, PARALLEL_STOP_TIMEOUT_MS - (System.currentTimeMillis() - stopStart))
            nativeStopThread.join(remaining)
            if (nativeStopThread.isAlive) {
                PersistentLoggers.warn(TAG, "native tunnel stop hung — abandon thread")
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
            if (callStopSelf) stopSelf(latestStartId.get())
            PersistentLoggers.info(TAG, "performShutdown end")
        }
    }

    override fun onRevoke() {
        PersistentLoggers.warn(TAG, "onRevoke — VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        PersistentLoggers.info(TAG, "onDestroy entry")
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
        socketProtector?.let { ru.ozero.enginescore.VpnSocketProtectorHolder.unbind(it) }
        runCatching { healthMonitor.stop() }
        serviceScope.cancel()
        runCatching { tunFdRef.getAndSet(null)?.close() }
        super.onDestroy()
    }
}
