package ru.ozero.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    const val DATASTORE_NAME: String = "ozero_settings"

    val SPLIT_MODE = stringPreferencesKey("split_mode")
    val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
    val AUTO_START = booleanPreferencesKey("auto_start")
    val MANUAL_ENGINE = stringPreferencesKey("manual_engine")

    val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")

    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    val BYDPI_WINNING_ARGS = stringPreferencesKey("bydpi_winning_args")

    val BYDPI_LAST_PROBE_AT = androidx.datastore.preferences.core.longPreferencesKey("bydpi_last_probe_at")

    val URNETWORK_ENABLED = booleanPreferencesKey("urnetwork_enabled")

    val URNETWORK_JWT = stringPreferencesKey("urnetwork_jwt")
}
