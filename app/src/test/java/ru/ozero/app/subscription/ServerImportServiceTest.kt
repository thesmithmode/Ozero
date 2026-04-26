package ru.ozero.app.subscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerImportServiceTest {

    private val dao = FakeServerDao()
    private val service = ServerImportService(dao)

    @Test
    fun `import valid vless URI returns Ok and inserts entity`() = runTest {
        val uri = "vless://uuid-1234@example.com:443?encryption=none&" +
            "security=reality&pbk=publickey&fp=chrome&sni=example.com&" +
            "type=tcp&flow=xtls-rprx-vision#test-vless"
        val result = service.import(uri)
        val ok = assertIs<ServerImportService.ImportResult.Ok>(result)
        assertEquals("vless", ok.entity.protocol)
        assertEquals(443, ok.entity.port)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `import unknown scheme returns Error`() = runTest {
        val result = service.import("totally-unknown://host:443")
        val err = assertIs<ServerImportService.ImportResult.Error>(result)
        assertTrue(err.reason.isNotBlank())
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `import empty URI returns Error`() = runTest {
        val result = service.import("   ")
        assertIs<ServerImportService.ImportResult.Error>(result)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `import malformed vless URI returns Error and does not insert`() = runTest {
        val result = service.import("vless://broken")
        assertIs<ServerImportService.ImportResult.Error>(result)
        assertTrue(dao.inserted.isEmpty())
    }

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
    }
}
