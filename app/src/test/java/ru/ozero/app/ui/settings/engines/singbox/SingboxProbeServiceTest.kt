package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.delay
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(profile))

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

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(unsupported, supported))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[8L])
        assertEquals(7L, prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect marks corrupted bean as failed and never calls probe`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val corrupted = ProxyProfile(
            id = 17L,
            groupId = 1L,
            name = "corrupted",
            beanBlob = byteArrayOf(1, 2, 3),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )
        val probe = CountingProfileProbe()
        val events = mutableListOf<Pair<Long, Boolean>>()

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(corrupted)) { id, testing ->
            events += id to testing
        }

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[17L])
        assertEquals(0, probe.calls.get())
        assertTrue(events.isEmpty())
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect rejects tcp-only fake when routed probe fails`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val probe = CountingProfileProbe()

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val tcpOnly = makeProfile(id = 7L, host = "127.0.0.1", port = server.localPort)

            SingboxProbeService(dao, dataStore, probe)
                .probeAndAutoSelect(listOf(tcpOnly))
        }

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[7L])
        assertEquals(0, probe.calls.get())
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect does not overwrite latency when singbox runtime is busy`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profile = makeProfile(id = 7L, host = "busy.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("busy.example:443" to LATENCY_SKIPPED_ACTIVE_RUNTIME)),
        )
            .probeAndAutoSelect(listOf(profile))

        assertNull(dao.latencies[7L])
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect limits active profile probes and reports per profile testing state`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profiles = (1L..25L).map { id -> makeProfile(id = id, host = "probe-$id.example", port = 443) }
        val testingNow = AtomicInteger(0)
        val maxTestingNow = AtomicInteger(0)
        val testedIds = ConcurrentHashMap.newKeySet<Long>()

        SingboxProbeService(dao, dataStore, TrackingProfileProbe())
            .probeAndAutoSelect(profiles) { profileId, isTesting ->
                if (isTesting) {
                    testedIds.add(profileId)
                    val current = testingNow.incrementAndGet()
                    maxTestingNow.updateAndGet { previous -> maxOf(previous, current) }
                } else {
                    testingNow.decrementAndGet()
                }
            }

        assertEquals(profiles.map { it.id }.toSet(), testedIds)
        assertTrue(
            maxTestingNow.get() <= SingboxProbeService.MAX_CONCURRENT_PROFILE_PROBES,
            "profile testing UI state must never show more than 10 active probes",
        )
    }

    @Test
    fun `probeAndAutoSelect breaks equal latency ties by input order not completion order`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val first = makeProfile(id = 1L, host = "first.example", port = 443)
        val second = makeProfile(id = 2L, host = "second.example", port = 443)

        val probe = object : SingboxProfileProbe {
            override suspend fun probeLatencyMs(bean: AbstractBean): Int {
                when (bean.serverAddress) {
                    "first.example" -> delay(50)
                    "second.example" -> delay(0)
                }
                return 42
            }
        }

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(first, second))

        assertEquals(1L, prefsFlow.value[selectedProfileKey])
        assertEquals(42, dao.latencies[1L])
        assertEquals(42, dao.latencies[2L])
    }

    @Test
    fun `probeAndAutoSelect with empty profiles leaves prefs and latencies unchanged`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        SingboxProbeService(dao, dataStore, FakeProfileProbe(emptyMap())).probeAndAutoSelect(emptyList())

        assertTrue(dao.latencies.isEmpty())
        assertNull(prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
    }

    @Test
    fun `probeAndAutoSelect leaves selection unset when all probes fail`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val first = makeProfile(id = 1L, host = "first.example", port = 443)
        val second = makeProfile(id = 2L, host = "second.example", port = 443)

        SingboxProbeService(dao, dataStore, FakeProfileProbe(emptyMap())).probeAndAutoSelect(listOf(first, second))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[1L])
        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[2L])
        assertNull(prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
    }

    @Test
    fun `probeAndAutoSelect ignores negative latency when another profile succeeds`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val failed = makeProfile(id = 1L, host = "failed.example", port = 443)
        val successful = makeProfile(id = 2L, host = "ok.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("failed.example:443" to -5, "ok.example:443" to 33)),
        ).probeAndAutoSelect(listOf(failed, successful))

        assertEquals(-5, dao.latencies[1L])
        assertEquals(33, dao.latencies[2L])
        assertEquals(2L, prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect selects fastest successful profile and writes bean blob`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val slower = makeProfile(id = 1L, host = "slow.example", port = 443)
        val faster = makeProfile(id = 2L, host = "fast.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("slow.example:443" to 100, "fast.example:443" to 10)),
        ).probeAndAutoSelect(listOf(slower, faster))

        assertEquals(2L, prefsFlow.value[selectedProfileKey])
        assertTrue(faster.beanBlob.contentEquals(prefsFlow.value[beanKey]))
        assertEquals(10, dao.latencies[2L])
    }

    @Test
    fun `probeAndAutoSelect reports testing false when runtime skip short-circuits profile`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val events = mutableListOf<Pair<Long, Boolean>>()
        val profile = makeProfile(id = 5L, host = "busy.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("busy.example:443" to LATENCY_SKIPPED_ACTIVE_RUNTIME)),
        ).probeAndAutoSelect(listOf(profile)) { id, testing -> events += id to testing }

        assertEquals(listOf(5L to true, 5L to false), events)
        assertTrue(dao.latencies.isEmpty())
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
        val latencies = ConcurrentHashMap<Long, Int>()

        override suspend fun updateLatency(id: Long, latency: Int) {
            latencies[id] = latency
        }

        override suspend fun insert(profile: ProxyProfile): Long = profile.id
        override suspend fun insertAll(profiles: List<ProxyProfile>) = Unit
        override suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long> =
            profiles.map { it.id.takeIf { id -> id != 0L } ?: 1L }
        override suspend fun getById(id: Long): ProxyProfile? = null
        override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getAllLimitedFlow(limit: Int): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getAutoCandidatesFlow(limit: Int): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> = emptyList()
        override suspend fun getByGroupIdLimited(groupId: Long, limit: Int): List<ProxyProfile> = emptyList()
        override suspend fun getAutoCandidatesByGroupId(groupId: Long, limit: Int): List<ProxyProfile> =
            emptyList()
        override suspend fun deleteByGroupId(groupId: Long) = Unit
        override suspend fun getIdsByGroupId(groupId: Long): List<Long> = emptyList()
        override suspend fun deleteByIds(ids: List<Long>) = Unit
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

    private class TrackingProfileProbe : SingboxProfileProbe {
        override suspend fun probeLatencyMs(bean: AbstractBean): Int {
            delay(10)
            return 1
        }
    }

    private class CountingProfileProbe : SingboxProfileProbe {
        val calls = AtomicInteger(0)
        override suspend fun probeLatencyMs(bean: AbstractBean): Int {
            calls.incrementAndGet()
            return 1
        }
    }
}
