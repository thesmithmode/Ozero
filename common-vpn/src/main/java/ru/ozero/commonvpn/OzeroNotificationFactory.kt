package ru.ozero.commonvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Build
import ru.ozero.enginescore.PersistentLoggers

class OzeroNotificationFactory(
    private val context: Context,
) {

    fun build(contentText: String? = null): Notification {
        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ozero VPN", NotificationManager.IMPORTANCE_LOW),
            )
            return Notification.Builder(context, CHANNEL_ID)
                .setContentTitle("Ozero VPN активен")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .apply {
                    if (contentText != null) {
                        val firstLine = contentText.substringBefore('\n')
                        setContentText(firstLine)
                        setStyle(Notification.BigTextStyle().bigText(contentText))
                    }
                    if (contentIntent != null) setContentIntent(contentIntent)
                }
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(context)
            .setContentTitle("Ozero VPN активен")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (contentText != null) {
                    val firstLine = contentText.substringBefore('\n')
                    setContentText(firstLine)
                    @Suppress("DEPRECATION")
                    setStyle(Notification.BigTextStyle().bigText(contentText))
                }
                if (contentIntent != null) setContentIntent(contentIntent)
            }
            .build()
    }

    fun notifyStats(statsText: String?) {
        if (statsText == null) return
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.notify(NOTIFICATION_ID, build(statsText))
        }.onFailure { PersistentLoggers.debug(TAG, "notifyStats: ${it.message}") }
    }

    fun enterForeground(service: Service): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val n = build()
                val su = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                val fb = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                try {
                    service.startForeground(NOTIFICATION_ID, n, su)
                } catch (t: Throwable) {
                    PersistentLoggers.warn(TAG, "startForeground SPECIAL_USE rejected, fallback to MANIFEST", t)
                    service.startForeground(NOTIFICATION_ID, n, fb)
                }
            } else {
                service.startForeground(NOTIFICATION_ID, build())
            }
            android.util.Log.i(TAG, "startForeground OK")
            true
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "startForeground threw: ${t.message}", t)
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "ozero_vpn"
        const val NOTIFICATION_ID = 1
        private const val TAG = "OzeroNotificationFactory"
    }
}
