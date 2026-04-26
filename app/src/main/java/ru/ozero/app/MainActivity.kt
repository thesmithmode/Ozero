package ru.ozero.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.app.selfupdate.UpdateInstallEvent
import ru.ozero.app.selfupdate.UpdateInstallEventBus
import ru.ozero.app.ui.MainScreen
import ru.ozero.app.ui.MainViewModel
import ru.ozero.app.ui.diag.DiagnosticsScreen
import ru.ozero.app.ui.servers.ServersScreen
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.splittunnel.SplitTunnelScreen
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.OzeroVpnService

enum class TopScreen { Main, Settings, Diagnostics, SplitTunnel, Servers }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /**
     * RT.6.1: запуск system-confirm-dialog'а PackageInstaller'а на Android 12+.
     * Получаем готовый Intent из broadcast'а STATUS_PENDING_USER_ACTION и
     * стартуем его как Activity. Финальный статус прилетит в receiver отдельным
     * broadcast'ом — result-callback нам не нужен.
     */
    private val updateConfirmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i(TAG, "update confirm result code=${result.resultCode}")
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onConnectClick()
                viewModel.onVpnPermissionGranted()
                startVpnService()
            } else {
                // Permission denied — orchestrator остаётся в Idle, не трогаем его.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeSelfUpdateEvents()
        setContent {
            OzeroTheme {
                var screen by rememberSaveable { mutableStateOf(TopScreen.Main) }
                when (screen) {
                    TopScreen.Settings ->
                        SettingsScreen(
                            onBack = { screen = TopScreen.Main },
                            onOpenAllowedApps = { screen = TopScreen.SplitTunnel },
                            onOpenServers = { screen = TopScreen.Servers },
                        )
                    TopScreen.Diagnostics ->
                        DiagnosticsScreen(onBack = { screen = TopScreen.Main })
                    TopScreen.SplitTunnel ->
                        SplitTunnelScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.Servers ->
                        ServersScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.Main ->
                        MainScreen(
                            viewModel = viewModel,
                            onConnectClick = ::onConnectClick,
                            onOpenSettings = { screen = TopScreen.Settings },
                            onOpenDiagnostics = { screen = TopScreen.Diagnostics },
                        )
                }
            }
        }
    }

    private fun onConnectClick() {
        // Prepare ДО dispatch: разрешение запрашивается первым, только при OK переходим в Connect.
        // Иначе orchestrator уйдёт в Probing до получения пермишна — race condition.
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            viewModel.onConnectClick()
            viewModel.onVpnPermissionGranted()
            startVpnService()
        }
    }

    private fun startVpnService() {
        startService(
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
        )
    }

    private fun observeSelfUpdateEvents() {
        lifecycleScope.launch {
            UpdateInstallEventBus.events
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .onEach { event ->
                    when (event) {
                        is UpdateInstallEvent.PendingUserAction -> {
                            Log.i(TAG, "self-update PendingUserAction → launch confirm")
                            updateConfirmLauncher.launch(event.intent)
                        }
                        is UpdateInstallEvent.Success ->
                            Log.i(TAG, "self-update Success session=${event.sessionId}")
                        is UpdateInstallEvent.Failure ->
                            Log.w(
                                TAG,
                                "self-update Failure session=${event.sessionId} " +
                                    "status=${event.statusCode} message=${event.message}",
                            )
                    }
                }
                .collect()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
