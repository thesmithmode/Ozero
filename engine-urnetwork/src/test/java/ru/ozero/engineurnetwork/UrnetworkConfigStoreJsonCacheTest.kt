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

class UrnetworkConfigStoreJsonCacheTest {

    @Test
    fun `legacy json cache keeps valid locations and skips malformed entries`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.editRaw(
            "urnetwork_cached_countries" to """
                [
                  {"name":"Germany","code":"de","region":null,"city":"","providers":12,"stable":false,"privacy":true},
                  {"name":"","code":"us"},
                  {"name":"InvalidCode","code":"usa"},
                  null,
                  {"name":"NoCode"}
                ]
            """.trimIndent(),
        )

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedCountries

        assertEquals(1, cached.size)
        assertEquals("Germany", cached.single().name)
        assertEquals("DE", cached.single().countryCode)
        assertNull(cached.single().region)
        assertNull(cached.single().city)
        assertEquals(12, cached.single().providerCount)
        assertEquals(false, cached.single().isStable)
        assertEquals(true, cached.single().isStrongPrivacy)
    }

    @Test
    fun `legacy json cache reads optional region city and defaults booleans`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.editRaw(
            "urnetwork_cached_best_matches" to """
                [
                  {"name":"Berlin","code":"de","region":"Berlin","city":"Berlin"}
                ]
            """.trimIndent(),
        )

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedBestMatches.single()

        assertEquals("Berlin", cached.name)
        assertEquals("DE", cached.countryCode)
        assertEquals("Berlin", cached.region)
        assertEquals("Berlin", cached.city)
        assertEquals(0, cached.providerCount)
        assertEquals(true, cached.isStable)
        assertEquals(false, cached.isStrongPrivacy)
    }

    @Test
    fun `legacy json cache malformed arrays fall back to empty lists`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.editRaw(
            "urnetwork_cached_regions" to "[",
            "urnetwork_cached_cities" to """[{"name":"Bad","code":null}]""",
        )

        val snap = DataStoreUrnetworkConfigStore(ds).config().first()

        assertTrue(snap.cachedRegions.isEmpty())
        assertTrue(snap.cachedCities.isEmpty())
    }

    @Test
    fun `legacy json cache accepts escaped text negative providers and default flags`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.editRaw(
            "urnetwork_cached_regions" to """
                [
                  {"name":"North \"Quoted\"","code":"nl","region":"North\\West","providers":0},
                  {"name":"Negative","code":"br","providers":-1},
                  {"name":"NullFlags","code":"pt","stable":null,"privacy":null}
                ]
            """.trimIndent(),
        )

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedRegions

        assertEquals(3, cached.size)
        assertEquals("North \"Quoted\"", cached[0].name)
        assertEquals("North\\West", cached[0].region)
        assertEquals(0, cached[0].providerCount)
        assertEquals("BR", cached[1].countryCode)
        assertEquals(-1, cached[1].providerCount)
        assertEquals("PT", cached[2].countryCode)
        assertEquals(true, cached[2].isStable)
        assertEquals(false, cached[2].isStrongPrivacy)
    }

    @Test
    fun `legacy json cache rejects blank names and keeps unknown escapes raw`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.editRaw(
            "urnetwork_cached_cities" to """
                [
                  {"name":"Broken","code":"de","city":"bad\q"},
                  {"name":"   ","code":"fr","city":"Paris"},
                  {"name":"Valid","code":"es","city":"Madrid"}
                ]
            """.trimIndent(),
        )

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedCities

        assertEquals(2, cached.size)
        assertEquals("Broken", cached[0].name)
        assertEquals("""bad\q""", cached[0].city)
        assertEquals("Valid", cached[1].name)
        assertEquals("Madrid", cached[1].city)
    }

    @Test
    fun `line cache roundtrip escapes fields normalizes codes and trims empty lists`() = runTest {
        val ds = FakePreferencesDataStore()
        val store = DataStoreUrnetworkConfigStore(ds)
        val locations = listOf(
            UrnetworkCachedLocation(
                name = "North\tQuoted\\Name",
                countryCode = "de",
                region = "Line\nRegion",
                city = "",
                providerCount = 7,
                isStable = false,
                isStrongPrivacy = true,
            ),
        )

        store.update {
            it.copy(
                cachedCountries = locations,
                cachedRegions = locations,
                cachedCities = emptyList(),
                cachedBestMatches = locations,
            )
        }

        val snap = store.config().first()

        assertEquals("North\tQuoted\\Name", snap.cachedCountries.single().name)
        assertEquals("DE", snap.cachedCountries.single().countryCode)
        assertEquals("Line\nRegion", snap.cachedCountries.single().region)
        assertNull(snap.cachedCountries.single().city)
        assertEquals(7, snap.cachedCountries.single().providerCount)
        assertEquals(false, snap.cachedCountries.single().isStable)
        assertEquals(true, snap.cachedCountries.single().isStrongPrivacy)
        assertEquals("DE", snap.cachedRegions.single().countryCode)
        assertTrue(snap.cachedCities.isEmpty())
        assertEquals("DE", snap.cachedBestMatches.single().countryCode)
    }

    @Test
    fun `line cache skips invalid rows and defaults malformed optional fields`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.editRaw(
            "urnetwork_cached_countries" to listOf(
                "",
                "\tde",
                "BadCode\tdeu",
                "Defaults\tbr\t\t\tNaN\tmaybe\tnope",
                "TrailingSlash\tfr\\\\",
            ).joinToString("\n"),
        )

        val cached = DataStoreUrnetworkConfigStore(ds).config().first().cachedCountries

        assertEquals(1, cached.size)
        assertEquals("Defaults", cached[0].name)
        assertEquals("BR", cached[0].countryCode)
        assertEquals(0, cached[0].providerCount)
        assertEquals(true, cached[0].isStable)
        assertEquals(false, cached[0].isStrongPrivacy)
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
                )
            }
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
