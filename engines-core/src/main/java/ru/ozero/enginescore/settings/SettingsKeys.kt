package ru.ozero.enginescore.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    const val DATASTORE_NAME: String = "ozero_settings"

    val SPLIT_MODE = stringPreferencesKey("split_mode")
    val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
    val AUTO_START = booleanPreferencesKey("auto_start")
    val MANUAL_ENGINE = stringPreferencesKey("manual_engine")
    val ENGINE_AUTO_PRIORITY = stringPreferencesKey("engine_auto_priority")

    val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")

    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    val BYDPI_WINNING_ARGS = stringPreferencesKey("bydpi_winning_args")
    val BYDPI_LAST_PROBE_AT = longPreferencesKey("bydpi_last_probe_at")

    val URNETWORK_ENABLED = booleanPreferencesKey("urnetwork_enabled")
    val URNETWORK_JWT = stringPreferencesKey("urnetwork_jwt")
    val URNETWORK_COUNTRY_CODE = stringPreferencesKey("urnetwork_country_code")

    val CUSTOM_DNS_SERVERS = stringPreferencesKey("custom_dns_servers")

    val HOSTS_MODE = stringPreferencesKey("hosts_mode")
    val HOSTS_LIST = stringPreferencesKey("hosts_list")

    val UI_LOCALE_TAG = stringPreferencesKey("ui_locale_tag")

    val APP_MODE = stringPreferencesKey("app_mode")

    val KILLSWITCH_ENABLED = booleanPreferencesKey("killswitch_enabled")
}
