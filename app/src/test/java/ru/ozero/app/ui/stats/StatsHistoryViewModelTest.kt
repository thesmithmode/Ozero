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
    fun `init подписывается на observeRecent с лимитом 200`() = runTest {
        advanceUntilIdle()
        assertEquals(
            200,
            dao.lastLimit,
            "HISTORY_LIMIT обязан быть 200 — фильтрация client-side требует достаточно данных",
        )
    }

    @Test
    fun `initial state — пустой список`() = runTest {
        advanceUntilIdle()
        assertEquals(emptyList(), vm.sessions.value)
    }

    @Test
    fun `обновления dao пропагируются в sessions StateFlow с дефолтной сортировкой по времени desc`() = runTest {
        advanceUntilIdle()
        val items = listOf(sample(1L, startedAt = 100L), sample(2L, startedAt = 300L), sample(3L, startedAt = 200L))
        dao.flow.value = items
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 1L), vm.sessions.value.map { it.id })
    }

    @Test
    fun `setSort TIME_ASC меняет порядок на по возрастанию`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(sample(1L, startedAt = 100L), sample(2L, startedAt = 300L))
        vm.setSort(SessionSort.TIME_ASC)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.sessions.value.map { it.id })
    }

    @Test
    fun `setSort TRAFFIC_DESC сортирует по сумме rx плюс tx по убыванию`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(
            sample(1L, rxBytes = 100L, txBytes = 100L),
            sample(2L, rxBytes = 500L, txBytes = 0L),
            sample(3L, rxBytes = 50L, txBytes = 50L),
        )
        vm.setSort(SessionSort.TRAFFIC_DESC)
        advanceUntilIdle()
        assertEquals(listOf(2L, 1L, 3L), vm.sessions.value.map { it.id })
    }

    @Test
    fun `setSort DURATION_DESC сортирует по длительности по убыванию`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(
            sample(1L, durationMs = 1000L),
            sample(2L, durationMs = 5000L),
            sample(3L, durationMs = 2000L),
        )
        vm.setSort(SessionSort.DURATION_DESC)
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 1L), vm.sessions.value.map { it.id })
    }

    @Test
    fun `toggleEngineFilter оставляет только сессии с выбранным движком`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(
            sample(1L, engineId = "BYEDPI"),
            sample(2L, engineId = "WARP"),
            sample(3L, engineId = "BYEDPI"),
        )
        vm.toggleEngineFilter("BYEDPI")
        advanceUntilIdle()
        assertEquals(setOf("BYEDPI"), vm.filter.value.engines)
        assertEquals(listOf(3L, 1L), vm.sessions.value.map { it.id })
    }

    @Test
    fun `повторный toggleEngineFilter снимает фильтр`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(sample(1L, engineId = "BYEDPI"), sample(2L, engineId = "WARP"))
        vm.toggleEngineFilter("WARP")
        advanceUntilIdle()
        assertEquals(1, vm.sessions.value.size)
        vm.toggleEngineFilter("WARP")
        advanceUntilIdle()
        assertEquals(2, vm.sessions.value.size)
        assertTrue(vm.filter.value.engines.isEmpty())
    }

    @Test
    fun `setPeriod отсекает старые сессии за пределами окна`() = runTest {
        advanceUntilIdle()
        val now = System.currentTimeMillis()
        dao.flow.value = listOf(
            sample(1L, startedAt = now - 60_000L),
            sample(2L, startedAt = now - 8L * 24L * 60L * 60L * 1000L),
        )
        val dayMs = 24L * 60L * 60L * 1000L
        vm.setPeriod(dayMs)
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.sessions.value.map { it.id })
    }

    @Test
    fun `clearFilters сбрасывает engines и periodMs`() = runTest {
        advanceUntilIdle()
        vm.toggleEngineFilter("BYEDPI")
        vm.setPeriod(60_000L)
        advanceUntilIdle()
        vm.clearFilters()
        advanceUntilIdle()
        assertEquals(SessionFilter(), vm.filter.value)
    }

    @Test
    fun `availableEngines собирает уникальные engineId из dao`() = runTest {
        advanceUntilIdle()
        dao.flow.value = listOf(
            sample(1L, engineId = "BYEDPI"),
            sample(2L, engineId = "WARP"),
            sample(3L, engineId = "BYEDPI"),
        )
        advanceUntilIdle()
        assertEquals(listOf("BYEDPI", "WARP"), vm.availableEngines.value)
    }

    private fun sample(
        id: Long,
        engineId: String = "BYE_DPI",
        startedAt: Long = id * 1000L,
        durationMs: Long = 5000L,
        rxBytes: Long = 100L,
        txBytes: Long = 200L,
    ) = SessionStatsEntity(
        id = id,
        engineId = engineId,
        startedAt = startedAt,
        endedAt = startedAt + durationMs,
        rxBytes = rxBytes,
        txBytes = txBytes,
        durationMs = durationMs,
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
