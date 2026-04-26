package ru.ozero.app.settings

import ru.ozero.coreapi.EngineId
import ru.ozero.commonvpn.split.SplitTunnelMode

data class SettingsModel(
    val splitMode: SplitTunnelMode = DEFAULT_SPLIT_MODE,
    val ipv6Enabled: Boolean = DEFAULT_IPV6_ENABLED,
    val autoStart: Boolean = DEFAULT_AUTO_START,
    val manualEngine: EngineId? = DEFAULT_MANUAL_ENGINE,
    /** E15: URnetwork P2P fallback включён пользователем */
    val urnetworkEnabled: Boolean = DEFAULT_URNETWORK_ENABLED,
    /** E15: JWT токен для URnetwork (null = не настроен) */
    val urnetworkJwt: String? = DEFAULT_URNETWORK_JWT,
) {
    companion object {
        val DEFAULT_SPLIT_MODE: SplitTunnelMode = SplitTunnelMode.ALL
        const val DEFAULT_IPV6_ENABLED: Boolean = false
        const val DEFAULT_AUTO_START: Boolean = false
        val DEFAULT_MANUAL_ENGINE: EngineId? = null
        const val DEFAULT_URNETWORK_ENABLED: Boolean = false
        val DEFAULT_URNETWORK_JWT: String? = null

        val DEFAULT: SettingsModel = SettingsModel()
    }
}
