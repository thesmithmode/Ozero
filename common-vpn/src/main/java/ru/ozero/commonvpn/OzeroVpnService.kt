package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.commonvpn.pipeline.VpnEnginePipeline
import ru.ozero.coreapi.PersistentLoggers
import ru.ozero.security.SecurityStateHolder
import javax.inject.Inject

fun interface ProcessKiller {
    fun kill(pid: Int)
}

@AndroidEntryPoint
class OzeroVpnService : android.net.VpnService() {

    @Inject lateinit var pipeline: VpnEnginePipeline

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
        const val TUN_DNS = "1.1.1.1"
        const val TUN_MTU = 1500
        private const val SESSION_NAME = "Ozero"
        private const val TAG = "OzeroVpnService"
        private const val PIPELINE_START_TIMEOUT_MS = 30_000L
        private const val SHUTDOWN_JOIN_TIMEOUT_MS = 2_500L
    }

    override fun onCreate() {
        runCatching { PersistentLoggers.instance?.info(TAG, "onCreate before super") }
        try {
            super.onCreate()
        } catch (t: Throwable) {
            runCatching { Log.e(TAG, "super.onCreate threw — Hilt graph failure", t) }
            runCatching { PersistentLoggers.instance?.error(TAG, "super.onCreate threw — Hilt graph failure", t) }
            throw t
        }
        runCatching { PersistentLoggers.instance?.info(TAG, "onCreate after super (Hilt inject done)") }
    }

    private val tunFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val startJobRef = AtomicReference<Job?>(null)

    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PersistentLoggers.instance?.info(
            TAG,
            "onStartCommand entry action=${intent?.action} flags=$flags startId=$startId",
        )
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        val foregroundOk = enterForegroundOrLog()
        if (!foregroundOk) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return try {
            if (!::pipeline.isInitialized) {
                Log.e(TAG, "pipeline not injected — Hilt graph failure")
                PersistentLoggers.instance?.error(TAG, "pipeline not injected — Hilt graph failure")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            when (intent?.action) {
                ACTION_STOP -> stopVpn()
                ACTION_START, null -> startVpn()
            }
            START_STICKY
        } catch (t: Throwable) {
            Log.e(TAG, "onStartCommand threw", t)
            PersistentLoggers.instance?.error(TAG, "onStartCommand threw", t)
            runCatching { stopVpn() }
            START_NOT_STICKY
        }
    }

    private fun startVpn() {
        if (stopping.get()) {
            Log.w(TAG, "startVpn ignored — идет остановка предыдущей сессии")
            stopVpn()
            return
        }
        if (SecurityStateHolder.isCompromised) {
            Log.w(TAG, "startVpn refused — security compromised: ${SecurityStateHolder.compromised.value}")
            PersistentLoggers.instance?.warn(
                TAG,
                "startVpn refused — security compromised: ${SecurityStateHolder.compromised.value}",
            )
            stopVpn()
            return
        }
        if (!starting.compareAndSet(false, true)) {
            Log.w(TAG, "startVpn ignored — уже запущен/запускается")
            return
        }
        Log.i(TAG, "startVpn")
        runCatching {
            val tName = Thread.currentThread().name
            val isMain = android.os.Looper.myLooper() === android.os.Looper.getMainLooper()
            val t0 = System.nanoTime()
            Log.i(TAG, "preload begin thread=$tName main=$isMain")
            hev.TProxyService.loadOnce()
            val dtMs = (System.nanoTime() - t0) / 1_000_000
            Log.i(
                TAG,
                "preload done dt=${dtMs}ms libraryLoaded=${hev.TProxyService.libraryLoaded} " +
                    "loadError=${hev.TProxyService.loadError}",
            )
        }.onFailure {
            Log.w(TAG, "TProxyService preload threw", it)
            PersistentLoggers.instance?.warn(TAG, "TProxyService preload threw", it)
        }
        val fd = try {
            buildTunBuilder().establish()
        } catch (t: Throwable) {
            Log.e(TAG, "VpnService.Builder.establish threw", t)
            PersistentLoggers.instance?.error(TAG, "Builder.establish threw", t)
            starting.set(false)
            stopVpn()
            return
        }
        if (fd == null) {
            Log.e(TAG, "TUN не установлен — VpnService.prepare не выдан?")
            PersistentLoggers.instance?.error(TAG, "establish returned null — permission revoked?")
            starting.set(false)
            stopVpn()
            return
        }
        tunFdRef.set(fd)
        Log.i(TAG, "TUN established fd=${fd.fd}")
        val job = serviceScope.launch {
            if (stopping.get()) {
                starting.set(false)
                return@launch
            }
            try {
                val result = withTimeoutOrNull(PIPELINE_START_TIMEOUT_MS) {
                    try {
                        pipeline.start(tunPfd = fd)
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Log.e(TAG, "pipeline.start threw", t)
                        null
                    }
                }
                Log.i(TAG, "pipeline.start result=$result")
                if (result !is VpnEnginePipeline.Result.Connected) {
                    Log.w(TAG, "pipeline не подключился: $result, останавливаем")
                    stopVpn()
                }
            } finally {
                starting.set(false)
            }
        }
        startJobRef.set(job)
    }

    private fun stopVpn() {
        if (!stopping.compareAndSet(false, true)) {
            Log.w(TAG, "stopVpn ignored — уже останавливается")
            return
        }
        Log.i(TAG, "stopVpn")
        startJobRef.getAndSet(null)?.cancel()
        val fdToClose = tunFdRef.getAndSet(null)
        serviceScope.launch {
            val shutdown = Thread({
                runBlocking {
                    runCatching { pipeline.stop() }
                        .onFailure { Log.w(TAG, "pipeline.stop threw", it) }
                }
            }, "OzeroVpn-stop").apply { isDaemon = true }
            shutdown.start()
            shutdown.join(SHUTDOWN_JOIN_TIMEOUT_MS)
            closeTunFd(fdToClose)
            if (shutdown.isAlive) {
                Log.w(
                    TAG,
                    "pipeline.stop hung > ${SHUTDOWN_JOIN_TIMEOUT_MS}ms — leak daemon, продолжаем stopSelf",
                )
                PersistentLoggers.instance?.warn(
                    TAG,
                    "pipeline.stop hung > ${SHUTDOWN_JOIN_TIMEOUT_MS}ms — leak daemon, no force-kill",
                )
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    starting.set(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                } finally {
                    stopping.set(false)
                }
            }
        }
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
            .onFailure { Log.w(TAG, "IPv6 TUN address rejected, IPv4-only", it) }
        ru.ozero.commonvpn.split.TunBuilderConfigurator(packageName)
            .apply(builder, splitConfig)
        return builder
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ozero VPN",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            return Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ozero VPN активен")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            return Notification.Builder(this)
                .setContentTitle("Ozero VPN активен")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "onRevoke")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (stopping.compareAndSet(false, true)) {
            val shutdown = Thread({
                runBlocking {
                    runCatching { pipeline.stop() }
                        .onFailure { Log.w(TAG, "pipeline.stop in onDestroy threw", it) }
                }
            }, "OzeroVpn-shutdown").apply { isDaemon = true }
            shutdown.start()
            shutdown.join(SHUTDOWN_JOIN_TIMEOUT_MS)
            if (shutdown.isAlive) {
                Log.w(TAG, "pipeline.stop hung in onDestroy > ${SHUTDOWN_JOIN_TIMEOUT_MS}ms — leak daemon")
                PersistentLoggers.instance?.warn(
                    TAG,
                    "pipeline.stop hung in onDestroy > ${SHUTDOWN_JOIN_TIMEOUT_MS}ms — leak daemon",
                )
            }
        }
        serviceScope.cancel()
        val tunFd = tunFdRef.getAndSet(null)
        tunFd?.close()
    }

    private fun closeTunFd(fd: ParcelFileDescriptor? = tunFdRef.getAndSet(null)) {
        fd?.close()
    }

    private fun enterForegroundOrLog(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val notification = buildNotification()
                val specialUse = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                val fallback = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                try {
                    startForeground(NOTIFICATION_ID, notification, specialUse)
                } catch (t: Throwable) {
                    Log.w(TAG, "startForeground specialUse failed → fallback to manifest type", t)
                    PersistentLoggers.instance?.warn(
                        TAG,
                        "startForeground specialUse failed → fallback to manifest type",
                        t,
                    )
                    startForeground(NOTIFICATION_ID, notification, fallback)
                }
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            Log.i(TAG, "startForeground OK (early)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground threw on entry", t)
            PersistentLoggers.instance?.error(TAG, "startForeground threw on entry", t)
            false
        }
    }
}
