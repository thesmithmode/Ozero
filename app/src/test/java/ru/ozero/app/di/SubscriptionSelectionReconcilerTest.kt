package ru.ozero.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionSelectionReconcilerTest {
    @Test
    fun `missing committed selection and cached bean are removed together`() = runBlocking {
        val data = ImmediateStore()
        val profiles = mockk<ProxyProfileDao>()
        coEvery { profiles.getById(42L) } returns null
        data.edit {
            it[SingboxProbeService.SELECTED_PROFILE_KEY] = 42L
            it[SingboxProbeService.BEAN_KEY] = byteArrayOf(1, 2)
        }

        reconcileSubscriptionSelection(data, profiles)
        reconcileSubscriptionSelection(data, profiles)

        val prefs = data.data.first()
        assertNull(prefs[SingboxProbeService.SELECTED_PROFILE_KEY])
        assertNull(prefs[SingboxProbeService.BEAN_KEY])
        coVerify(exactly = 1) { profiles.getById(42L) }
    }

    @Test
    fun `existing auto and legacy selections are preserved`() = runBlocking {
        val data = ImmediateStore()
        val profiles = mockk<ProxyProfileDao>()
        coEvery { profiles.getById(42L) } returns ProxyProfile(
            id = 42L,
            groupId = 1L,
            name = "Fixture",
            beanBlob = byteArrayOf(1, 2),
            protocolType = 0,
        )
        for (selected in listOf(42L, SingboxEngine.SELECTED_AUTO, null)) {
            data.edit {
                if (selected == null) {
                    it.remove(SingboxProbeService.SELECTED_PROFILE_KEY)
                } else {
                    it[SingboxProbeService.SELECTED_PROFILE_KEY] = selected
                }
                it[SingboxProbeService.BEAN_KEY] = byteArrayOf(1, 2)
            }
            reconcileSubscriptionSelection(data, profiles)
            val prefs = data.data.first()
            assertEquals(selected, prefs[SingboxProbeService.SELECTED_PROFILE_KEY])
            assertContentEquals(byteArrayOf(1, 2), prefs[SingboxProbeService.BEAN_KEY])
        }
        coVerify(exactly = 1) { profiles.getById(any()) }
    }

    private class ImmediateStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
