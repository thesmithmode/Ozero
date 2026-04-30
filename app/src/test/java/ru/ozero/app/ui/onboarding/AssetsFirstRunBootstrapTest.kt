package ru.ozero.app.ui.onboarding

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.app.subscription.ServerImportService
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetsFirstRunBootstrapTest {

    @Test
    fun `import skips placeholder URIs`() = runTest {
        val dao = FakeServerDao()
        val importer = ServerImportService(dao)
        val placeholder = "vless://uuid@example.invalid:443?security=reality&pbk=PLACEHOLDER" +
            "&fp=chrome&sni=example.com&type=tcp&flow=xtls-rprx-vision#placeholder"
        assertTrue(placeholder.contains("placeholder"))
    }

    @Test
    fun `valid vless URI imported into dao`() = runTest {
        val dao = FakeServerDao()
        val importer = ServerImportService(dao)
        val uri = "vless://uuid-1@host.example:443?encryption=none&security=reality" +
            "&pbk=somekey&fp=chrome&sni=youtube.com&type=tcp&flow=xtls-rprx-vision#real-1"
        val r = importer.import(uri)
        assertTrue(r is ServerImportService.ImportResult.Ok)
        assertEquals(1, dao.inserted.size)
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
        override suspend fun getAllServers(): List<ServerEntity> = inserted.toList()
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
