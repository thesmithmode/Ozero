package ru.ozero.app.ui.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.ui.permission.BatteryOptimization

class BatteryGuard(
    private val activity: ComponentActivity,
    private val userFlags: UserFlagsRepository,
    private val launcher: ActivityResultLauncher<Intent>,
) {
    fun maybeShowPrompt() {
        activity.lifecycleScope.launch {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@launch
            val isIgnoring = if (Build.VERSION.SDK_INT >= BatteryOptimization.MIN_SDK) {
                pm.isIgnoringBatteryOptimizations(activity.packageName)
            } else {
                true
            }
            val alreadyShown = userFlags.isBatteryPromptShown()
            val state = BatteryOptimization.resolve(
                sdkInt = Build.VERSION.SDK_INT,
                isIgnoring = isIgnoring,
                alreadyShown = alreadyShown,
            )
            if (state == BatteryOptimization.State.NeedsPrompt) {
                userFlags.markBatteryPromptShown()
                runCatching {
                    @Suppress("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    launcher.launch(intent)
                }.onFailure { AppLogger.w(TAG, "battery prompt launch failed", it) }
            } else {
                AppLogger.i(TAG, "battery prompt skipped state=$state")
            }
        }
    }

    private companion object {
        const val TAG = "BatteryGuard"
    }
}
