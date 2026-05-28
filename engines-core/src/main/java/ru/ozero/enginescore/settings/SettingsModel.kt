package ru.ozero.enginescore.settings

import ru.ozero.enginescore.EngineId

data class SettingsModel(
    val splitMode: SplitTunnelMode = DEFAULT_SPLIT_MODE,
    val ipv6Enabled: Boolean = DEFAULT_IPV6_ENABLED,
    val autoStart: Boolean = DEFAULT_AUTO_START,
    val trafficMode: TrafficMode = DEFAULT_TRAFFIC_MODE,
    val manualEngine: EngineId? = DEFAULT_MANUAL_ENGINE,
    val engineAutoPriority: List<EngineId> = DEFAULT_ENGINE_AUTO_PRIORITY,
    val urnetworkEnabled: Boolean = DEFAULT_URNETWORK_ENABLED,
    val urnetworkJwt: String? = DEFAULT_URNETWORK_JWT,
    val urnetworkCountryCode: String? = DEFAULT_URNETWORK_COUNTRY_CODE,
    val byedpiWinningArgs: String? = DEFAULT_BYEDPI_WINNING_ARGS,
    val byedpiDefaultAccepted: Boolean = DEFAULT_BYEDPI_DEFAULT_ACCEPTED,
    val byedpiUseUiMode: Boolean = DEFAULT_BYEDPI_USE_UI_MODE,
    val byedpiUiSettings: ByeDpiUiSettings = DEFAULT_BYEDPI_UI_SETTINGS,
    val customDnsServers: List<String> = DEFAULT_CUSTOM_DNS_SERVERS,
    val hostsMode: HostsMode = DEFAULT_HOSTS_MODE,
    val hosts: List<String> = DEFAULT_HOSTS,
    val uiLocaleTag: String? = DEFAULT_UI_LOCALE_TAG,
    val appMode: AppMode = DEFAULT_APP_MODE,
    val killswitchEnabled: Boolean = DEFAULT_KILLSWITCH_ENABLED,
    val alwaysOnBannerDismissed: Boolean = DEFAULT_ALWAYS_ON_BANNER_DISMISSED,
) {
    companion object {
        val DEFAULT_SPLIT_MODE: SplitTunnelMode = SplitTunnelMode.ALL
        const val DEFAULT_IPV6_ENABLED: Boolean = false
        const val DEFAULT_AUTO_START: Boolean = false
        val DEFAULT_TRAFFIC_MODE: TrafficMode = TrafficMode.TUN
        val DEFAULT_MANUAL_ENGINE: EngineId? = null
        val DEFAULT_ENGINE_AUTO_PRIORITY: List<EngineId> = listOf(
            EngineId.WARP, EngineId.URNETWORK, EngineId.BYEDPI, EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
        )
        const val DEFAULT_URNETWORK_ENABLED: Boolean = false
        val DEFAULT_URNETWORK_JWT: String? = null
        val DEFAULT_URNETWORK_COUNTRY_CODE: String? = null
        val DEFAULT_BYEDPI_WINNING_ARGS: String? = null
        const val DEFAULT_BYEDPI_DEFAULT_ACCEPTED: Boolean = false
        const val DEFAULT_BYEDPI_USE_UI_MODE: Boolean = true
        val DEFAULT_BYEDPI_UI_SETTINGS: ByeDpiUiSettings = ByeDpiUiSettings.DEFAULT
        val DEFAULT_CUSTOM_DNS_SERVERS: List<String> = emptyList()
        val DEFAULT_HOSTS_MODE: HostsMode = HostsMode.DISABLED
        val DEFAULT_HOSTS: List<String> = emptyList()
        val DEFAULT_UI_LOCALE_TAG: String? = null
        val DEFAULT_APP_MODE: AppMode = AppMode.SIMPLE
        const val DEFAULT_KILLSWITCH_ENABLED: Boolean = false
        const val DEFAULT_ALWAYS_ON_BANNER_DISMISSED: Boolean = false

        const val LOCALE_RU: String = "ru"
        const val LOCALE_EN: String = "en"
        const val LOCALE_ZH_CN: String = "zh-CN"
        const val LOCALE_ES: String = "es"
        const val LOCALE_AR: String = "ar"
        const val LOCALE_FR: String = "fr"
        const val LOCALE_HI: String = "hi"
        const val LOCALE_PT: String = "pt"
        const val LOCALE_ID: String = "id"
        const val LOCALE_DE: String = "de"
        const val LOCALE_JA: String = "ja"

        val SUPPORTED_LOCALES: List<String> = listOf(
            LOCALE_RU, LOCALE_EN, LOCALE_ZH_CN, LOCALE_ES, LOCALE_AR,
            LOCALE_FR, LOCALE_HI, LOCALE_PT, LOCALE_ID, LOCALE_DE, LOCALE_JA,
        )

        val DEFAULT: SettingsModel = SettingsModel()
    }
}
