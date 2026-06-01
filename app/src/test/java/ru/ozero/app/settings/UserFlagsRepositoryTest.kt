package ru.ozero.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.enginescore.settings.SettingsKeys
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserFlagsRepositoryTest {

    @TempDir
    lateinit var tmpDir: File

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: UserFlagsRepository

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmpDir, "user_flags.preferences_pb") },
        )
        repository = UserFlagsRepositoryImpl(dataStore)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `flags emit false defaults from empty DataStore`() = runTest {
        val flags = repository.flags.first()

        assertFalse(flags.batteryPromptShown)
        assertFalse(flags.onboardingCompleted)
        assertFalse(repository.isBatteryPromptShown())
        assertFalse(repository.isOnboardingCompleted())
    }

    @Test
    fun `markBatteryPromptShown persists flag and preserves onboarding default`() = runTest {
        repository.markBatteryPromptShown()

        val flags = repository.flags.first()
        assertTrue(flags.batteryPromptShown)
        assertFalse(flags.onboardingCompleted)
        assertTrue(repository.isBatteryPromptShown())
    }

    @Test
    fun `markOnboardingCompleted persists flag and preserves battery default`() = runTest {
        repository.markOnboardingCompleted()

        val flags = repository.flags.first()
        assertFalse(flags.batteryPromptShown)
        assertTrue(flags.onboardingCompleted)
        assertTrue(repository.isOnboardingCompleted())
    }

    @Test
    fun `flags reflect values written directly to DataStore`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.BATTERY_PROMPT_SHOWN] = true
            prefs[SettingsKeys.ONBOARDING_COMPLETED] = true
        }

        val flags = repository.flags.first()
        assertTrue(flags.batteryPromptShown)
        assertTrue(flags.onboardingCompleted)
    }
}
