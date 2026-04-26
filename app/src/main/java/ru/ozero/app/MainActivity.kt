package ru.ozero.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import ru.ozero.app.ui.MainScreen
import ru.ozero.app.ui.MainViewModel
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.OzeroVpnService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

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
        setContent {
            OzeroTheme {
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                if (settingsOpen) {
                    SettingsScreen(onBack = { settingsOpen = false })
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onConnectClick = ::onConnectClick,
                        onOpenSettings = { settingsOpen = true },
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
}
