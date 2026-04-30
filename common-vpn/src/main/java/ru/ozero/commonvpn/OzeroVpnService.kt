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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ChainResult
import ru.ozero.enginescore.ChainStep
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.security.SecurityStateHolder
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal var processKiller: ProcessKiller = ProcessKiller { pid -> Process.killProcess(pid) }

    companion object {
        const val ACTION_START = "ru.ozero.vpn.ACTION_START"
        const val ACTION_STOP = "ru.ozero.vpn.ACTION_STOP"
        const val CHANNEL_ID = "ozero_vpn"
        const val NOTIFICATION_ID = 1
        const val TUN_ADDRESS = "10.10.10.10"
        const val TUN_PREFIX_LENGTH = 32
        const val TUN_ADDRESS_V6 = "fd00:ffff:ffff:ffff::1"
        const val TUN_PREFIX_LENGTH_V6 = 64
        const val TUN_DNS = "100.64.0.1"
        const val TUN_MTU = 1500
        private const val SESSION_NAME = "Ozero"
        private const val TAG = "OzeroVpnService"
        private const val CHAIN_START_TIMEOUT_MS = 30_000L
        private const val SHUTDOWN_TIMEOUT_MS = 6_000L
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate before super")
        try {
            super.onCreate()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "super.onCreate threw — Hilt graph failure: ${t.message}")
            throw t
        }
        Log.i(TAG, "onCreate after super (Hilt inject done)")
    }

    private val tunFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val startJobRef = AtomicReference<Job?>(null)
    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} startId=$startId")
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
        if (SecurityStateHolder.isCompromised) {
            stopVpn()
            return
        }
        if (!starting.compareAndSet(false, true)) return
        PersistentLoggers.info(TAG, "startVpn entry")

        val tName = Thread.currentThread().name
        val isMain = android.os.Looper.myLooper() === android.os.Looper.getMainLooper()
        PersistentLoggers.info(TAG, "loadOnce begin thread=$tName main=$isMain")
        runCatching { hev.TProxyService.loadOnce() }
            .onFailure { PersistentLoggers.warn(TAG, "TProxyService.loadOnce threw: ${it.message}") }
        PersistentLoggers.info(TAG, "loadOnce done libraryLoaded=${hev.TProxyService.libraryLoaded}")

        val fd = try {
            buildTunBuilder().establish()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "establish threw: ${t.message}")
            starting.set(false)
            stopVpn()
            return
        }
        if (fd == null) {
            PersistentLoggers.error(TAG, "establish returned null — permission revoked?")
            starting.set(false)
            stopVpn()
            return
        }
        tunFdRef.set(fd)
        PersistentLoggers.info(TAG, "TUN established fd=${fd.fd}")

        val job = serviceScope.launch {
            if (stopping.get()) {
                runCatching { fd.close() }
                tunFdRef.compareAndSet(fd, null)
                starting.set(false)
                return@launch
            }
            try {
                tunnelController.onProbing()
                val chainResult = withTimeoutOrNull(CHAIN_START_TIMEOUT_MS) {
                    try {
                        chainOrchestrator.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi())))
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
                    return@launch
                }
                tunnelController.onConnecting(EngineId.BYEDPI)
                val code = try {
                    tunnelGateway.start(
                        HevTunnelConfig(
                            tunPfd = fd,
                            socksAddress = "127.0.0.1",
                            socksPort = chainResult.finalSocksPort,
                        ),
                    )
                } catch (t: Throwable) {
                    PersistentLoggers.error(TAG, "tunnelGateway.start threw: ${t.message}")
                    runCatching { chainOrchestrator.stop() }
                    stopVpn()
                    return@launch
                }
                if (code != 0) {
                    PersistentLoggers.error(TAG, "tunnel start failed code=$code")
                    runCatching { chainOrchestrator.stop() }
                    tunnelController.onEngineDied(EngineId.BYEDPI, "tunnel code=$code")
                    stopVpn()
                    return@launch
                }
                tunnelController.onEngineStarted(EngineId.BYEDPI, chainResult.finalSocksPort)
                PersistentLoggers.info(TAG, "connected socksPort=${chainResult.finalSocksPort}")
            } finally {
                starting.set(false)
            }
        }
        startJobRef.set(job)
    }

    private fun stopVpn() {
        if (!stopping.compareAndSet(false, true)) return
        PersistentLoggers.info(TAG, "stopVpn entry")
        tunnelController.onDisconnecting()
        startJobRef.getAndSet(null)?.cancel()
        runCatching { tunFdRef.getAndSet(null)?.close() }
        serviceScope.launch { performShutdown() }
    }

    private suspend fun performShutdown() {
        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
            runCatching { tunnelGateway.stop() }
            runCatching { chainOrchestrator.stop() }
        } ?: PersistentLoggers.warn(TAG, "shutdown hung > ${SHUTDOWN_TIMEOUT_MS}ms")
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
    }

    internal fun buildTunBuilder(
        splitConfig: ru.ozero.commonvpn.split.SplitTunnelConfig =
            ru.ozero.commonvpn.split.SplitTunnelConfig(),
    ): Builder {
        val builder = Builder()
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .addDnsServer(TUN_DNS)
            .setMtu(TUN_MTU)
            .setSession(SESSION_NAME)
            .setBlocking(true)
        runCatching { builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6) }
        ru.ozero.commonvpn.split.TunBuilderConfigurator(packageName).apply(builder, splitConfig)
        return builder
    }

    private fun buildNotification(): Notification {
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
                .apply { if (contentIntent != null) setContentIntent(contentIntent) }
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(this)
            .setContentTitle("Ozero VPN активен")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
    }

    override fun onRevoke() {
        Log.i(TAG, "onRevoke — VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy entry")
        if (stopping.compareAndSet(false, true)) serviceScope.launch { performShutdown() }
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
            PersistentLoggers.error(TAG, "startForeground threw: ${t.message}")
            false
        }
    }
}
