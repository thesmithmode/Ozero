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
import kotlin.test.assertEquals
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
    fun `init авто-грантит consent когда он не дан`() = runTest {
        store.setConsentRaw(false)
        val vm = UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        assertEquals(1, store.markConsentCalls)
        assertSame(UrnetworkSettingsUiState.Ready, vm.uiState.value)
    }

    @Test
    fun `init НЕ зовёт markConsentGranted если consent уже дан`() = runTest {
        store.setConsentRaw(true)
        val vm = UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        assertEquals(0, store.markConsentCalls)
        assertSame(UrnetworkSettingsUiState.Ready, vm.uiState.value)
    }

    @Test
    fun `init никогда не зовёт setWalletOverride — кошелёк юзеру невидим`() = runTest {
        store.setConsentRaw(false)
        UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        assertEquals(0, store.setOverrideCalls.size)
    }

    @Test
    fun `init никогда не зовёт revokeConsent — кнопка отзыва удалена из UI`() = runTest {
        store.setConsentRaw(true)
        UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        assertEquals(0, store.revokeConsentCalls)
    }

    @Test
    fun `PRESET_WALLET никогда не утекает в store через VM — sentinel против регрессии`() = runTest {
        UrnetworkEngineSettingsViewModel(store)
        advanceUntilIdle()
        assertEquals(
            emptyList<String?>(),
            store.setOverrideCalls,
            "VM не должен трогать walletOverride — preset зашит в store, юзер не видит и не редактирует.",
        )
        store.setOverrideCalls.forEach { value ->
            assert(value != UrnetworkDefaults.PRESET_WALLET) {
                "PRESET_WALLET записан через VM — утечка реферального кошелька в writable layer."
            }
        }
    }

    @Test
    fun `uiState стартует как Loading и переходит в Ready`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(store)
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertSame(UrnetworkSettingsUiState.Ready, vm.uiState.value)
    }

    private class FakeUrnetworkConfigStore : UrnetworkConfigStore {
        private val override = MutableStateFlow<String?>(null)
        private val consent = MutableStateFlow(false)

        val setOverrideCalls = mutableListOf<String?>()
        var markConsentCalls: Int = 0
        var revokeConsentCalls: Int = 0

        fun setConsentRaw(value: Boolean) {
            consent.value = value
        }

        override fun walletAddress(): Flow<String> =
            override.map { it ?: UrnetworkDefaults.PRESET_WALLET }
        override fun walletOverride(): Flow<String?> = override
        override suspend fun setWalletOverride(value: String?) {
            setOverrideCalls += value
            override.value = value
        }
        override fun consentGranted(): Flow<Boolean> = consent
        override suspend fun markConsentGranted() {
            markConsentCalls++
            consent.value = true
        }
        override suspend fun revokeConsent() {
            revokeConsentCalls++
            consent.value = false
        }
        private val byJwtFlow = MutableStateFlow<String?>(null)
        override fun byJwt(): Flow<String?> = byJwtFlow
        override suspend fun setByJwt(value: String?) {
            byJwtFlow.value = value
        }
    }
}
