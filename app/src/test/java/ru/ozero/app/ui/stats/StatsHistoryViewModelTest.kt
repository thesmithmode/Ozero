package ru.ozero.app.ui.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StatsHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeSessionStatsDao
    private lateinit var vm: StatsHistoryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeSessionStatsDao()
        vm = StatsHistoryViewModel(dao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init подписывается на observeRecent с лимитом 30`() = runTest {
        advanceUntilIdle()
        assertEquals(30, dao.lastLimit, "HISTORY_LIMIT обязан быть 30 — UI scroll не справится с большим числом")
    }

    @Test
    fun `initial state — пустой список`() = runTest {
        advanceUntilIdle()
        assertEquals(emptyList(), vm.sessions.value)
    }

    @Test
    fun `обновления dao пропагируются в sessions StateFlow`() = runTest {
        advanceUntilIdle()
        val items = listOf(sample(1L), sample(2L))
        dao.flow.value = items
        advanceUntilIdle()
        assertEquals(items, vm.sessions.value)
    }

    @Test
    fun `последовательные обновления отражаются в sessions`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(sample(1L))
        advanceUntilIdle()
        assertEquals(1, vm.sessions.value.size)
        dao.flow.value = listOf(sample(1L), sample(2L), sample(3L))
        advanceUntilIdle()
        assertEquals(3, vm.sessions.value.size)
        dao.flow.value = emptyList()
        advanceUntilIdle()
        assertTrue(vm.sessions.value.isEmpty())
    }

    @Test
    fun `observeRecent вызывается ровно один раз (Eagerly)`() = runTest {
        advanceUntilIdle()
        assertEquals(1, dao.observeCalls, "observeRecent должен вызываться один раз — повторные подписки утечка")
    }

    private fun sample(id: Long) = SessionStatsEntity(
        id = id,
        engineId = "BYE_DPI",
        startedAt = id * 1000L,
        endedAt = id * 1000L + 5000L,
        rxBytes = 100L,
        txBytes = 200L,
        durationMs = 5000L,
        finalStatus = SessionStatsEntity.STATUS_DISCONNECTED,
    )

    private class FakeSessionStatsDao : SessionStatsDao {
        val flow = MutableStateFlow<List<SessionStatsEntity>>(emptyList())
        var observeCalls: Int = 0
        var lastLimit: Int = -1

        override fun observeRecent(limit: Int): Flow<List<SessionStatsEntity>> {
            observeCalls++
            lastLimit = limit
            return flow
        }

        override suspend fun insertStart(entity: SessionStatsEntity): Long = 0L

        override suspend fun updateEnd(
            id: Long,
            endedAt: Long,
            rxBytes: Long,
            txBytes: Long,
            durationMs: Long,
            finalStatus: String,
        ) = Unit

        override suspend fun deleteOlderThan(olderThan: Long): Int = 0
    }
}
