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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkEngineSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeUrnetworkConfigStore
    private lateinit var vm: UrnetworkEngineSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeUrnetworkConfigStore()
        vm = UrnetworkEngineSettingsViewModel(store)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init читает текущий wallet и consent state из store`() = runTest {
        store.setOverrideRaw("AAAAbbbbCCCCdddd1111222233334444555566667777")
        store.setConsentRaw(true)
        advanceUntilIdle()
        val s = vm.uiState.value as UrnetworkSettingsUiState.Content
        assertEquals("AAAAbbbbCCCCdddd1111222233334444555566667777", s.currentWallet)
        assertFalse(s.isUsingPreset)
        assertTrue(s.consentGranted)
    }

    @Test
    fun `init при null override НЕ раскрывает PRESET_WALLET в UI state`() = runTest {
        advanceUntilIdle()
        val s = vm.uiState.value as UrnetworkSettingsUiState.Content
        assertEquals("", s.currentWallet)
        assertEquals("", s.editedWallet)
        assertTrue(s.isUsingPreset)
        assertFalse(s.consentGranted)
    }

    @Test
    fun `PRESET_WALLET не утекает в editedWallet ни в одном пути init`() = runTest {
        advanceUntilIdle()
        val s = vm.uiState.value as UrnetworkSettingsUiState.Content
        assertFalse(
            s.editedWallet.contains(UrnetworkDefaults.PRESET_WALLET),
            "PRESET_WALLET виден юзеру в поле — утечка дефолтного реферального кошелька в UI.",
        )
        assertFalse(
            s.currentWallet.contains(UrnetworkDefaults.PRESET_WALLET),
            "PRESET_WALLET exposed через currentWallet — утечка в UI.",
        )
    }

    @Test
    fun `onSaveWallet с пустой строкой сбрасывает override в null`() = runTest {
        store.setOverrideRaw("AAAAbbbbCCCCdddd1111222233334444555566667777")
        advanceUntilIdle()
        vm.onSaveWallet("")
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), store.setOverrideCalls)
    }

    @Test
    fun `onWalletChange обновляет UI state но не store`() = runTest {
        advanceUntilIdle()
        vm.onWalletChange("DRAFT_WALLET_TEXT_NOT_PERSISTED")
        val s = vm.uiState.value as UrnetworkSettingsUiState.Content
        assertEquals("DRAFT_WALLET_TEXT_NOT_PERSISTED", s.editedWallet)
        assertEquals(0, store.setOverrideCalls.size)
    }

    @Test
    fun `onSaveWallet валидный base58 вызывает setWalletOverride`() = runTest {
        advanceUntilIdle()
        val valid = "AAAAbbbbCCCCdddd1111222233334444555566667777"
        vm.onSaveWallet(valid)
        advanceUntilIdle()
        assertEquals(listOf<String?>(valid), store.setOverrideCalls)
    }

    @Test
    fun `onSaveWallet с PRESET_WALLET сбрасывает override в null`() = runTest {
        advanceUntilIdle()
        vm.onSaveWallet(UrnetworkDefaults.PRESET_WALLET)
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), store.setOverrideCalls)
    }

    @Test
    fun `onSaveWallet с invalid эмитит errorMessage и не зовёт store`() = runTest {
        advanceUntilIdle()
        vm.onSaveWallet("too_short")
        advanceUntilIdle()
        val s = vm.uiState.value as UrnetworkSettingsUiState.Content
        assertNotNull(s.errorMessage)
        assertEquals(0, store.setOverrideCalls.size)
    }

    @Test
    fun `onSaveWallet с символами вне base58 alphabet эмитит error`() = runTest {
        advanceUntilIdle()
        vm.onSaveWallet("0OIl0OIl0OIl0OIl0OIl0OIl0OIl0OIl0OIl0OIl")
        advanceUntilIdle()
        val s = vm.uiState.value as UrnetworkSettingsUiState.Content
        assertNotNull(s.errorMessage)
        assertEquals(0, store.setOverrideCalls.size)
    }

    @Test
    fun `onResetWallet сбрасывает override в null`() = runTest {
        store.setOverrideRaw("AAAAbbbbCCCCdddd1111222233334444555566667777")
        advanceUntilIdle()
        vm.onResetWallet()
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), store.setOverrideCalls)
    }

    @Test
    fun `onGrantConsent вызывает markConsentGranted`() = runTest {
        advanceUntilIdle()
        vm.onGrantConsent()
        advanceUntilIdle()
        assertEquals(1, store.markConsentCalls)
    }

    @Test
    fun `onRevokeConsent вызывает revokeConsent`() = runTest {
        store.setConsentRaw(true)
        advanceUntilIdle()
        vm.onRevokeConsent()
        advanceUntilIdle()
        assertEquals(1, store.revokeConsentCalls)
    }

    private class FakeUrnetworkConfigStore : UrnetworkConfigStore {
        private val override = MutableStateFlow<String?>(null)
        private val consent = MutableStateFlow(false)

        val setOverrideCalls = mutableListOf<String?>()
        var markConsentCalls: Int = 0
        var revokeConsentCalls: Int = 0

        fun setOverrideRaw(value: String?) {
            override.value = value
        }

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
