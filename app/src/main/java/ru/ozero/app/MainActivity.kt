package ru.ozero.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var userFlags: UserFlagsRepository

    @Inject lateinit var settingsRepository: ru.ozero.enginescore.settings.SettingsRepository

    @Inject lateinit var logcatReader: LogcatReader

    @Inject lateinit var tunnelController: TunnelController

    private lateinit var vpnIntentLauncher: VpnIntentLauncher
    private lateinit var batteryGuard: BatteryGuard
    private val restartMutex = Mutex()
    private val restartQueue = ArrayDeque<String>()
    private var restartInProgress = false

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
        setContent {
            OzeroTheme {
                OnboardingGate(userFlags = userFlags) {
                    RootNavigation(viewModel = viewModel, onConnectClick = vpnIntentLauncher::onConnectClick)
                }
            }
        }
    }

    private suspend fun restartVpnIfConnected(reason: String) {
        var shouldProcess = false
        restartMutex.withLock {
            restartQueue.addLast(reason)
            if (!restartInProgress) {
                restartInProgress = true
                shouldProcess = true
            }
        }
        if (!shouldProcess) {
            AppLogger.d(TAG, "restart request coalesced while another restart is running")
            return
        }
        try {
            do {
                val nextReason = restartMutex.withLock {
                    restartQueue.removeFirstOrNull().also {
                        if (it == null) {
                            restartInProgress = false
                        }
                    }
                } ?: return
                if (!performRestartIfConnected(nextReason)) return
                if (restartMutex.withLock { restartQueue.isNotEmpty() }) {
                    withTimeoutOrNull(RESTART_SETTLE_TIMEOUT_MS) {
                        viewModel.state.first {
                            it is TunnelState.Connected || it is TunnelState.Failed
                        }
                    }
                }
            } while (restartMutex.withLock { restartQueue.isNotEmpty() })
        } finally {
            restartMutex.withLock {
                if (restartQueue.isEmpty()) {
                    restartInProgress = false
                }
            }
        }
    }

    private suspend fun performRestartIfConnected(reason: String): Boolean {
        val current = viewModel.state.value
        val fromEngine = when (current) {
            is TunnelState.Connected -> current.engineId
            is TunnelState.Connecting -> current.engineId
            is TunnelState.Probing -> current.engineId
            else -> return false
        }
        AppLogger.i(TAG, reason)
        val pendingTarget = tunnelController.switching.value?.to
        tunnelController.onSwitchingStarted(from = fromEngine, to = pendingTarget)
        try {
            vpnIntentLauncher.stop()
            val stopped = withTimeoutOrNull(RESTART_STOP_TIMEOUT_MS) {
                viewModel.state.first { it is TunnelState.Idle || it is TunnelState.Failed }
            }
            if (stopped == null) {
                AppLogger.w(TAG, "engine settings restart skipped: stop timeout")
                tunnelController.onSwitchingFinished("restart stop timeout")
                return false
            }
            tunnelController.onSwitchingStarted(from = fromEngine, to = pendingTarget)
            vpnIntentLauncher.start()
            return true
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
                restartVpnIfConnected("engine settings changed while connected -> restart $snapshot")
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
        const val RESTART_STOP_TIMEOUT_MS = 11_000L
        const val RESTART_SETTLE_TIMEOUT_MS = 15_000L
    }
}
