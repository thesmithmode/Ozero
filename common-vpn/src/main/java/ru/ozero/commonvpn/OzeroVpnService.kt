package ru.ozero.commonvpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_RESTART_RUNTIME_CONFIG
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.settings.TrafficMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

fun interface ProcessKiller {
    fun kill(pid: Int)
}

@AndroidEntryPoint
class OzeroVpnService : android.net.VpnService() {

    @Inject lateinit var chainOrchestrator: ChainOrchestrator

    @Inject lateinit var tunnelGateway: HevTunnelGateway

    @Inject lateinit var tunnelController: TunnelController

    @Inject lateinit var settingsRepository: ru.ozero.enginescore.settings.SettingsRepository

    @Inject lateinit var sessionStatsRecorder: SessionStatsRecorder

    @Inject lateinit var splitTunnelRulesProvider: SplitTunnelRulesProvider

    @Inject lateinit var healthMonitor: HealthMonitor

    @Inject lateinit var enginePlugins: Set<@JvmSuppressWildcards EnginePlugin>

    @Inject lateinit var runtimeFailureRouter: RuntimeFailureRouter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionIdRef = AtomicReference<Long>(-1L)
    private val sessionStartMsRef = AtomicReference<Long>(0L)

    internal var processKiller: ProcessKiller = ProcessKiller { pid -> Process.killProcess(pid) }

    private val notificationFactory: OzeroNotificationFactory by lazy {
        OzeroNotificationFactory(this, OzeroVpnService::class.java)
    }
    private val tunBuilderHelper: TunBuilderHelper by lazy { TunBuilderHelper(this) }
    private val statsLogger: TunnelStatsLogger by lazy {
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
    private val startSequence: StartSequenceCoordinator by lazy {
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
    private val engineWatchdog: EngineWatchdogCoordinator by lazy {
        EngineWatchdogCoordinator(
            scope = serviceScope,
            healthMonitor = healthMonitor,
            enginePlugins = enginePlugins,
            tunnelController = tunnelController,
            chainOrchestrator = chainOrchestrator,
            notificationFactory = notificationFactory,
            tunFdRef = tunFdRef,
            lockdownStartupFdRef = lockdownStartupFdRef,
            statsJobRef = statsJobRef,
            stopping = stopping,
            starting = starting,
            killswitchProvider = { killswitchCached },
            stopVpnRequest = { stopVpn() },
        )
    }
    private val shutdownCoord: ShutdownCoordinator by lazy {
        ShutdownCoordinator(
            scope = serviceScope,
            deps = ShutdownCollaborators(
                tunnelController = tunnelController,
                healthMonitor = healthMonitor,
                chainOrchestrator = chainOrchestrator,
                tunnelGateway = tunnelGateway,
                statsLogger = statsLogger,
                engineWatchdog = engineWatchdog,
                sessionStatsRecorder = sessionStatsRecorder,
            ),
            state = ShutdownState(
                tunFdRef = tunFdRef,
                tunIfaceNameRef = tunIfaceNameRef,
                lockdownStartupFdRef = lockdownStartupFdRef,
                sessionStartMsRef = sessionStartMsRef,
                sessionIdRef = sessionIdRef,
                startJobRef = startJobRef,
                shutdownJobRef = shutdownJobRef,
                starting = starting,
                stopping = stopping,
                stopSignal = stopSignal,
            ),
            latestStartIdProvider = { latestStartId.get() },
            stopForegroundRequest = {
                stopForeground(STOP_FOREGROUND_REMOVE)
            },
            stopSelfRequest = { id -> stopSelf(id) },
        )
    }

    companion object {
        const val ACTION_START = "ru.ozero.vpn.ACTION_START"
        const val ACTION_STOP = "ru.ozero.vpn.ACTION_STOP"
        const val ACTION_RESTART_RUNTIME_CONFIG = "ru.ozero.vpn.ACTION_RESTART_RUNTIME_CONFIG"
        const val TUN_ADDRESS = TunBuilderHelper.TUN_ADDRESS
        const val TUN_PREFIX_LENGTH = TunBuilderHelper.TUN_PREFIX_LENGTH
        const val TUN_ADDRESS_V6 = TunBuilderHelper.TUN_ADDRESS_V6
        const val TUN_PREFIX_LENGTH_V6 = TunBuilderHelper.TUN_PREFIX_LENGTH_V6
        val TUN_DNS_SERVERS: List<String> = TunBuilderHelper.TUN_DNS_SERVERS
        private const val TAG = "OzeroVpnService"
        private const val SHUTDOWN_JOIN_TIMEOUT_MS = 7_000L
        private const val ON_DESTROY_SHUTDOWN_TIMEOUT_MS = 5_000L
        internal const val REVOKE_KILL_DELAY_MS = 1_000L
        internal const val EXTERNAL_VPN_RELEASE_DELAY_MS = 750L
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
        runtimeFailureRouter.bind(runtimeFailureHandler)
        acquireLocks()
        observeKillswitchSetting()
        PersistentLoggers.info(TAG, "onCreate after super (Hilt inject done)")
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ozero::vpn-service")
            ?.apply { acquire() }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wm?.createWifiLock(mode, "ozero::vpn-service")
            ?.apply { acquire() }
        PersistentLoggers.debug(TAG, "locks acquired wake=${wakeLock != null} wifi=${wifiLock != null}")
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        PersistentLoggers.debug(TAG, "locks released")
    }

    private fun observeKillswitchSetting() {
        settingsRepository.settings
            .map { it.trafficMode == TrafficMode.TUN && it.killswitchEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                killswitchCached = enabled
                PersistentLoggers.debug(TAG, "effective killswitch live update: $enabled")
            }
            .launchIn(serviceScope)
    }

    private var socketProtector: ru.ozero.enginescore.VpnSocketProtector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val tunFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val tunIfaceNameRef = AtomicReference<String?>(null)
    private val startJobRef = AtomicReference<Job?>(null)
    private val statsJobRef = AtomicReference<Job?>(null)
    private val shutdownJobRef = AtomicReference<Job?>(null)
    private val lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val runtimeConfigRestartInProgress = AtomicBoolean(false)
    private val runtimeConfigRestartCancelled = AtomicBoolean(false)
    private val stopSignal = AtomicBoolean(false)
    private val latestStartId = AtomicInteger(-1)

    @Volatile
    private var killswitchCached: Boolean = false

    private val runtimeFailureHandler: (ru.ozero.enginescore.EngineId, String) -> Unit by lazy {
        { engineId, reason ->
            engineWatchdog.handleEngineFailure(engineId, reason)
            Unit
        }
    }

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
                ACTION_RESTART_RUNTIME_CONFIG -> restartVpn()
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
        runtimeConfigRestartCancelled.set(false)
        PersistentLoggers.info(TAG, "startVpn entry")
        val externalVpnActive = logActiveExternalVpn()
        runCatching { tunFdRef.getAndSet(null)?.close() }
            .onFailure { PersistentLoggers.warn(TAG, "startVpn: stale tunFd close threw: ${it.message}") }
        tunnelController.onKillswitchReleased()

        val tName = Thread.currentThread().name
        val isMain = android.os.Looper.myLooper() === android.os.Looper.getMainLooper()
        PersistentLoggers.debug(TAG, "loadOnce begin thread=$tName main=$isMain")
        // SENTINEL [OzeroVpnServiceLifecycleTest]: loadOnce() для libhev-socks5-tunnel — main thread,
        // до coroutine-старта ниже. Background-load даёт UnsatisfiedLinkError на старте hev.
        runCatching { hev.TProxyService.loadOnce() }
            .onFailure { PersistentLoggers.warn(TAG, "TProxyService.loadOnce threw: ${it.message}") }
        PersistentLoggers.debug(TAG, "loadOnce done libraryLoaded=${hev.TProxyService.libraryLoaded}")

        val job = serviceScope.launch {
            try {
                shutdownJobRef.getAndSet(null)?.let { prev ->
                    PersistentLoggers.debug(TAG, "startVpn: ожидание завершения предыдущего shutdown")
                    runCatching {
                        withTimeoutOrNull(SHUTDOWN_JOIN_TIMEOUT_MS) { prev.join() }
                    }
                    PersistentLoggers.debug(TAG, "startVpn: предыдущий shutdown завершён → продолжаем старт")
                }
                if (stopping.get()) {
                    PersistentLoggers.warn(TAG, "startVpn: stopping всё ещё активен после join — отказ старта")
                    return@launch
                }
                if (externalVpnActive) {
                    PersistentLoggers.warn(
                        TAG,
                        "external VPN был активен — даём ОС ${EXTERNAL_VPN_RELEASE_DELAY_MS}ms " +
                            "на освобождение VPN slot до establish() (избегаем race с onRevoke того VPN)",
                    )
                    runCatching {
                        kotlinx.coroutines.delay(EXTERNAL_VPN_RELEASE_DELAY_MS)
                    }
                }
                startSequence.run()
            } finally {
                starting.set(false)
                runtimeConfigRestartInProgress.set(false)
            }
        }
        startJobRef.set(job)
    }

    private fun engineExtras(): String {
        if (!::chainOrchestrator.isInitialized) return ""
        return runCatching {
            chainOrchestrator.activeEngines()
                .map { it.id.displayName }
                .joinToString(" + ")
        }.getOrDefault("")
    }

    private fun stopVpn() {
        if (runtimeConfigRestartInProgress.get()) {
            runtimeConfigRestartCancelled.set(true)
        }
        shutdownCoord.stopVpn()
    }

    private fun restartVpn() {
        val shutdownStarted = stopping.compareAndSet(false, true)
        if (!shutdownStarted) return
        runtimeConfigRestartCancelled.set(false)
        runtimeConfigRestartInProgress.set(true)
        stopping.set(false)
        shutdownCoord.stopVpn(callStopSelf = false)
        serviceScope.launch {
            shutdownJobRef.get()?.let { job ->
                runCatching { withTimeoutOrNull(SHUTDOWN_JOIN_TIMEOUT_MS) { job.join() } }
            }
            val restartCancelled = runtimeConfigRestartCancelled.get()
            if (
                !restartCancelled &&
                !stopping.get() &&
                notificationFactory.enterForeground(this@OzeroVpnService)
            ) {
                startVpn()
            } else {
                runtimeConfigRestartInProgress.set(false)
                runtimeConfigRestartCancelled.set(false)
                stopSelf(latestStartId.get())
            }
        }
    }

    private fun logActiveExternalVpn(): Boolean {
        return runCatching {
            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return@runCatching false
            val networks = cm.allNetworks
            var detected = false
            val myUid = android.os.Process.myUid()
            for (n in networks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue
                if (android.os.Build.VERSION.SDK_INT >= 29 && caps.ownerUid == myUid) continue
                detected = true
                PersistentLoggers.warn(
                    TAG,
                    "external VPN active at start — caps=${caps.toString().take(120)}",
                )
            }
            detected
        }.getOrDefault(false)
    }

    // SENTINEL [project_vpn_slot_onrevoke_kill]: killProcess только в onRevoke (юзер запустил другой VPN).
    // Никогда не убивать процесс в onDestroy/stopVpn — это сломает re-connect и swipe-close persistence.
    override fun onRevoke() {
        PersistentLoggers.warn(
            TAG,
            "onRevoke — VPN permission revoked, will kill own process after " +
                "${REVOKE_KILL_DELAY_MS}ms to release Android VPN slot for other VPN apps",
        )
        stopVpn()
        super.onRevoke()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            {
                runCatching { tunFdRef.getAndSet(null)?.close() }
                PersistentLoggers.warn(TAG, "onRevoke kill pid=${android.os.Process.myPid()} — slot release")
                processKiller.kill(android.os.Process.myPid())
            },
            REVOKE_KILL_DELAY_MS,
        )
    }

    override fun onDestroy() {
        PersistentLoggers.info(TAG, "onDestroy entry")
        runBlocking(Dispatchers.IO) {
            val ok = withTimeoutOrNull(ON_DESTROY_SHUTDOWN_TIMEOUT_MS) {
                val inFlight = shutdownJobRef.get()
                if (inFlight != null) {
                    inFlight.join()
                } else if (stopping.compareAndSet(false, true)) {
                    shutdownCoord.performShutdown(callStopSelf = false)
                }
            }
            if (ok == null) {
                PersistentLoggers.warn(
                    TAG,
                    "onDestroy shutdown timeout > ${ON_DESTROY_SHUTDOWN_TIMEOUT_MS}ms — abandon",
                )
            }
        }
        socketProtector?.let { ru.ozero.enginescore.VpnSocketProtectorHolder.unbind(it) }
        runtimeFailureRouter.unbind(runtimeFailureHandler)
        runCatching { healthMonitor.stop() }
        releaseLocks()
        serviceScope.cancel()
        runCatching { tunFdRef.getAndSet(null)?.close() }
        super.onDestroy()
    }
}
