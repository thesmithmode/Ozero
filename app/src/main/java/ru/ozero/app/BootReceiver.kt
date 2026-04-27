package ru.ozero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.OzeroVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // goAsync: SharedPreferences.getBoolean синхронен, но загрузка с диска первого
        // обращения может быть пропущена в main thread → дефолт false и auto-start
        // не сработает. Уносим в IO-scope с pendingResult.finish().
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isAutoStartEnabled(context)) return@launch
                // VpnService.prepare() возвращает не-null Intent если разрешение НЕ выдано.
                // Из BroadcastReceiver мы не можем стартовать activity для запроса —
                // VpnService.establish() в сервисе вернёт null и сервис self-stop сразу же.
                // Не запускаем сервис вообще — иначе foreground notification мелькнёт и
                // тут же исчезнет, юзер не поймёт почему авто-старт не сработал.
                if (VpnService.prepare(context) != null) {
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
