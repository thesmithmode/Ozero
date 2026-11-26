package ru.ozero.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.enginescore.EngineId
import ru.ozero.app.ui.about.AboutScreen
import ru.ozero.app.ui.backup.BackupScreen
import ru.ozero.app.ui.logs.LogsScreen
import ru.ozero.app.ui.servers.ServersScreen
import ru.ozero.app.ui.settings.AutoModeSettingsScreen
import ru.ozero.app.ui.settings.LanguageScreen
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.settings.engines.ByeDpiEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.FptnEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.MasqueSettingsScreen
import ru.ozero.app.ui.settings.engines.MasterDnsSettingsScreen
import ru.ozero.app.ui.settings.engines.OperaEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.TelegramWssSettingsScreen
import ru.ozero.app.ui.settings.engines.UrnetworkEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.UrnetworkSharedTrafficScreen
import ru.ozero.app.ui.settings.engines.WarpEngineSettingsScreen
import ru.ozero.app.ui.settings.engines.singbox.SingboxAdvancedSettingsScreen
import ru.ozero.app.ui.settings.engines.singbox.SingboxEngineSettingsScreen
import ru.ozero.app.ui.splittunnel.SplitTunnelScreen
import ru.ozero.app.ui.stats.TrafficStatsScreen
import ru.ozero.app.ui.strategy.StrategyTestScreen

private fun engineParamsTarget(engineId: EngineId?): TopScreen = when (engineId) {
    null -> TopScreen.AutoModeSettings
    EngineId.WARP -> TopScreen.WarpEngineSettings
    EngineId.MASQUE -> TopScreen.MasqueSettings
    EngineId.URNETWORK -> TopScreen.UrnetworkEngineSettings
    EngineId.BYEDPI -> TopScreen.ByeDpiEngineSettings
    EngineId.MASTERDNS -> TopScreen.MasterDnsSettings
    EngineId.FPTN -> TopScreen.FptnSettings
    EngineId.SINGBOX -> TopScreen.SingboxSettings
    EngineId.TGWSS -> TopScreen.TelegramWssSettings
    EngineId.OPERA -> TopScreen.OperaSettings
    EngineId.GFVPN -> TopScreen.GfVpnSettings
}

internal val screenHistorySaver = listSaver<List<TopScreen>, String>(
    save = { screens -> screens.map { it.name } },
    restore = { names -> names.map(TopScreen::valueOf) },
)

@Composable
fun RootNavigation(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
) {
    var history by rememberSaveable(stateSaver = screenHistorySaver) { mutableStateOf(listOf(TopScreen.Main)) }
    val screen = history.last()
    fun navigate(target: TopScreen) {
        history = history + target
    }
    fun back() {
        if (history.size > 1) history = history.dropLast(1)
    }
    BackHandler(enabled = history.size > 1) { back() }
    val stateHolder = rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(key = screen.name) {
        NavigationContent(screen, viewModel, onConnectClick, ::navigate, ::back)
    }
}

@Composable
private fun NavigationContent(
    screen: TopScreen,
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    navigate: (TopScreen) -> Unit,
    back: () -> Unit,
) {
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
                onOpenMasqueSettings = { navigate(TopScreen.MasqueSettings) },
                onOpenMasterDnsSettings = { navigate(TopScreen.MasterDnsSettings) },
                onOpenFptnSettings = { navigate(TopScreen.FptnSettings) },
                onOpenSingboxSettings = { navigate(TopScreen.SingboxSettings) },
                onOpenTelegramWssSettings = { navigate(TopScreen.TelegramWssSettings) },
                onOpenOperaSettings = { navigate(TopScreen.OperaSettings) },
                onOpenGfVpnSettings = { navigate(TopScreen.GfVpnSettings) },
                onOpenStatsHistory = { navigate(TopScreen.StatsHistory) },
                onOpenBackup = { navigate(TopScreen.Backup) },
                onOpenAutoModeSettings = { navigate(TopScreen.AutoModeSettings) },
                onOpenLanguage = { navigate(TopScreen.Language) },
            )
        TopScreen.AutoModeSettings -> AutoModeSettingsScreen(onBack = { back() })
        TopScreen.Language -> LanguageScreen(onBack = { back() })
        TopScreen.Backup -> BackupScreen(onBack = { back() })
        TopScreen.Logs -> LogsScreen(onBack = { back() })
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
        TopScreen.MasqueSettings -> MasqueSettingsScreen(onBack = { back() })
        TopScreen.MasterDnsSettings -> MasterDnsSettingsScreen(onBack = { back() })
        TopScreen.FptnSettings -> FptnEngineSettingsScreen(onBack = { back() })
        TopScreen.OperaSettings -> OperaEngineSettingsScreen(onBack = { back() })
        TopScreen.GfVpnSettings -> ru.ozero.app.ui.settings.engines.GfVpnSettingsScreen(onBack = back)
        TopScreen.SingboxSettings -> SingboxEngineSettingsScreen(
            onBack = { back() },
            onOpenAdvanced = { navigate(TopScreen.SingboxAdvancedSettings) },
        )
        TopScreen.SingboxAdvancedSettings -> SingboxAdvancedSettingsScreen(onBack = { back() })
        TopScreen.TelegramWssSettings -> {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val trafficMode by viewModel.trafficMode.collectAsStateWithLifecycle()
            TelegramWssSettingsScreen(
                state = state,
                trafficMode = trafficMode,
                onBack = { back() },
                onOpenSplitTunnel = { navigate(TopScreen.SplitTunnel) },
                onOpenWarp = { navigate(TopScreen.WarpEngineSettings) },
            )
        }
        TopScreen.StrategyTest -> StrategyTestScreen(onBack = { back() })
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
