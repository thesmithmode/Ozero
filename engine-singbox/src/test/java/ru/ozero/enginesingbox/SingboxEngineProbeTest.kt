package ru.ozero.enginesingbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ProbeResult
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingboxEngineProbeTest {

    @Test
    fun `probe rejects running runtime when routed probe fails and clears stale port`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = object : SingboxRoutedProbe {
            override suspend fun probeLatencyMs(socksPort: Int): Long = SingboxHttp204RoutedProbe.LATENCY_FAILED
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.probe()

        assertTrue(result is ProbeResult.Failure)
        assertTrue(result.reason.contains("routed probe"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `awaitReady fails when routed probe fails`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = object : SingboxRoutedProbe {
            override suspend fun probeLatencyMs(socksPort: Int): Long = SingboxHttp204RoutedProbe.LATENCY_FAILED
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.awaitReady()

        assertTrue(result is EnginePlugin.ReadyResult.Timeout)
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    private fun buildEngine(): SingboxEngine =
        SingboxEngine(
            context = mockk(relaxed = true),
            dataStore = fakeDataStore(),
            profileDao = fakeProfileDao(),
            proxyChainDao = fakeProxyChainDao(),
        )

    private fun fakeDataStore(): DataStore<Preferences> {
        val flow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(flow.value)
                flow.value = updated
                return updated
            }
        }
    }

    private fun fakeProfileDao(): ProxyProfileDao =
        object : ProxyProfileDao {
            override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> = emptyList()
            override suspend fun getById(id: Long): ProxyProfile? = null
            override suspend fun insert(profile: ProxyProfile): Long = profile.id
            override suspend fun insertAll(profiles: List<ProxyProfile>) = Unit
            override suspend fun deleteByGroupId(groupId: Long) = Unit
            override suspend fun replaceForGroup(groupId: Long, profiles: List<ProxyProfile>) = Unit
            override suspend fun updateLatency(id: Long, latency: Int) = Unit
            override suspend fun countByGroupId(groupId: Long): Int = 0
            override suspend fun update(profile: ProxyProfile) = Unit
            override suspend fun delete(profile: ProxyProfile) = Unit
        }

    private fun fakeProxyChainDao(): ProxyChainDao =
        object : ProxyChainDao {
            override fun getAllFlow(): Flow<List<ProxyChainStep>> = MutableStateFlow(emptyList())
            override suspend fun getAll(): List<ProxyChainStep> = emptyList()
            override suspend fun clear() = Unit
            override suspend fun insertAll(steps: List<ProxyChainStep>) = Unit
            override suspend fun replace(profileIds: List<Long>) = Unit
        }

    private fun SingboxEngine.setPrivateField(name: String, value: Any) {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private fun SingboxEngine.privateIntField(name: String): Int {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.getInt(this)
    }
}
