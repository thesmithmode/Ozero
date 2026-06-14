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
