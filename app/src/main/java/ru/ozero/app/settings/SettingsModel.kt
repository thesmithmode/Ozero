package ru.ozero.app.settings

import ru.ozero.coreapi.EngineId
import ru.ozero.commonvpn.split.SplitTunnelMode

data class SettingsModel(
    val splitMode: SplitTunnelMode = DEFAULT_SPLIT_MODE,
    val ipv6Enabled: Boolean = DEFAULT_IPV6_ENABLED,
    val autoStart: Boolean = DEFAULT_AUTO_START,
    val manualEngine: EngineId? = DEFAULT_MANUAL_ENGINE,
) {
    companion object {
        val DEFAULT_SPLIT_MODE: SplitTunnelMode = SplitTunnelMode.ALL
        const val DEFAULT_IPV6_ENABLED: Boolean = false
        const val DEFAULT_AUTO_START: Boolean = false
        val DEFAULT_MANUAL_ENGINE: EngineId? = null

        val DEFAULT: SettingsModel = SettingsModel()
    }
}
