package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP
import ru.ozero.commonvpn.pipeline.VpnEnginePipeline
import javax.inject.Inject

@AndroidEntryPoint
class OzeroVpnService : android.net.VpnService() {

    @Inject lateinit var pipeline: VpnEnginePipeline

    /**
     * Service-scoped coroutine для async операций pipeline (probe + engine.start
     * могут занять секунды). SupervisorJob — failure одного launch не валит scope.
     * Отменяется в onDestroy чтобы pipeline.start не выполнился после stop сервиса.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_START = "ru.ozero.vpn.ACTION_START"
        const val ACTION_STOP = "ru.ozero.vpn.ACTION_STOP"
        const val CHANNEL_ID = "ozero_vpn"
        const val NOTIFICATION_ID = 1
        const val TUN_ADDRESS = "10.10.10.10"
        const val TUN_PREFIX_LENGTH = 32
        // IPv6 ULA (RFC 4193) для TUN: должен быть назначен иначе VpnService.Builder
        // не маршрутизирует IPv6 в туннель и весь IPv6-трафик утекает мимо VPN.
        const val TUN_ADDRESS_V6 = "fd00:ffff:ffff:ffff::1"
        const val TUN_PREFIX_LENGTH_V6 = 64
        const val TUN_DNS = "127.0.0.1"
        const val TUN_MTU = 1500
        private const val SESSION_NAME = "Ozero"
        private const val TAG = "OzeroVpnService"
    }

    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        // При null intent (восстановление после OOM со START_STICKY) поднимаем VPN
        // заново — иначе foreground notification потеряется и сервис зависнет мёртвым.
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START, null -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        Log.i(TAG, "startVpn")
        // Foreground запускаем СРАЗУ — Android 8+ имеет 5-секундный deadline,
        // pipeline.start (probe + engine.start) может превысить его.
        startForeground(NOTIFICATION_ID, buildNotification())
        val fd = buildTunBuilder().establish()
        if (fd == null) {
            Log.e(TAG, "TUN не установлен — VpnService.prepare не выдан?")
            stopVpn()
            return
        }
        tunFd = fd
        Log.i(TAG, "TUN established fd=${fd.fd}")
        serviceScope.launch {
            val result = runCatching { pipeline.start(tunFd = fd.fd) }
                .onFailure { Log.e(TAG, "pipeline.start threw", it) }
                .getOrNull()
            if (result !is VpnEnginePipeline.Result.Connected) {
                Log.w(TAG, "pipeline не подключился: $result, останавливаем")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "stopVpn")
        // ВСЁ последовательно в одной корутине: pipeline.stop() ДОЛЖНА завершиться
        // ДО закрытия tunFd, иначе hev-tunnel читает из уже закрытого fd → native crash.
        // stopForeground/stopSelf тоже после остановки pipeline, чтобы Service не был
        // убит планировщиком до завершения cleanup.
        serviceScope.launch {
            runCatching { pipeline.stop() }
                .onFailure { Log.w(TAG, "pipeline.stop threw", it) }
            withContext(Dispatchers.Main) {
                tunFd?.close()
                tunFd = null
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

    override fun onDestroy() {
        super.onDestroy()
        // Kill-switch гарантия: при force-stop / OOM-kill системы pipeline должен быть
        // остановлен ДО закрытия fd, иначе native код hev-tunnel пишет в закрытый fd.
        // runBlocking на main thread допустим — onDestroy уже синхронный teardown.
        runCatching {
            runBlocking { pipeline.stop() }
        }.onFailure { Log.w(TAG, "pipeline.stop in onDestroy threw", it) }
        serviceScope.cancel()
        tunFd?.close()
        tunFd = null
    }
}
