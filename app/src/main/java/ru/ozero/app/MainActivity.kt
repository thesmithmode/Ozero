package ru.ozero.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.ui.MainViewModel
import ru.ozero.app.ui.RootNavigation
import ru.ozero.app.ui.launcher.BatteryGuard
import ru.ozero.app.ui.launcher.OnboardingGate
import ru.ozero.app.ui.launcher.VpnIntentLauncher
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.app.vpn.EngineSettingsRestartObserver
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginewarp.WarpConfigSlotStore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var userFlags: UserFlagsRepository

    @Inject lateinit var settingsRepository: ru.ozero.enginescore.settings.SettingsRepository

    @Inject lateinit var warpConfigSlotStore: WarpConfigSlotStore

    @Inject lateinit var logcatReader: LogcatReader

    @Inject lateinit var tunnelController: TunnelController

    private lateinit var vpnIntentLauncher: VpnIntentLauncher
    private lateinit var batteryGuard: BatteryGuard

    private val safeUiCoroutineHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.e(TAG, "uncaught coroutine in MainActivity", throwable)
    }

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

        observeLiveEngineSettingsChanges()
        observeWarpActiveSlotChanges()
        setContent {
            OzeroTheme {
                OnboardingGate(userFlags = userFlags) {
                    RootNavigation(viewModel = viewModel, onConnectClick = vpnIntentLauncher::onConnectClick)
                }
            }
        }
    }

    private suspend fun restartVpnIfConnected(reason: String) {
        val current = viewModel.state.value
        val fromEngine = when (current) {
            is TunnelState.Connected -> current.engineId
            is TunnelState.Connecting -> current.engineId
            is TunnelState.Probing -> current.engineId
            else -> return
        }
        AppLogger.i(TAG, reason)
        val pendingTarget = tunnelController.switching.value?.to
        tunnelController.onSwitchingStarted(from = fromEngine, to = pendingTarget)
        try {
            vpnIntentLauncher.stop()
            withTimeoutOrNull(5_000L) {
                viewModel.state.first { it is TunnelState.Idle || it is TunnelState.Failed }
            }
            vpnIntentLauncher.start()
        } catch (t: Throwable) {
            tunnelController.onSwitchingFinished("restart failed: ${t.message}")
            throw t
        }
    }

    private fun observeLiveEngineSettingsChanges() {
        val observer = EngineSettingsRestartObserver(
            settingsFlow = settingsRepository.settings,
            vpnStateProvider = { viewModel.state.value },
            onRestartConnected = { snapshot ->
                restartVpnIfConnected("engine settings changed while connected → restart $snapshot")
            },
        )
        lifecycleScope.launch(safeUiCoroutineHandler) {
            observer.triggers
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { observer.handle(it) }
        }
    }

    private fun observeWarpActiveSlotChanges() {
        lifecycleScope.launch(safeUiCoroutineHandler) {
            warpConfigSlotStore.activeConfig()
                .map { it?.peerEndpoint + it?.privateKey?.take(8) }
                .distinctUntilChanged()
                .drop(1)
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    restartVpnIfConnected("WARP active slot changed while connected → restart")
                }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
