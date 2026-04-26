package ru.ozero.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    const val DATASTORE_NAME: String = "ozero_settings"

    val SPLIT_MODE = stringPreferencesKey("split_mode")
    val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
    val AUTO_START = booleanPreferencesKey("auto_start")
    val MANUAL_ENGINE = stringPreferencesKey("manual_engine")
}
