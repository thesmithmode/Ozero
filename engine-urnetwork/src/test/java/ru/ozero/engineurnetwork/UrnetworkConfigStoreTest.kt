package ru.ozero.engineurnetwork

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrnetworkConfigStoreTest {

    private fun newStore(): Pair<DataStoreUrnetworkConfigStore, FakePreferencesDataStore> {
        val ds = FakePreferencesDataStore()
        return DataStoreUrnetworkConfigStore(ds) to ds
    }

    @Test
    fun `walletAddress по умолчанию равен PRESET_WALLET если override null`() = runTest {
        val (store, _) = newStore()
        val addr = store.walletAddress().first()
        assertEquals(UrnetworkDefaults.PRESET_WALLET, addr)
    }

    @Test
    fun `setWalletOverride сохраняет значение и walletAddress возвращает override`() = runTest {
        val (store, _) = newStore()
        val override = "EFGHabcd1111111111111111111111111111111111"
        store.setWalletOverride(override)
        assertEquals(override, store.walletAddress().first())
        assertEquals(override, store.walletOverride().first())
    }

    @Test
    fun `setWalletOverride(null) очищает override и возвращает PRESET_WALLET`() = runTest {
        val (store, _) = newStore()
        store.setWalletOverride("EFGHabcd1111111111111111111111111111111111")
        store.setWalletOverride(null)
        assertEquals(UrnetworkDefaults.PRESET_WALLET, store.walletAddress().first())
        assertNull(store.walletOverride().first())
    }

    @Test
    fun `consentGranted по умолчанию false`() = runTest {
        val (store, _) = newStore()
        assertFalse(store.consentGranted().first())
    }

    @Test
    fun `markConsentGranted ставит флаг в true`() = runTest {
        val (store, _) = newStore()
        store.markConsentGranted()
        assertTrue(store.consentGranted().first())
    }

    @Test
    fun `revokeConsent очищает флаг`() = runTest {
        val (store, _) = newStore()
        store.markConsentGranted()
        store.revokeConsent()
        assertFalse(store.consentGranted().first())
    }

    @Test
    fun `byJwt по умолчанию null`() = runTest {
        val (store, _) = newStore()
        assertNull(store.byJwt().first())
    }

    @Test
    fun `setByJwt сохраняет токен и byJwt возвращает его`() = runTest {
        val (store, _) = newStore()
        val jwt = "eyJhbGciOiJIUzI1NiJ9.payload.sig"
        store.setByJwt(jwt)
        assertEquals(jwt, store.byJwt().first())
    }

    @Test
    fun `setByJwt(null) очищает токен`() = runTest {
        val (store, _) = newStore()
        store.setByJwt("eyJabc.def.ghi")
        store.setByJwt(null)
        assertNull(store.byJwt().first())
    }

    @Test
    fun `setByJwt с пустой строкой сохраняется как null`() = runTest {
        val (store, _) = newStore()
        store.setByJwt("eyJabc.def.ghi")
        store.setByJwt("")
        assertNull(store.byJwt().first())
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
