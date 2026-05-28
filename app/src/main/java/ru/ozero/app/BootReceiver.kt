package ru.ozero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var settingsDataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isAutoStartEnabled(context)) return@launch
                if (currentTrafficMode() == TrafficMode.TUN && VpnService.prepare(context) != null) {
                    Log.w(TAG, "auto-start пропущен: VpnService.prepare не выдан (нужен повторный grant в UI)")
                    return@launch
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, OzeroVpnService::class.java).apply {
                        action = OzeroVpnService.ACTION_START
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isAutoStartEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_START, false)

    private suspend fun currentTrafficMode(): TrafficMode {
        val raw = settingsDataStore.data.first()[SettingsKeys.TRAFFIC_MODE]
        return raw
            ?.let { runCatching { TrafficMode.valueOf(it) }.getOrNull() }
            ?: SettingsModel.DEFAULT_TRAFFIC_MODE
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val KEY_AUTO_START = "auto_start_on_boot"
        private const val PREFS_NAME = "ozero_prefs"

        fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun setAutoStart(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
        }
    }
}
