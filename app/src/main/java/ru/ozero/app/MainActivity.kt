package ru.ozero.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.BootFileLogger
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.ui.MainViewModel
import ru.ozero.app.ui.RootNavigation
import ru.ozero.app.ui.launcher.BatteryGuard
import ru.ozero.app.ui.launcher.OnboardingGate
import ru.ozero.app.ui.launcher.VpnIntentLauncher
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.app.vpn.EngineSettingsRestartObserver
import ru.ozero.commonvpn.TunnelState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var userFlags: UserFlagsRepository

    @Inject lateinit var settingsRepository: ru.ozero.app.settings.SettingsRepository

    @Inject lateinit var logcatReader: LogcatReader

    private lateinit var vpnIntentLauncher: VpnIntentLauncher
    private lateinit var batteryGuard: BatteryGuard

    private val safeUiCoroutineHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.e(TAG, "uncaught coroutine in MainActivity", throwable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { BootFileLogger.info(TAG, "onCreate before super") }
        super.onCreate(savedInstanceState)
        runCatching { BootFileLogger.info(TAG, "onCreate after super (Hilt inject done)") }
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

        observeLiveEngineSettingsChanges()
        runCatching { BootFileLogger.info(TAG, "onCreate before setContent") }
        setContent {
            OzeroTheme {
                OnboardingGate(userFlags = userFlags) {
                    RootNavigation(viewModel = viewModel, onConnectClick = vpnIntentLauncher::onConnectClick)
                }
            }
        }
        runCatching { BootFileLogger.info(TAG, "onCreate after setContent") }
    }

    private fun observeLiveEngineSettingsChanges() {
        val observer = EngineSettingsRestartObserver(
            settingsFlow = settingsRepository.settings,
            vpnStateProvider = { viewModel.state.value },
            onRestartConnected = { snapshot ->
                AppLogger.i(TAG, "engine settings changed while connected → restart $snapshot")
                vpnIntentLauncher.stop()
                withTimeoutOrNull(5_000L) {
                    viewModel.state.first {
                        it is TunnelState.Idle || it is TunnelState.Failed
                    }
                }
                vpnIntentLauncher.start()
            },
        )
        lifecycleScope.launch(safeUiCoroutineHandler) {
            observer.triggers
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { observer.handle(it) }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
