package ru.ozero.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import ru.ozero.enginescore.EngineId
import ru.ozero.app.ui.about.AboutScreen
import ru.ozero.app.ui.backup.BackupScreen
import ru.ozero.app.ui.diag.DiagnosticsScreen
import ru.ozero.app.ui.logs.LogsScreen
import ru.ozero.app.ui.servers.ManualServerScreen
import ru.ozero.app.ui.servers.ServersScreen
import ru.ozero.app.ui.settings.AutoModeSettingsScreen
import ru.ozero.app.ui.settings.LanguageScreen
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.settings.engines.ByeDpiEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.TelegramProxySettingsScreen
import ru.ozero.app.ui.settings.engines.UrnetworkEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.UrnetworkSharedTrafficScreen
import ru.ozero.app.ui.settings.engines.WarpEngineSettingsScreen
import ru.ozero.app.ui.splittunnel.SplitTunnelScreen
import ru.ozero.app.ui.stats.TrafficStatsScreen
import ru.ozero.app.ui.strategy.StrategyTestScreen

private fun engineParamsTarget(engineId: EngineId?): TopScreen = when (engineId) {
    null -> TopScreen.AutoModeSettings
    EngineId.WARP -> TopScreen.WarpEngineSettings
    EngineId.URNETWORK -> TopScreen.UrnetworkEngineSettings
    EngineId.BYEDPI -> TopScreen.ByeDpiEngineSettings
    else -> TopScreen.Servers
}

@Composable
fun RootNavigation(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(TopScreen.Main) }
    val backStack = remember { mutableListOf<TopScreen>() }
    fun navigate(target: TopScreen) {
        backStack.add(screen)
        screen = target
    }
    fun back() {
        screen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) else TopScreen.Main
    }
    BackHandler(enabled = backStack.isNotEmpty()) { back() }
    val stateHolder = rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(key = screen.name) {
        when (screen) {
            TopScreen.Settings ->
                SettingsScreen(
                    onBack = { back() },
                    onOpenAllowedApps = { navigate(TopScreen.SplitTunnel) },
                    onOpenServers = { navigate(TopScreen.Servers) },
                    onOpenAbout = { navigate(TopScreen.About) },
                    onOpenLogs = { navigate(TopScreen.Logs) },
                    onOpenByeDpiEngineSettings = { navigate(TopScreen.ByeDpiEngineSettings) },
                    onOpenUrnetworkSettings = { navigate(TopScreen.UrnetworkEngineSettings) },
                    onOpenWarpSettings = { navigate(TopScreen.WarpEngineSettings) },
                    onOpenTelegramProxySettings = { navigate(TopScreen.TelegramProxySettings) },
                    onOpenManualServer = { navigate(TopScreen.ManualServer) },
                    onOpenStatsHistory = { navigate(TopScreen.StatsHistory) },
                    onOpenDiagnostics = { navigate(TopScreen.Diagnostics) },
                    onOpenBackup = { navigate(TopScreen.Backup) },
                    onOpenAutoModeSettings = { navigate(TopScreen.AutoModeSettings) },
                    onOpenLanguage = { navigate(TopScreen.Language) },
                )
            TopScreen.AutoModeSettings -> AutoModeSettingsScreen(onBack = { back() })
            TopScreen.Language -> LanguageScreen(onBack = { back() })
            TopScreen.Backup -> BackupScreen(onBack = { back() })
            TopScreen.Logs -> LogsScreen(onBack = { back() })
            TopScreen.Diagnostics -> DiagnosticsScreen(onBack = { back() })
            TopScreen.SplitTunnel -> SplitTunnelScreen(onBack = { back() })
            TopScreen.Servers -> ServersScreen(onBack = { back() })
            TopScreen.About -> AboutScreen(onBack = { back() })
            TopScreen.ByeDpiEngineSettings ->
                ByeDpiEngineSettingsScreen(
                    onBack = { back() },
                    onOpenStrategyTest = { navigate(TopScreen.StrategyTest) },
                )
            TopScreen.UrnetworkEngineSettings -> UrnetworkEngineSettingsScreen(
                onBack = { back() },
                onOpenSharedTraffic = { navigate(TopScreen.UrnetworkSharedTraffic) },
            )
            TopScreen.UrnetworkSharedTraffic -> UrnetworkSharedTrafficScreen(onBack = { back() })
            TopScreen.WarpEngineSettings -> WarpEngineSettingsScreen(onBack = { back() })
            TopScreen.TelegramProxySettings -> TelegramProxySettingsScreen(onBack = { back() })
            TopScreen.StrategyTest -> StrategyTestScreen(onBack = { back() })
            TopScreen.ManualServer -> ManualServerScreen(onBack = { back() })
            TopScreen.StatsHistory -> TrafficStatsScreen(onBack = { back() })
            TopScreen.Main ->
                MainScreen(
                    viewModel = viewModel,
                    onConnectClick = onConnectClick,
                    onOpenSettings = { navigate(TopScreen.Settings) },
                    onOpenSplitTunnel = { navigate(TopScreen.SplitTunnel) },
                    onOpenEngineParams = { engineId -> navigate(engineParamsTarget(engineId)) },
                )
        }
    }
}
