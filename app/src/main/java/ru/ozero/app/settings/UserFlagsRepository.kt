package ru.ozero.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.ozero.enginescore.settings.SettingsKeys
import javax.inject.Inject

interface UserFlagsRepository {
    val flags: Flow<UserFlags>

    suspend fun isBatteryPromptShown(): Boolean

    suspend fun markBatteryPromptShown()

    suspend fun isOnboardingCompleted(): Boolean

    suspend fun markOnboardingCompleted()
}

data class UserFlags(
    val batteryPromptShown: Boolean,
    val onboardingCompleted: Boolean,
)

class UserFlagsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserFlagsRepository {

    override val flags: Flow<UserFlags> = dataStore.data.map { prefs ->
        UserFlags(
            batteryPromptShown = prefs[SettingsKeys.BATTERY_PROMPT_SHOWN] ?: false,
            onboardingCompleted = prefs[SettingsKeys.ONBOARDING_COMPLETED] ?: false,
        )
    }

    override suspend fun isBatteryPromptShown(): Boolean =
        dataStore.data.first()[SettingsKeys.BATTERY_PROMPT_SHOWN] ?: false

    override suspend fun markBatteryPromptShown() {
        dataStore.edit { it[SettingsKeys.BATTERY_PROMPT_SHOWN] = true }
    }

    override suspend fun isOnboardingCompleted(): Boolean =
        dataStore.data.first()[SettingsKeys.ONBOARDING_COMPLETED] ?: false

    override suspend fun markOnboardingCompleted() {
        dataStore.edit { it[SettingsKeys.ONBOARDING_COMPLETED] = true }
    }
}
