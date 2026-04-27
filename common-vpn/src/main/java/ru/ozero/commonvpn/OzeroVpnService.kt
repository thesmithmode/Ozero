package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.commonvpn.pipeline.VpnEnginePipeline
import javax.inject.Inject

@AndroidEntryPoint
class OzeroVpnService : android.net.VpnService() {

    @Inject lateinit var pipeline: VpnEnginePipeline

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        private const val SHUTDOWN_TIMEOUT_MS = 3_000L
        private const val SHUTDOWN_JOIN_TIMEOUT_MS = 3_500L
    }

    private val tunFdRef = AtomicReference<ParcelFileDescriptor?>(null)
    private val startJobRef = AtomicReference<Job?>(null)

    private val starting = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START, null -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (!starting.compareAndSet(false, true)) {
            Log.w(TAG, "startVpn ignored — уже запущен/запускается")
            return
        }
        Log.i(TAG, "startVpn")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        val fd = buildTunBuilder().establish()
        if (fd == null) {
            Log.e(TAG, "TUN не установлен — VpnService.prepare не выдан?")
            starting.set(false)
            stopVpn()
            return
        }
        tunFdRef.set(fd)
        Log.i(TAG, "TUN established fd=${fd.fd}")
        val job = serviceScope.launch {
            try {
                val result = withTimeoutOrNull(PIPELINE_START_TIMEOUT_MS) {
                    try {
                        pipeline.start(tunFd = fd.fd)
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Log.e(TAG, "pipeline.start threw", t)
                        null
                    }
                }
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
        serviceScope.launch {
            val finished = withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
                runCatching { pipeline.stop() }
                    .onFailure { Log.w(TAG, "pipeline.stop threw", it) }
            }
            if (finished == null) {
                Log.w(TAG, "pipeline.stop не завершилась за ${SHUTDOWN_TIMEOUT_MS}ms — закрываем fd")
            }
            withContext(Dispatchers.Main) {
                closeTunFd()
                starting.set(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    internal fun buildTunBuilder(
        splitConfig: ru.ozero.commonvpn.split.SplitTunnelConfig =
            ru.ozero.commonvpn.split.SplitTunnelConfig(),
    ): Builder {
        val builder = Builder()
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            .addDnsServer(TUN_DNS)
            .setMtu(TUN_MTU)
            .setSession(SESSION_NAME)
            .setBlocking(true)
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
                    runCatching {
                        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { pipeline.stop() }
                    }.onFailure { Log.w(TAG, "pipeline.stop in onDestroy threw", it) }
                }
            }, "OzeroVpn-shutdown")
            shutdown.start()
            shutdown.join(SHUTDOWN_JOIN_TIMEOUT_MS)
            if (shutdown.isAlive) {
                Log.w(TAG, "pipeline.stop не завершилась за ${SHUTDOWN_JOIN_TIMEOUT_MS}ms — продолжаем cleanup")
            }
        }
        serviceScope.cancel()
        closeTunFd()
    }

    private fun closeTunFd() {
        tunFdRef.getAndSet(null)?.close()
    }
}
