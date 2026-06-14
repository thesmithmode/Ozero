package ru.ozero.app.ui.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeServerDao
    private lateinit var viewModel: ServersViewModel

    private val sample = listOf(
        server("a", "RU", "entry"),
        server("b", "DE", "exit"),
        server("c", "NL", "single"),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeServerDao()
        viewModel = ServersViewModel(dao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty servers gives Empty state`() = runTest {
        advanceUntilIdle()
        assertIs<ServersUiState.Empty>(viewModel.uiState.value)
    }

    @Test
    fun `loaded servers gives Content`() = runTest {
        dao.emit(sample)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<ServersUiState.Content>(state)
        assertEquals(3, state.servers.size)
        assertNull(state.entryId)
        assertNull(state.exitId)
    }

    @Test
    fun `onEntrySelect sets entry id only`() = runTest {
        dao.emit(sample)
        advanceUntilIdle()

        viewModel.onEntrySelect("a")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ServersUiState.Content
        assertEquals("a", state.entryId)
        assertNull(state.exitId)
    }

    @Test
    fun `canSave false when only one selected`() = runTest {
        dao.emit(sample)
        advanceUntilIdle()
        viewModel.onEntrySelect("a")
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as ServersUiState.Content).canSave)
    }

    @Test
    fun `canSave false when entry equals exit`() = runTest {
        dao.emit(sample)
        advanceUntilIdle()

        viewModel.onEntrySelect("a")
        viewModel.onExitSelect("a")
        advanceUntilIdle()

        assertFalse((viewModel.uiState.value as ServersUiState.Content).canSave)
    }

    @Test
    fun `content resolves selected entry exit and can save distinct pair`() = runTest {
        dao.emit(sample)
        advanceUntilIdle()

        viewModel.onEntrySelect("a")
        viewModel.onExitSelect("b")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ServersUiState.Content
        assertEquals("a", state.entry?.id)
        assertEquals("b", state.exit?.id)
        assertTrue(state.canSave)
    }

    @Test
    fun `onSavePair upserts both entries with cross pairId`() = runTest {
        dao.emit(sample)
        advanceUntilIdle()

        viewModel.onEntrySelect("a")
        viewModel.onExitSelect("b")
        advanceUntilIdle()
        viewModel.onSavePair()
        advanceUntilIdle()

        val byId = dao.upserts.associateBy { it.id }
        assertEquals("b", byId["a"]?.pairId)
        assertEquals("a", byId["b"]?.pairId)
    }

    @Test
    fun `onClearPair clears pairId on selected entries`() = runTest {
        val paired = listOf(
            server("a", "RU", "entry").copy(pairId = "b"),
            server("b", "DE", "exit").copy(pairId = "a"),
        )
        dao.emit(paired)
        advanceUntilIdle()

        viewModel.onEntrySelect("a")
        viewModel.onExitSelect("b")
        advanceUntilIdle()

        viewModel.onClearPair()
        advanceUntilIdle()

        assertTrue(dao.upserts.all { it.pairId == null })
    }

    private fun server(id: String, country: String, role: String) =
        ServerEntity(
            id = id,
            country = country,
            role = role,
            protocol = "vless",
            uri = "vless://$id",
            port = 443,
        )

    private class FakeServerDao : ServerDao {
        private val flow = MutableStateFlow<List<ServerEntity>>(emptyList())
        val upserts = mutableListOf<ServerEntity>()

        fun emit(servers: List<ServerEntity>) {
            flow.value = servers
        }

        override suspend fun upsert(server: ServerEntity) {
            upserts += server
            flow.value = flow.value.filterNot { it.id == server.id } + server
        }

        override suspend fun upsertAll(servers: List<ServerEntity>) {
            servers.forEach { upsert(it) }
        }

        override fun observeAll(): Flow<List<ServerEntity>> = flow.asStateFlow()

        override suspend fun getLiveServers(): List<ServerEntity> =
            flow.value.filter { it.isAlive }

        override suspend fun getAllServers(): List<ServerEntity> = flow.value

        override suspend fun findById(id: String): ServerEntity? =
            flow.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: String) {
            flow.value = flow.value.filterNot { it.id == id }
        }

        override suspend fun deleteAll() {
            flow.value = emptyList()
        }

        override suspend fun setAlive(id: String, alive: Boolean, ts: Long) {
            flow.value = flow.value.map { if (it.id == id) it.copy(isAlive = alive, lastCheckedAt = ts) else it }
        }
    }
}
