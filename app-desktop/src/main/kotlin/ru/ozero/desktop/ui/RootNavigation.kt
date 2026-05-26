package ru.ozero.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.ui.about.AboutScreen
import ru.ozero.desktop.ui.logs.LogsScreen
import ru.ozero.desktop.ui.settings.SettingsScreen
import ru.ozero.desktop.ui.splittunnel.SplitTunnelScreen

private fun engineParamsTarget(engineId: EngineId?): TopScreen = when (engineId) {
    EngineId.WARP -> TopScreen.WarpEngineSettings
    EngineId.URNETWORK -> TopScreen.UrnetworkEngineSettings
    EngineId.BYEDPI -> TopScreen.ByeDpiEngineSettings
    EngineId.MASTERDNS -> TopScreen.MasterDnsSettings
    EngineId.FPTN -> TopScreen.FptnSettings
    EngineId.SINGBOX -> TopScreen.SingboxSettings
    else -> TopScreen.Settings
}

@Composable
fun RootNavigation(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf(TopScreen.Main) }
    val backStack = remember { mutableListOf<TopScreen>() }
    fun navigate(target: TopScreen) {
        backStack.add(screen)
        screen = target
    }
    fun back() {
        screen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) else TopScreen.Main
    }

    when (screen) {
        TopScreen.Settings -> {
            val currentMode by viewModel.appMode.collectAsState()
            SettingsScreen(
                onBack = { back() },
                currentAppMode = currentMode,
                onOpenAllowedApps = { navigate(TopScreen.SplitTunnel) },
                onOpenAbout = { navigate(TopScreen.About) },
                onOpenLogs = { navigate(TopScreen.Logs) },
                onAppModeSelect = viewModel::onAppModeSelect,
            )
        }
        TopScreen.About -> AboutScreen(onBack = { back() })
        TopScreen.Logs -> LogsScreen(onBack = { back() })
        TopScreen.SplitTunnel -> SplitTunnelScreen(onBack = { back() })
        TopScreen.ByeDpiEngineSettings,
        TopScreen.UrnetworkEngineSettings,
        TopScreen.WarpEngineSettings,
        TopScreen.MasterDnsSettings,
        TopScreen.FptnSettings,
        TopScreen.SingboxSettings -> PlaceholderScreen(
            title = screen.name,
            onBack = { back() },
        )
        TopScreen.Main -> MainScreen(
            viewModel = viewModel,
            onConnectClick = viewModel::onConnectClick,
            onOpenSettings = { navigate(TopScreen.Settings) },
            onOpenSplitTunnel = { navigate(TopScreen.SplitTunnel) },
            onOpenEngineParams = { engineId -> navigate(engineParamsTarget(engineId)) },
        )
    }
}
