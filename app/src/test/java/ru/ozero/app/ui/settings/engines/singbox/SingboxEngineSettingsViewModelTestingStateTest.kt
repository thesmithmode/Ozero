package ru.ozero.app.ui.settings.engines.singbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxEngineSettingsViewModelTestingStateTest {

    @Test
    fun `overlapping testing callbacks keep spinner visible until all probes finish`() = runTest {
        val vm = buildViewModel()
        val invokeTesting = vm.javaClass.getDeclaredMethod("onProfileTestingChanged", Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val uiStateField = vm.javaClass.getDeclaredField("_uiState")
            .apply { isAccessible = true }
        val uiStateFlow = uiStateField.get(vm) as MutableStateFlow<*>

        invokeTesting.invoke(vm, 42L, true)
        invokeTesting.invoke(vm, 42L, true)
        assertTrue((uiStateFlow.value as SingboxSettingsUiState).testingProfileIds.contains(42L))

        invokeTesting.invoke(vm, 42L, false)
        assertTrue((uiStateFlow.value as SingboxSettingsUiState).testingProfileIds.contains(42L))

        invokeTesting.invoke(vm, 42L, false)
        assertFalse((uiStateFlow.value as SingboxSettingsUiState).testingProfileIds.contains(42L))
    }

    private fun buildViewModel(): SingboxEngineSettingsViewModel {
        val context = mockk<Context>(relaxed = true)
        val dataStore = flowDataStore(MutableStateFlow(mutablePreferencesOf()))
        val groupDao = mockk<SubscriptionGroupDao>()
        val profileDao = mockk<ProxyProfileDao>()
        val proxyChainDao = mockk<ProxyChainDao>()
        val rawUpdater = mockk<ru.ozero.singboxsubscription.RawUpdater>(relaxed = true)
        val groupSeeder = mockk<ru.ozero.singboxsubscription.GroupSeeder>(relaxed = true)
        val probeService = mockk<SingboxProbeService>(relaxed = true)

        every { groupDao.getAllFlow() } returns emptyFlow()
        every { profileDao.getAllFlow() } returns emptyFlow()
        every { proxyChainDao.getAllFlow() } returns emptyFlow()

        return SingboxEngineSettingsViewModel(
            appContext = context,
            dataStore = dataStore,
            groupDao = groupDao,
            profileDao = profileDao,
            proxyChainDao = proxyChainDao,
            rawUpdater = rawUpdater,
            groupSeeder = groupSeeder,
            probeService = probeService,
        )
    }

    private fun flowDataStore(prefsFlow: MutableStateFlow<Preferences>): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = prefsFlow

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                return updated
            }
        }
}
