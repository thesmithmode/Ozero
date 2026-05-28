package ru.ozero.app.ui.settings

data class SettingsNavActions(
    val onOpenAllowedApps: () -> Unit,
    val onOpenServers: () -> Unit,
    val onOpenAbout: () -> Unit = {},
    val onOpenLogs: () -> Unit = {},
    val onOpenByeDpiEngineSettings: () -> Unit = {},
    val onOpenUrnetworkSettings: () -> Unit = {},
    val onOpenWarpSettings: () -> Unit = {},
    val onOpenStatsHistory: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onOpenAutoModeSettings: () -> Unit = {},
    val onOpenLanguage: () -> Unit = {},
    val onOpenMasterDnsSettings: () -> Unit = {},
    val onOpenFptnSettings: () -> Unit = {},
    val onOpenSingboxSettings: () -> Unit = {},
    val onOpenChainSettings: () -> Unit = {},
)
