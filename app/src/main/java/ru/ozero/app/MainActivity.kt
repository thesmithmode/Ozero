package ru.ozero.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.ui.MainViewModel
import ru.ozero.app.ui.RootNavigation
import ru.ozero.app.ui.launcher.BatteryGuard
import ru.ozero.app.ui.launcher.OnboardingGate
import ru.ozero.app.ui.launcher.VpnIntentLauncher
import ru.ozero.app.ui.theme.OzeroTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var userFlags: UserFlagsRepository

    @Inject lateinit var logcatReader: LogcatReader

    private lateinit var vpnIntentLauncher: VpnIntentLauncher
    private lateinit var batteryGuard: BatteryGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { logcatReader.start() }.onFailure { AppLogger.w(TAG, "logcatReader.start failed", it) }

        val vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { vpnIntentLauncher.onVpnPermissionResult(it.resultCode) }
        val notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                vpnIntentLauncher.onNotificationPermissionResult()
            }
        val batteryPromptLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                AppLogger.i(TAG, "battery prompt result code=${result.resultCode}")
            }

        batteryGuard = BatteryGuard(activity = this, userFlags = userFlags, launcher = batteryPromptLauncher)
        vpnIntentLauncher = VpnIntentLauncher(
            activity = this,
            viewModel = viewModel,
            vpnPermissionLauncher = vpnPermissionLauncher,
            notificationPermissionLauncher = notificationPermissionLauncher,
            onStarted = { batteryGuard.maybeShowPrompt() },
        )

        setContent {
            OzeroTheme {
                OnboardingGate(userFlags = userFlags) {
                    RootNavigation(viewModel = viewModel, onConnectClick = vpnIntentLauncher::onConnectClick)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
