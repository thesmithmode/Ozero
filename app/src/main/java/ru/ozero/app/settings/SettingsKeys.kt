package ru.ozero.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    const val DATASTORE_NAME: String = "ozero_settings"

    val SPLIT_MODE = stringPreferencesKey("split_mode")
    val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
    val AUTO_START = booleanPreferencesKey("auto_start")
    val MANUAL_ENGINE = stringPreferencesKey("manual_engine")

    // RT.7.3: prompt показан хотя бы один раз → больше не дёргаем.
    val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")

    // RT.9: onboarding пройден → main UI.
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    // E16.3: winning ByeDPI strategy args (CLI-string), кэш probe-результата.
    val BYDPI_WINNING_ARGS = stringPreferencesKey("bydpi_winning_args")

    // E16.3: timestamp последнего успешного probe (ms epoch). Re-probe раз в 24h.
    val BYDPI_LAST_PROBE_AT = androidx.datastore.preferences.core.longPreferencesKey("bydpi_last_probe_at")

    // E15: URnetwork P2P fallback engine toggle (default: off до явного включения пользователем)
    val URNETWORK_ENABLED = booleanPreferencesKey("urnetwork_enabled")

    // E15: URnetwork JWT токен (от urnetwork.com аккаунта, null = не настроен)
    val URNETWORK_JWT = stringPreferencesKey("urnetwork_jwt")
}
