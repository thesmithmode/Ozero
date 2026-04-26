package ru.ozero.coresubscriptions.harvester

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveProberTest {

    private lateinit var dao: FakeServerDao
    private lateinit var prober: LiveProber
    private var serverSocket: ServerSocket? = null

    @BeforeEach
    fun setUp() {
        dao = FakeServerDao()
        prober = LiveProber(dao, now = { 1234L })
    }

    @AfterEach
    fun tearDown() {
        serverSocket?.close()
    }

    @Test
    fun `live server is marked alive`() = runTest {
        serverSocket = ServerSocket(0) // any free port
        val port = serverSocket!!.localPort
        val srv = vless(id = "s1", host = "127.0.0.1", port = port)
        val stats = prober.probeAll(listOf(srv))
        assertEquals(1, stats.live)
        assertEquals(0, stats.dead)
        assertTrue(dao.aliveCalls["s1"]?.first == true)
        assertEquals(1234L, dao.aliveCalls["s1"]?.second)
    }

    @Test
    fun `dead server is marked dead`() = runTest {
        // Закрытый порт на localhost (заведомо недоступен)
        val srv = vless(id = "s2", host = "127.0.0.1", port = 1) // privileged, никто не слушает
        val stats = prober.probeAll(listOf(srv))
        assertEquals(0, stats.live)
        assertEquals(1, stats.dead)
        assertFalse(dao.aliveCalls["s2"]?.first ?: true)
    }

    @Test
    fun `malformed URI marked dead without crash`() = runTest {
        val srv = ServerEntity(
            id = "broken",
            country = "??",
            role = "single",
            protocol = "vless",
            uri = "not-a-uri",
            port = 443,
        )
        val stats = prober.probeAll(listOf(srv))
        assertEquals(1, stats.dead)
        assertFalse(dao.aliveCalls["broken"]?.first ?: true)
    }

    @Test
    fun `empty list returns zero stats`() = runTest {
        val stats = prober.probeAll(emptyList())
        assertEquals(0, stats.total)
        assertEquals(0, stats.live)
        assertEquals(0, stats.dead)
        assertTrue(dao.aliveCalls.isEmpty())
    }

    private fun vless(id: String, host: String, port: Int) = ServerEntity(
        id = id,
        country = "??",
        role = "single",
        protocol = "vless",
        uri = "vless://uuid@$host:$port?security=reality#$id",
        port = port,
    )

    private class FakeServerDao : ServerDao {
        val aliveCalls = mutableMapOf<String, Pair<Boolean, Long>>()
        override suspend fun upsert(server: ServerEntity) {}
        override suspend fun upsertAll(servers: List<ServerEntity>) {}
        override fun observeAll(): Flow<List<ServerEntity>> = throw NotImplementedError()
        override suspend fun getLiveServers(): List<ServerEntity> = emptyList()
        override suspend fun findById(id: String): ServerEntity? = null
        override suspend fun deleteById(id: String) {}
        override suspend fun deleteAll() {}
        override suspend fun setAlive(id: String, alive: Boolean, ts: Long) {
            aliveCalls[id] = alive to ts
        }
    }
}
