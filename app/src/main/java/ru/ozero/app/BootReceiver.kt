package ru.ozero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import ru.ozero.commonvpn.OzeroVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!isAutoStartEnabled(context)) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
        )
    }

    private fun isAutoStartEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_START, false)

    companion object {
        private const val KEY_AUTO_START = "auto_start_on_boot"
        private const val PREFS_NAME = "ozero_prefs"

        fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun setAutoStart(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
        }
    }
}
