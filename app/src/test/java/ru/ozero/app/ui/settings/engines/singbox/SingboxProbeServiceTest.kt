package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingboxProbeServiceTest {

    private val selectedProfileKey = longPreferencesKey("singbox_selected_profile_id")
    private val beanKey = byteArrayPreferencesKey("singbox_vless_bean")

    @Test
    fun `probeAndAutoSelect preserves auto-select mode without opening latency sockets`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(
            mutablePreferencesOf(selectedProfileKey to SingboxEngine.SELECTED_AUTO),
        )
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        val profile = ProxyProfile(
            id = 7L,
            groupId = 1L,
            name = "local",
            beanBlob = KryoSerializer.serialize(
                VLESSBean().apply {
                    serverAddress = "198.51.100.7"
                    serverPort = 443
                },
            ),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )

        SingboxProbeService(dao, dataStore).probeAndAutoSelect(listOf(profile))

        assertEquals(SingboxEngine.SELECTED_AUTO, prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
        assertTrue(dao.latencies.isEmpty())
    }

    @Test
    fun `probeAndAutoSelect skips unsupported transport before selecting known profile`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        val unsupported = ProxyProfile(
            id = 8L,
            groupId = 1L,
            name = "unsupported",
            beanBlob = KryoSerializer.serialize(
                VLESSBean().apply {
                    serverAddress = "198.51.100.8"
                    serverPort = 443
                    type = "splithttp"
                },
            ),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )
        val supported = ProxyProfile(
            id = 7L,
            groupId = 1L,
            name = "supported",
            beanBlob = KryoSerializer.serialize(
                VLESSBean().apply {
                    serverAddress = "198.51.100.7"
                    serverPort = 443
                    type = "tcp"
                },
            ),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )

        SingboxProbeService(dao, dataStore).probeAndAutoSelect(listOf(unsupported, supported))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[8L])
        assertEquals(7L, prefsFlow.value[selectedProfileKey])
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

    private class FakeProxyProfileDao : ProxyProfileDao {
        val latencies = mutableMapOf<Long, Int>()

        override suspend fun updateLatency(id: Long, latency: Int) {
            latencies[id] = latency
        }

        override suspend fun insert(profile: ProxyProfile): Long = profile.id
        override suspend fun insertAll(profiles: List<ProxyProfile>) = Unit
        override suspend fun getById(id: Long): ProxyProfile? = null
        override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> = emptyList()
        override suspend fun deleteByGroupId(groupId: Long) = Unit
        override suspend fun replaceForGroup(groupId: Long, profiles: List<ProxyProfile>) = Unit
        override suspend fun countByGroupId(groupId: Long): Int = 0
        override suspend fun update(profile: ProxyProfile) = Unit
        override suspend fun delete(profile: ProxyProfile) = Unit
    }
}
