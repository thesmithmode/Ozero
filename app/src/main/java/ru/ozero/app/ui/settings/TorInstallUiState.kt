package ru.ozero.app.ui.settings

sealed interface TorInstallUiState {
    data object NotInstalled : TorInstallUiState

    data class Installing(val percent: Int) : TorInstallUiState

    data object Installed : TorInstallUiState

    data class Failed(val reason: String) : TorInstallUiState
}

data class TorActions(
    val onInstall: () -> Unit,
    val onCancel: () -> Unit,
)

data class SettingsNavActions(
    val onOpenAllowedApps: () -> Unit,
    val onOpenServers: () -> Unit,
    val onOpenAbout: () -> Unit = {},
    val onOpenLogs: () -> Unit = {},
    val onOpenBootLog: () -> Unit = {},
)
