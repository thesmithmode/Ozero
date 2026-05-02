package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkDefaults
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkEngineSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeUrnetworkConfigStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeUrnetworkConfigStore()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState стартует как Loading и переходит в Ready`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(store)
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertSame(UrnetworkSettingsUiState.Ready, vm.uiState.value)
    }

    @Test
    fun `init никогда не зовёт setWalletOverride — кошелёк юзеру невидим`() = runTest {
        UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        assertSame(emptyList<String?>(), store.setOverrideCalls)
    }

    @Test
    fun `PRESET_WALLET никогда не утекает в store через VM — sentinel против регрессии`() = runTest {
        UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        store.setOverrideCalls.forEach { value ->
            assert(value != UrnetworkDefaults.PRESET_WALLET) {
                "PRESET_WALLET записан через VM — утечка реферального кошелька в writable layer."
            }
        }
    }

    private class FakeUrnetworkConfigStore : UrnetworkConfigStore {
        private val overrideFlow = MutableStateFlow<String?>(null)
        val setOverrideCalls = mutableListOf<String?>()

        override fun walletAddress(): Flow<String> =
            overrideFlow.map { it ?: UrnetworkDefaults.PRESET_WALLET }
        override fun walletOverride(): Flow<String?> = overrideFlow
        override suspend fun setWalletOverride(value: String?) {
            setOverrideCalls += value
            overrideFlow.value = value
        }
        override fun byJwt(): Flow<String?> = MutableStateFlow(null)
        override suspend fun setByJwt(value: String?) = Unit
    }
}
