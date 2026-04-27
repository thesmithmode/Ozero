package ru.ozero.coresubscriptions.harvester

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicProxyHarvesterTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeServerDao
    private lateinit var harvester: PublicProxyHarvester

    private val sampleVless =
        "vless://uuid-1@example.com:443?encryption=none&security=reality&" +
            "pbk=key&fp=chrome&sni=youtube.com&type=tcp&flow=xtls-rprx-vision#sample"

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        dao = FakeServerDao()
        harvester = PublicProxyHarvester(OkHttpClient(), dao)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `LINES format parses URI per line and upserts`() = runTest {
        server.enqueue(MockResponse().setBody("$sampleVless\n# comment\n\n"))
        val src = source(SourceFormat.LINES)
        val r = harvester.harvest(listOf(src))
        assertEquals(1, r.totalParsed)
        assertEquals(1, dao.inserted.size)
        assertEquals("vless", dao.inserted.first().protocol)
    }

    @Test
    fun `BASE64_LINES format decodes and parses`() = runTest {
        val encoded = Base64.getEncoder().encodeToString("$sampleVless\n".toByteArray())
        server.enqueue(MockResponse().setBody(encoded))
        val r = harvester.harvest(listOf(source(SourceFormat.BASE64_LINES)))
        assertEquals(1, r.totalParsed)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `JSON_ARRAY format parses servers array`() = runTest {
        server.enqueue(MockResponse().setBody("""{"servers": ["$sampleVless"]}"""))
        val r = harvester.harvest(listOf(source(SourceFormat.JSON_ARRAY)))
        assertEquals(1, r.totalParsed)
    }

    @Test
    fun `non-200 source skipped without crashing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val r = harvester.harvest(listOf(source(SourceFormat.LINES)))
        assertEquals(0, r.totalParsed)
        assertTrue(r.perSource.first().error?.contains("503") == true)
    }

    @Test
    fun `empty harvest does not touch dao (network outage protection)`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = harvester.harvest(listOf(source(SourceFormat.LINES)))
        assertEquals(0, dao.inserted.size)
        assertEquals(0, r.totalParsed)
        assertEquals(1, r.failedSources)
    }

    @Test
    fun `dedupes identical URIs from multiple sources by stable id`() = runTest {
        server.enqueue(MockResponse().setBody(sampleVless))
        server.enqueue(MockResponse().setBody(sampleVless))
        val r = harvester.harvest(
            listOf(source(SourceFormat.LINES, id = "a"), source(SourceFormat.LINES, id = "b")),
        )
        assertEquals(2, r.totalParsed)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `unknown scheme dropped silently`() = runTest {
        server.enqueue(MockResponse().setBody("foo://broken\n$sampleVless"))
        val r = harvester.harvest(listOf(source(SourceFormat.LINES)))
        assertEquals(1, r.totalParsed)
        assertEquals(1, dao.inserted.size)
    }

    private fun source(fmt: SourceFormat, id: String = "test") = PublicProxySource(
        id = id,
        url = server.url("/").toString(),
        format = fmt,
    )

    private class FakeServerDao : ServerDao {
        val inserted = mutableListOf<ServerEntity>()
        override suspend fun upsert(server: ServerEntity) {
            inserted += server
        }
        override suspend fun upsertAll(servers: List<ServerEntity>) {
            inserted += servers
        }
        override fun observeAll(): Flow<List<ServerEntity>> = throw NotImplementedError()
        override suspend fun getLiveServers(): List<ServerEntity> = inserted.toList()
        override suspend fun findById(id: String): ServerEntity? =
            inserted.firstOrNull { it.id == id }
        override suspend fun deleteById(id: String) {
            inserted.removeAll { it.id == id }
        }
        override suspend fun deleteAll() = inserted.clear()
        override suspend fun setAlive(id: String, alive: Boolean, ts: Long) {
            inserted.replaceAll { if (it.id == id) it.copy(isAlive = alive, lastCheckedAt = ts) else it }
        }
    }
}
