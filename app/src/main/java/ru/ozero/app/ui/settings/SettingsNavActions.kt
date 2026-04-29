package ru.ozero.app.ui.settings

data class SettingsNavActions(
    val onOpenAllowedApps: () -> Unit,
    val onOpenServers: () -> Unit,
    val onOpenAbout: () -> Unit = {},
    val onOpenLogs: () -> Unit = {},
    val onOpenByeDpiEngineSettings: () -> Unit = {},
    val onOpenManualServer: () -> Unit = {},
)
