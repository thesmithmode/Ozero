package ru.ozero.app.relay

import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import ru.ozero.enginescore.PersistentLoggers

class RelayLockManager(
    private val powerManager: PowerManager,
    private val wifiManager: WifiManager,
) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire() {
        if (wakeLock == null) {
            wakeLock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ozero::relay-provide")
                .apply { acquire() }
            PersistentLoggers.info(TAG, "WakeLock acquired")
        }
        if (wifiLock == null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager
                .createWifiLock(mode, "ozero::relay-provide")
                .apply { acquire() }
            PersistentLoggers.info(TAG, "WifiLock acquired")
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
            PersistentLoggers.info(TAG, "WakeLock released")
        }
        wifiLock?.let {
            if (it.isHeld) it.release()
            wifiLock = null
            PersistentLoggers.info(TAG, "WifiLock released")
        }
    }

    private companion object {
        const val TAG = "RelayLock"
    }
}
