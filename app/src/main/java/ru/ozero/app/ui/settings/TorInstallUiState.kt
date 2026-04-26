package ru.ozero.app.ui.settings

/**
 * Состояние UI для on-demand модуля :dynamic_tor.
 *
 * Транзиции:
 *   NotInstalled → Installing(percent…) → Installed | Failed(reason)
 *   Failed → (retry) → Installing → …
 *   Installing → (cancel) → NotInstalled
 */
sealed interface TorInstallUiState {
    data object NotInstalled : TorInstallUiState

    data class Installing(val percent: Int) : TorInstallUiState

    data object Installed : TorInstallUiState

    data class Failed(val reason: String) : TorInstallUiState
}

/** Группа Tor-колбэков для UI, чтобы не раздувать сигнатуры Composable. */
data class TorActions(
    val onInstall: () -> Unit,
    val onCancel: () -> Unit,
)

/** Группа навигационных колбэков Settings → дочерние экраны. */
data class SettingsNavActions(
    val onOpenAllowedApps: () -> Unit,
    val onOpenServers: () -> Unit,
    val onOpenAbout: () -> Unit = {},
)
