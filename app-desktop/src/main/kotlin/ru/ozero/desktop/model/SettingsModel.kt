package ru.ozero.desktop.model

data class SettingsModel(
    val ipv6Enabled: Boolean = false,
    val autoStart: Boolean = false,
    val manualEngine: EngineId? = null,
    val engineAutoPriority: List<EngineId> = DEFAULT_ENGINE_AUTO_PRIORITY,
    val appMode: AppMode = AppMode.SIMPLE,
    val killswitchEnabled: Boolean = false,
    val alwaysOnBannerDismissed: Boolean = false,
    val uiLocaleTag: String? = null,
) {
    companion object {
        val DEFAULT_ENGINE_AUTO_PRIORITY: List<EngineId> = listOf(
            EngineId.WARP, EngineId.URNETWORK, EngineId.BYEDPI,
            EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
        )
        val DEFAULT = SettingsModel()
    }
}
