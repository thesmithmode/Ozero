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
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SingboxProbeServiceTest {

    private val selectedProfileKey = longPreferencesKey("singbox_selected_profile_id")
    private val beanKey = byteArrayPreferencesKey("singbox_vless_bean")

    @Test
    fun `probeAndAutoSelect preserves auto-select mode while updating latency`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(
            mutablePreferencesOf(selectedProfileKey to SingboxEngine.SELECTED_AUTO),
        )
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        val profile = makeProfile(id = 7L, host = "probe.example", port = 443)
        val probe = FakeProfileProbe(mapOf("probe.example:443" to 24))

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(profile), this)

        assertEquals(SingboxEngine.SELECTED_AUTO, prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
        assertEquals(24, dao.latencies[7L])
    }

    @Test
    fun `probeAndAutoSelect skips unsupported transport before selecting fastest profile`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        val unsupported = makeProfile(id = 8L, host = "unsupported.example", port = 443, type = "splithttp")
        val supported = makeProfile(id = 7L, host = "supported.example", port = 443)
        val probe = FakeProfileProbe(mapOf("supported.example:443" to 9))

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(unsupported, supported), this)

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[8L])
        assertEquals(7L, prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect rejects tcp-only fake when routed probe fails`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val tcpOnly = makeProfile(id = 7L, host = "127.0.0.1", port = server.localPort)

            SingboxProbeService(dao, dataStore, FakeProfileProbe(emptyMap()))
                .probeAndAutoSelect(listOf(tcpOnly), this)
        }

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[7L])
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    private fun makeProfile(
        id: Long,
        host: String,
        port: Int,
        type: String = "tcp",
    ): ProxyProfile =
        ProxyProfile(
            id = id,
            groupId = 1L,
            name = host,
            beanBlob = KryoSerializer.serialize(
                VLESSBean().apply {
                    serverAddress = host
                    serverPort = port
                    this.type = type
                },
            ),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )

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

    private class FakeProfileProbe(
        private val latenciesByAddress: Map<String, Int>,
    ) : SingboxProfileProbe {
        override suspend fun probeLatencyMs(bean: AbstractBean): Int =
            latenciesByAddress[bean.displayAddress()] ?: SingboxProbeService.LATENCY_FAILED
    }
}
