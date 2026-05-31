package ru.ozero.app.ui.strategy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.ozero.app.R

class StrategyScanService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelRequests.tryEmit(Unit)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, StrategyScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.strategy_scan_notification_title))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.strategy_test_stop),
                stopIntent,
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.strategy_scan_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val ACTION_STOP = "ru.ozero.app.strategy.ACTION_STOP"
        const val CHANNEL_ID = "strategy_scan"
        const val NOTIFICATION_ID = 23
        internal val cancelRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
