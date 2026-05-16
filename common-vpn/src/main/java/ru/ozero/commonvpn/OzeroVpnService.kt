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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.PersistentLoggers
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
    private val startSequence by lazy {
        StartSequenceCoordinator(
            packageName = packageName,
            deps = StartSequenceCollaborators(
                enginePlugins = enginePlugins,
                chainOrchestrator = chainOrchestrator,
                tunnelController = tunnelController,
                tunnelGateway = tunnelGateway,
                healthMonitor = healthMonitor,
                tunBuilderHelper = tunBuilderHelper,
                engineWatchdog = engineWatchdog,
                statsLogger = statsLogger,
                splitTunnelRulesProvider = splitTunnelRulesProvider,
                settingsRepository = settingsRepository,
                sessionStatsRecorder = sessionStatsRecorder,
            ),
            state = StartSequenceState(
                tunFdRef = tunFdRef,
                tunIfaceNameRef = tunIfaceNameRef,
                lockdownStartupFdRef = lockdownStartupFdRef,
                sessionStartMsRef = sessionStartMsRef,
                sessionIdRef = sessionIdRef,
                stopping = stopping,
            ),
            killswitchSetter = { killswitchCached = it },
            stopVpnRequest = { stopVpn() },
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
        private const val PARALLEL_STOP_TIMEOUT_MS = 4_000L
        private const val SHUTDOWN_JOIN_TIMEOUT_MS = 7_000L
        private const val ON_DESTROY_SHUTDOWN_TIMEOUT_MS = 5_000L
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
                startSequence.run()
            } finally {
                starting.set(false)
            }
        }
        startJobRef.set(job)
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
