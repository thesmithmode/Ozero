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
    val vpnMode: VpnMode = VpnMode.TUN,
    val byedpiArgs: String = DEFAULT_BYEDPI_ARGS,
    val byedpiDns: String = "",
    val singboxSubscriptionUrl: String = "",
    val singboxCustomLink: String = "",
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.DISABLED,
    val splitTunnelApps: List<String> = emptyList(),
) {
    companion object {
        val DEFAULT_ENGINE_AUTO_PRIORITY: List<EngineId> = listOf(
            EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK,
            EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
        )
        const val DEFAULT_BYEDPI_ARGS = "--disorder 1 --oob 1 --split 1"
        val DEFAULT = SettingsModel()
    }
}

enum class SplitTunnelMode { DISABLED, INCLUDE, EXCLUDE }
