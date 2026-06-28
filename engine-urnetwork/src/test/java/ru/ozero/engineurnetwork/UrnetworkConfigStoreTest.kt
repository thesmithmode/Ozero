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
import kotlin.test.assertNull

class UrnetworkConfigStoreTest {

    private fun newStore(): Pair<DataStoreUrnetworkConfigStore, FakePreferencesDataStore> {
        val ds = FakePreferencesDataStore()
        return DataStoreUrnetworkConfigStore(ds) to ds
    }

    @Test
    fun `walletAddress по умолчанию пустой если override null`() = runTest {
        val (store, _) = newStore()
        val addr = store.walletAddress().first()
        assertEquals("", addr)
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
    fun `setWalletOverride null очищает override и возвращает пустой walletAddress`() = runTest {
        val (store, _) = newStore()
        store.setWalletOverride("EFGHabcd1111111111111111111111111111111111")
        store.setWalletOverride(null)
        assertEquals("", store.walletAddress().first())
        assertNull(store.walletOverride().first())
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

    @Test
    fun `byClientJwt по умолчанию null`() = runTest {
        val (store, _) = newStore()
        assertNull(store.byClientJwt().first())
    }

    @Test
    fun `setByClientJwt сохраняет токен`() = runTest {
        val (store, _) = newStore()
        val cjwt = "client.eyJ.x.y"
        store.setByClientJwt(cjwt)
        assertEquals(cjwt, store.byClientJwt().first())
    }

    @Test
    fun `setByClientJwt(null) очищает токен`() = runTest {
        val (store, _) = newStore()
        store.setByClientJwt("c.j.w.t")
        store.setByClientJwt(null)
        assertNull(store.byClientJwt().first())
    }

    @Test
    fun `byJwt и byClientJwt хранятся независимо`() = runTest {
        val (store, _) = newStore()
        store.setByJwt("guest.tok")
        store.setByClientJwt("client.tok")
        assertEquals("guest.tok", store.byJwt().first())
        assertEquals("client.tok", store.byClientJwt().first())
    }

    @Test
    fun `devicePubkey по умолчанию null`() = runTest {
        val (store, _) = newStore()
        assertNull(store.devicePubkey().first())
    }

    @Test
    fun `setDevicePubkey persist и devicePubkey возвращает значение`() = runTest {
        val (store, _) = newStore()
        val pk = "device-pubkey"
        store.setDevicePubkey(pk)
        assertEquals(pk, store.devicePubkey().first())
    }

    @Test
    fun `setDevicePubkey(null) очищает`() = runTest {
        val (store, _) = newStore()
        store.setDevicePubkey("abc")
        store.setDevicePubkey(null)
        assertNull(store.devicePubkey().first())
    }

    @Test
    fun `setDevicePubkey пустая строка трактуется как null`() = runTest {
        val (store, _) = newStore()
        store.setDevicePubkey("abc")
        store.setDevicePubkey("")
        assertNull(store.devicePubkey().first())
    }

    @Test
    fun `deviceNetworkName по умолчанию null`() = runTest {
        val (store, _) = newStore()
        assertNull(store.deviceNetworkName().first())
    }

    @Test
    fun `setDeviceNetworkName persist и returns`() = runTest {
        val (store, _) = newStore()
        store.setDeviceNetworkName("n-abc")
        assertEquals("n-abc", store.deviceNetworkName().first())
    }

    @Test
    fun `devicePubkey и deviceNetworkName хранятся независимо от byJwt`() = runTest {
        val (store, _) = newStore()
        store.setByJwt("j")
        store.setDevicePubkey("p")
        store.setDeviceNetworkName("n")
        assertEquals("j", store.byJwt().first())
        assertEquals("p", store.devicePubkey().first())
        assertEquals("n", store.deviceNetworkName().first())
    }

    @Test
    fun `provideEnabled по умолчанию true`() = runTest {
        val (store, _) = newStore()
        assertEquals(true, store.provideEnabled().first())
    }

    @Test
    fun `setProvideEnabled(false) персистирует и provideEnabled возвращает false`() = runTest {
        val (store, _) = newStore()
        store.setProvideEnabled(false)
        assertEquals(false, store.provideEnabled().first())
    }

    @Test
    fun `setProvideEnabled(true) после false возвращает true`() = runTest {
        val (store, _) = newStore()
        store.setProvideEnabled(false)
        store.setProvideEnabled(true)
        assertEquals(true, store.provideEnabled().first())
    }

    @Test
    fun `selectedLocation по умолчанию EMPTY`() = runTest {
        val (store, _) = newStore()
        assertEquals(UrnetworkLocationSelection.EMPTY, store.selectedLocation().first())
    }

    @Test
    fun `setSelectedLocation сохраняет country uppercase trimmed`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection("  de  ", null, null))
        assertEquals("DE", store.selectedLocation().first().countryCode)
    }

    @Test
    fun `setSelectedLocation country=null очищает country`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection("US", null, null))
        store.setSelectedLocation(UrnetworkLocationSelection(null, null, null))
        assertNull(store.selectedLocation().first().countryCode)
    }

    @Test
    fun `setSelectedLocation пустая country трактуется как null`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection("US", null, null))
        store.setSelectedLocation(UrnetworkLocationSelection("   ", null, null))
        assertNull(store.selectedLocation().first().countryCode)
    }

    @Test
    fun `setSelectedLocation сохраняет region trimmed`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection(null, "  Bavaria  ", null))
        assertEquals("Bavaria", store.selectedLocation().first().region)
    }

    @Test
    fun `setSelectedLocation region=null очищает`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection(null, "Bavaria", null))
        store.setSelectedLocation(UrnetworkLocationSelection(null, null, null))
        assertNull(store.selectedLocation().first().region)
    }

    @Test
    fun `setSelectedLocation сохраняет city trimmed`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection(null, null, "  Munich  "))
        assertEquals("Munich", store.selectedLocation().first().city)
    }

    @Test
    fun `setSelectedLocation city=null очищает`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection(null, null, "Munich"))
        store.setSelectedLocation(UrnetworkLocationSelection(null, null, null))
        assertNull(store.selectedLocation().first().city)
    }

    @Test
    fun `country region city персистятся атомарно`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection("DE", "Bavaria", "Munich"))
        val snap = store.selectedLocation().first()
        assertEquals("DE", snap.countryCode)
        assertEquals("Bavaria", snap.region)
        assertEquals("Munich", snap.city)
    }

    @Test
    fun `setSelectedLocation частичное обновление сбрасывает остальные`() = runTest {
        val (store, _) = newStore()
        store.setSelectedLocation(UrnetworkLocationSelection("DE", "Bavaria", "Munich"))
        store.setSelectedLocation(UrnetworkLocationSelection("DE", null, "Munich"))
        val snap = store.selectedLocation().first()
        assertEquals("DE", snap.countryCode)
        assertNull(snap.region)
        assertEquals("Munich", snap.city)
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
