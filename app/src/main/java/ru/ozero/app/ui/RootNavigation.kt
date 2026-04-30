package ru.ozero.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.ozero.app.ui.about.AboutScreen
import ru.ozero.app.ui.diag.DiagnosticsScreen
import ru.ozero.app.ui.logs.LogsScreen
import ru.ozero.app.ui.servers.ManualServerScreen
import ru.ozero.app.ui.servers.ServersScreen
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.settings.engines.ByeDpiEngineSettingsScreen
import ru.ozero.app.ui.splittunnel.SplitTunnelScreen
import ru.ozero.app.ui.stats.StatsHistoryScreen

@Composable
fun RootNavigation(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(TopScreen.Main) }
    when (screen) {
        TopScreen.Settings ->
            SettingsScreen(
                onBack = { screen = TopScreen.Main },
                onOpenAllowedApps = { screen = TopScreen.SplitTunnel },
                onOpenServers = { screen = TopScreen.Servers },
                onOpenAbout = { screen = TopScreen.About },
                onOpenLogs = { screen = TopScreen.Logs },
                onOpenByeDpiEngineSettings = { screen = TopScreen.ByeDpiEngineSettings },
                onOpenManualServer = { screen = TopScreen.ManualServer },
                onOpenStatsHistory = { screen = TopScreen.StatsHistory },
            )
        TopScreen.Logs ->
            LogsScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.Diagnostics ->
            DiagnosticsScreen(onBack = { screen = TopScreen.Main })
        TopScreen.SplitTunnel ->
            SplitTunnelScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.Servers ->
            ServersScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.About ->
            AboutScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.ByeDpiEngineSettings ->
            ByeDpiEngineSettingsScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.ManualServer ->
            ManualServerScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.StatsHistory ->
            StatsHistoryScreen(onBack = { screen = TopScreen.Settings })
        TopScreen.Main ->
            MainScreen(
                viewModel = viewModel,
                onConnectClick = onConnectClick,
                onOpenSettings = { screen = TopScreen.Settings },
                onOpenDiagnostics = { screen = TopScreen.Diagnostics },
            )
    }
}
