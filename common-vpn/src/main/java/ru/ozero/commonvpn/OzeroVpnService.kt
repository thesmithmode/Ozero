package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_START
import ru.ozero.commonvpn.OzeroVpnService.Companion.ACTION_STOP

class OzeroVpnService : android.net.VpnService() {

    companion object {
        const val ACTION_START = "ru.ozero.vpn.ACTION_START"
        const val ACTION_STOP = "ru.ozero.vpn.ACTION_STOP"
        const val CHANNEL_ID = "ozero_vpn"
        const val NOTIFICATION_ID = 1
        const val TUN_ADDRESS = "10.10.10.10"
        const val TUN_PREFIX_LENGTH = 32
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
        startForeground(NOTIFICATION_ID, buildNotification())
        tunFd = buildTunBuilder().establish()
        Log.i(TAG, "TUN established fd=${tunFd?.fd}")
    }

    private fun stopVpn() {
        Log.i(TAG, "stopVpn")
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

    internal fun buildTunBuilder(): Builder =
        Builder()
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(TUN_DNS)
            .setMtu(TUN_MTU)
            .setSession(SESSION_NAME)
            .setBlocking(true)

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
        tunFd?.close()
        tunFd = null
    }
}
