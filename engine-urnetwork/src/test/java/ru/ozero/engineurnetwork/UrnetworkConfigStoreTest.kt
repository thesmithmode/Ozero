package ru.ozero.engineurnetwork

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
        val pk = "27wAThHKVpd8c4r4GXNzMAev8byGUozA6RVayeZ7vHMM"
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

    @Test
    fun `config persists enum and boolean branches`() = runTest {
        val (store, _) = newStore()

        store.update {
            it.copy(
                windowType = UrnetworkWindowType.SPEED,
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = UrnetworkProvideControlMode.AUTO,
                provideNetworkMode = UrnetworkProvideNetworkMode.ALL,
            )
        }

        val snap = store.config().first()
        assertEquals(UrnetworkWindowType.SPEED, snap.windowType)
        assertEquals(true, snap.fixedIpSize)
        assertEquals(false, snap.allowDirect)
        assertEquals(false, snap.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.AUTO, snap.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, snap.provideNetworkMode)
    }

    @Test
    fun `cached locations roundtrip all buckets and optional fields`() = runTest {
        val (store, _) = newStore()
        val country = UrnetworkCachedLocation(
            name = "Germany",
            countryCode = "de",
            providerCount = 12,
            isStable = false,
            isStrongPrivacy = true,
        )
        val region = UrnetworkCachedLocation(
            name = "Bavaria",
            countryCode = "DE",
            region = "Bavaria",
            providerCount = 3,
        )
        val city = UrnetworkCachedLocation(
            name = "Munich",
            countryCode = "DE",
            region = "Bavaria",
            city = "Munich",
            providerCount = 2,
        )
        val best = UrnetworkCachedLocation(
            name = "Best",
            countryCode = "US",
            city = "New York",
            isStrongPrivacy = true,
        )

        store.setCachedLocations(
            countries = listOf(country),
            regions = listOf(region),
            cities = listOf(city),
            bestMatches = listOf(best),
        )

        val snap = store.config().first()
        assertEquals("DE", snap.cachedCountries.single().countryCode)
        assertEquals(false, snap.cachedCountries.single().isStable)
        assertEquals(true, snap.cachedCountries.single().isStrongPrivacy)
        assertEquals("Bavaria", snap.cachedRegions.single().region)
        assertEquals("Munich", snap.cachedCities.single().city)
        assertEquals("New York", snap.cachedBestMatches.single().city)
    }

    @Test
    fun `empty cached locations remove persisted json`() = runTest {
        val (store, _) = newStore()
        store.setCachedLocations(
            countries = listOf(UrnetworkCachedLocation("Germany", "DE")),
            regions = emptyList(),
            cities = emptyList(),
            bestMatches = emptyList(),
        )

        store.setCachedLocations(emptyList(), emptyList(), emptyList(), emptyList())

        val snap = store.config().first()
        assertTrue(snap.cachedCountries.isEmpty())
        assertTrue(snap.cachedRegions.isEmpty())
        assertTrue(snap.cachedCities.isEmpty())
        assertTrue(snap.cachedBestMatches.isEmpty())
    }

    @Test
    fun `cached locations are capped to max size`() = runTest {
        val (store, _) = newStore()
        val locations = (1..510).map { index ->
            UrnetworkCachedLocation(name = "L$index", countryCode = "US")
        }

        store.setCachedLocations(locations, emptyList(), emptyList(), emptyList())

        val cached = store.config().first().cachedCountries
        assertEquals(500, cached.size)
        assertEquals("L1", cached.first().name)
        assertEquals("L500", cached.last().name)
    }

    @Test
    fun `location selection normalization rejects invalid country only values`() {
        assertNull(UrnetworkLocationSelection("U1", null, null).normalized())
        assertNull(UrnetworkLocationSelection("USA", null, null).normalized())
        assertEquals(
            UrnetworkLocationSelection("DE", "Bavaria", "Munich"),
            UrnetworkLocationSelection(" de ", " Bavaria ", " Munich ").normalized(),
        )
        assertEquals("DE/Bavaria/Munich", UrnetworkLocationSelection("DE", "Bavaria", "Munich").summary())
        assertEquals("??", UrnetworkLocationSelection.EMPTY.summary())
    }

    @Test
    fun `persisted enum and boolean values reload through readConfig`() = runTest {
        val (store, ds) = newStore()
        store.setWindowType(UrnetworkWindowType.QUALITY)
        store.setFixedIpSize(true)
        store.setAllowDirect(false)
        store.setProvideEnabled(false)
        store.setProvideControlMode(UrnetworkProvideControlMode.AUTO)
        store.setProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)

        val reloaded = DataStoreUrnetworkConfigStore(ds)
        reloaded.update { it }
        val snap = reloaded.config().first()

        assertEquals(UrnetworkWindowType.QUALITY, snap.windowType)
        assertEquals(true, snap.fixedIpSize)
        assertEquals(false, snap.allowDirect)
        assertEquals(false, snap.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.AUTO, snap.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, snap.provideNetworkMode)
    }

    @Test
    fun `persisted credentials and selected location reload through readConfig`() = runTest {
        val (store, ds) = newStore()
        store.setWalletOverride("wallet-override")
        store.setByJwt("by.jwt")
        store.setByClientJwt("client.jwt")
        store.setDevicePubkey("device-pubkey")
        store.setDeviceNetworkName("device-network")
        store.setSelectedLocation(UrnetworkLocationSelection(" de ", " Bavaria ", " Munich "))

        val reloaded = DataStoreUrnetworkConfigStore(ds)
        reloaded.update { it }
        val snap = reloaded.config().first()

        assertEquals("wallet-override", snap.walletOverride)
        assertEquals("by.jwt", snap.byJwt)
        assertEquals("client.jwt", snap.byClientJwt)
        assertEquals("device-pubkey", snap.devicePubkey)
        assertEquals("device-network", snap.deviceNetworkName)
        assertEquals(UrnetworkLocationSelection("DE", "Bavaria", "Munich"), snap.selectedLocation)
    }

    @Test
    fun `config first after process recreation emits persisted values without update`() = runTest {
        val (store, ds) = newStore()
        store.update {
            it.copy(
                walletOverride = "wallet-override",
                byJwt = "by.jwt",
                byClientJwt = "client.jwt",
                devicePubkey = "device-pubkey",
                deviceNetworkName = "device-network",
                windowType = UrnetworkWindowType.SPEED,
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = UrnetworkProvideControlMode.AUTO,
                provideNetworkMode = UrnetworkProvideNetworkMode.ALL,
                selectedLocation = UrnetworkLocationSelection("DE", "Bavaria", "Munich"),
            )
        }

        val snap = DataStoreUrnetworkConfigStore(ds).config().first()

        assertEquals("wallet-override", snap.walletOverride)
        assertEquals("by.jwt", snap.byJwt)
        assertEquals("client.jwt", snap.byClientJwt)
        assertEquals("device-pubkey", snap.devicePubkey)
        assertEquals("device-network", snap.deviceNetworkName)
        assertEquals(UrnetworkWindowType.SPEED, snap.windowType)
        assertEquals(true, snap.fixedIpSize)
        assertEquals(false, snap.allowDirect)
        assertEquals(false, snap.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.AUTO, snap.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, snap.provideNetworkMode)
        assertEquals(UrnetworkLocationSelection("DE", "Bavaria", "Munich"), snap.selectedLocation)
    }

    @Test
    fun `blank persisted optional values are treated as absent after reload`() = runTest {
        val (_, ds) = newStore()
        ds.editRaw(
            "urnetwork_wallet_override" to " ",
            "urnetwork_by_jwt" to "",
            "urnetwork_by_client_jwt" to "   ",
            "urnetwork_device_pubkey" to "",
            "urnetwork_device_network_name" to " ",
            "urnetwork_selected_country_code" to "",
            "urnetwork_selected_region" to " ",
            "urnetwork_selected_city" to "",
        )

        val reloaded = DataStoreUrnetworkConfigStore(ds)
        reloaded.update { it }
        val snap = reloaded.config().first()

        assertNull(snap.walletOverride)
        assertNull(snap.byJwt)
        assertNull(snap.byClientJwt)
        assertNull(snap.devicePubkey)
        assertNull(snap.deviceNetworkName)
        assertEquals(UrnetworkLocationSelection.EMPTY, snap.selectedLocation)
    }

    @Test
    fun `malformed persisted cached locations reload as empty lists`() = runTest {
        val (_, ds) = newStore()
        ds.editRaw(
            "urnetwork_cached_countries" to "{",
            "urnetwork_cached_regions" to "not-json",
            "urnetwork_cached_cities" to "",
            "urnetwork_cached_best_matches" to "[]",
        )

        val reloaded = DataStoreUrnetworkConfigStore(ds)
        reloaded.update { snap ->
            assertTrue(snap.cachedCountries.isEmpty())
            assertTrue(snap.cachedRegions.isEmpty())
            assertTrue(snap.cachedCities.isEmpty())
            assertTrue(snap.cachedBestMatches.isEmpty())
            snap
        }
    }

    @Test
    fun `line cached locations preserve escaped tabs newlines and backslashes`() = runTest {
        val (store, ds) = newStore()
        val original = UrnetworkCachedLocation(
            name = "Name\twith\nslashes\\",
            countryCode = "de",
            region = "Region\tA",
            city = "City\nB",
            providerCount = 7,
            isStable = false,
            isStrongPrivacy = true,
        )

        store.setCachedLocations(listOf(original), emptyList(), emptyList(), emptyList())
        val reloaded = DataStoreUrnetworkConfigStore(ds).config().first().cachedCountries.single()

        assertEquals("Name\twith\nslashes\\", reloaded.name)
        assertEquals("DE", reloaded.countryCode)
        assertEquals("Region\tA", reloaded.region)
        assertEquals("City\nB", reloaded.city)
        assertEquals(7, reloaded.providerCount)
        assertEquals(false, reloaded.isStable)
        assertEquals(true, reloaded.isStrongPrivacy)
    }

    @Test
    fun `line cached locations skip invalid rows and keep valid rows`() = runTest {
        val (_, ds) = newStore()
        ds.editRaw(
            "urnetwork_cached_countries" to listOf(
                "",
                "MissingCode",
                "Short\tD",
                "Good\tde\t\tBerlin\t9\tfalse\ttrue",
                "BadProviders\tus\t\t\tNaN\tbad\tbad",
            ).joinToString("\n"),
        )

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedCountries

        assertEquals(2, cached.size)
        assertEquals("Good", cached[0].name)
        assertEquals("DE", cached[0].countryCode)
        assertEquals(9, cached[0].providerCount)
        assertEquals(false, cached[0].isStable)
        assertEquals(true, cached[0].isStrongPrivacy)
        assertEquals("BadProviders", cached[1].name)
        assertEquals("US", cached[1].countryCode)
        assertEquals(0, cached[1].providerCount)
        assertEquals(true, cached[1].isStable)
        assertEquals(false, cached[1].isStrongPrivacy)
    }

    @Test
    fun `line cached locations are capped during raw reload`() = runTest {
        val (_, ds) = newStore()
        val raw = (1..510).joinToString("\n") { index -> "L$index\tus" }

        ds.editRaw("urnetwork_cached_best_matches" to raw)

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedBestMatches
        assertEquals(500, cached.size)
        assertEquals("L1", cached.first().name)
        assertEquals("L500", cached.last().name)
    }

    @Test
    fun `invalid persisted enum raw values fall back to defaults`() = runTest {
        val (_, ds) = newStore()
        ds.editRaw(
            "urnetwork_window_type" to "bad-window",
            "urnetwork_provide_control_mode" to "bad-control",
            "urnetwork_provide_network_mode" to "bad-network",
        )

        val snap = DataStoreUrnetworkConfigStore(ds).config().first()

        assertEquals(UrnetworkWindowType.AUTO, snap.windowType)
        assertEquals(UrnetworkProvideControlMode.ALWAYS, snap.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.WIFI, snap.provideNetworkMode)
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state

        suspend fun editRaw(vararg values: Pair<String, String>) {
            updateData { prefs ->
                mutablePreferencesOf(
                    *prefs.asMap()
                        .mapNotNull { (key, value) -> (value as? String)?.let { key.name to it } }
                        .plus(values)
                        .map { (key, value) -> stringPreferencesKey(key) to value }
                        .toTypedArray(),
                ).apply {
                    values.forEach { (key, value) ->
                        this[stringPreferencesKey(key)] = value
                    }
                }
            }
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
