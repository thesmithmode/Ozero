package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        private const val STATS_SAMPLE_INTERVAL_MS = 1_000L
        private const val STATS_NOTIFY_LOG_EVERY = 5
        private const val SETTINGS_READ_TIMEOUT_MS = 1_500L
    }

    override fun onCreate() {
        PersistentLoggers.info(TAG, "onCreate before super")
        try {
            super.onCreate()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "super.onCreate threw — Hilt graph failure: ${t.message}", t)
            throw t
        }
        PersistentLoggers.info(TAG, "onCreate after super (Hilt inject done)")
    }

    private val tunFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val startJobRef = AtomicReference<Job?>(null)
    private val statsJobRef = AtomicReference<Job?>(null)
    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val stopSignal = AtomicBoolean(false)

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
                ACTION_STOP -> stopVpn()
                ACTION_START, null -> startVpn()
            }
            START_STICKY
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "onStartCommand threw: ${t.message}")
            runCatching { stopVpn() }
            START_NOT_STICKY
        }
    }

    private fun startVpn() {
        if (stopping.get()) {
            stopVpn()
            return
        }
        if (!starting.compareAndSet(false, true)) return
        stopSignal.set(false)
        PersistentLoggers.info(TAG, "startVpn entry")

        val tName = Thread.currentThread().name
        val isMain = android.os.Looper.myLooper() === android.os.Looper.getMainLooper()
        PersistentLoggers.info(TAG, "loadOnce begin thread=$tName main=$isMain")
        runCatching { hev.TProxyService.loadOnce() }
            .onFailure { PersistentLoggers.warn(TAG, "TProxyService.loadOnce threw: ${it.message}") }
        PersistentLoggers.info(TAG, "loadOnce done libraryLoaded=${hev.TProxyService.libraryLoaded}")

        val job = serviceScope.launch {
            try {
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

        val fd = establishTun(
            splitConfig,
            ipv6 = settings?.ipv6Enabled ?: false,
            customDns = settings?.customDnsServers.orEmpty(),
        ) ?: return
        if (stopping.get()) {
            runCatching { fd.close() }
            tunFdRef.compareAndSet(fd, null)
            return
        }

        tunnelController.onProbing()
        val byedpiConfig = EngineConfig.ByeDpi(
            args = settings?.byedpiWinningArgs?.takeIf { it.isNotBlank() }
                ?: EngineConfig.ByeDpi().args,
            hostsMode = settings?.hostsMode ?: ru.ozero.enginescore.settings.HostsMode.DISABLED,
            hosts = settings?.hosts.orEmpty(),
        )
        val chainResult = startChain(byedpiConfig) ?: return
        if (!startNativeTunnel(fd, chainResult.finalSocksPort)) return

        tunnelController.onEngineStarted(EngineId.BYEDPI, chainResult.finalSocksPort)
        val nowMs = System.currentTimeMillis()
        sessionStartMsRef.set(nowMs)
        sessionIdRef.set(
            runCatching { sessionStatsRecorder.startSession(EngineId.BYEDPI.name, nowMs) }.getOrDefault(-1L),
        )
        runCatching { healthMonitor.start(chainResult.finalSocksPort) }
        startStatsLogger()
    }

    private fun establishTun(
        splitConfig: ru.ozero.commonvpn.split.SplitTunnelConfig,
        ipv6: Boolean,
        customDns: List<String>,
    ): ParcelFileDescriptor? {
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
        PersistentLoggers.info(TAG, "TUN established fd=${fd.fd}")
        return fd
    }

    private suspend fun startChain(byedpiConfig: EngineConfig.ByeDpi): ChainResult.Success? {
        val chainResult = withTimeoutOrNull(CHAIN_START_TIMEOUT_MS) {
            try {
                chainOrchestrator.start(listOf(ChainStep(EngineId.BYEDPI, byedpiConfig)))
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "chain.start threw: ${t.message}")
                null
            }
        }
        PersistentLoggers.info(TAG, "chain result=$chainResult")
        if (chainResult !is ChainResult.Success) {
            tunnelController.onEngineDied(EngineId.BYEDPI, chainResult?.toString() ?: "timeout")
            stopVpn()
            return null
        }
        tunnelController.onConnecting(EngineId.BYEDPI)
        return chainResult
    }

    private fun startNativeTunnel(fd: ParcelFileDescriptor, socksPort: Int): Boolean {
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
            tunnelController.onEngineDied(EngineId.BYEDPI, "tunnel code=$code")
            stopVpn()
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
                    val raw = runCatching { hev.TProxyService.TProxyGetStats() }.getOrNull()
                    if (raw == null || raw.size < 4) {
                        PersistentLoggers.warn(TAG, "TunnelStats: TProxyGetStats unavailable")
                        continue
                    }
                    val txPackets = raw[0]
                    val txBytes = raw[1]
                    val rxPackets = raw[2]
                    val rxBytes = raw[3]
                    val snapshot = TunnelStats(
                        txPackets = txPackets,
                        txBytes = txBytes,
                        rxPackets = rxPackets,
                        rxBytes = rxBytes,
                        timestampMs = System.currentTimeMillis(),
                    )
                    tunnelController.updateStats(snapshot)
                    tickCount++
                    if (tickCount % STATS_NOTIFY_LOG_EVERY == 0) {
                        val dTx = txBytes - prevTx
                        val dRx = rxBytes - prevRx
                        PersistentLoggers.info(
                            TAG,
                            "TunnelStats tx=${BytesFormatter.humanReadable(txBytes)}/$txPackets pkts " +
                                "rx=${BytesFormatter.humanReadable(rxBytes)}/$rxPackets pkts " +
                                "Δtx=${BytesFormatter.humanReadable(dTx)} Δrx=${BytesFormatter.humanReadable(dRx)}",
                        )
                        updateNotificationWithStats(txBytes, rxBytes)
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

    private fun updateNotificationWithStats(txBytes: Long, rxBytes: Long) {
        if (stopSignal.get()) return
        runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val text = "↓ ${BytesFormatter.humanReadable(rxBytes)}  ↑ ${BytesFormatter.humanReadable(txBytes)}"
            val n = buildNotification(text)
            nm.notify(NOTIFICATION_ID, n)
        }.onFailure { PersistentLoggers.warn(TAG, "updateNotificationWithStats: ${it.message}") }
    }

    private fun stopVpn() {
        if (!stopping.compareAndSet(false, true)) return
        stopSignal.set(true)
        PersistentLoggers.info(TAG, "stopVpn entry")
        val priorState = tunnelController.state.value
        tunnelController.onDisconnecting()
        startJobRef.getAndSet(null)?.cancel()
        statsJobRef.getAndSet(null)?.cancel()
        runCatching { healthMonitor.stop() }
        val endStatus = if (priorState is TunnelState.Failed) {
            SessionStatsRecorder.Status.FAILED
        } else {
            SessionStatsRecorder.Status.DISCONNECTED
        }
        recordSessionEnd(endStatus)
        serviceScope.launch { performShutdown() }
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

    private suspend fun performShutdown() {
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
                PersistentLoggers.info(TAG, "chainOrchestrator.stop completed")
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
                PersistentLoggers.info(TAG, "native tunnel stop completed")
            }

            runCatching { tunFdRef.getAndSet(null)?.close() }
                .onFailure { PersistentLoggers.warn(TAG, "tunFd.close threw: ${it.message}") }
        } finally {
            tunnelController.reset()
            starting.set(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            stopping.set(false)
            PersistentLoggers.info(TAG, "performShutdown end")
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
        if (ipv6Enabled) {
            builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            builder.addRoute("::", 0)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ozero VPN", NotificationManager.IMPORTANCE_LOW),
            )
            return Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ozero VPN активен")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .apply {
                    if (contentText != null) setContentText(contentText)
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
            .apply {
                if (contentText != null) setContentText(contentText)
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
        if (stopping.compareAndSet(false, true)) serviceScope.launch { performShutdown() }
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
            PersistentLoggers.info(TAG, "startForeground OK")
            true
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "startForeground threw: ${t.message}", t)
            false
        }
    }
}
