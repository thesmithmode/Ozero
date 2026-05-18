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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficStatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeSessionStatsDao

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeSessionStatsDao()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = TrafficStatsViewModel(dao)

    @Nested
    inner class InitialState {

        @Test
        fun `default timeframe is WEEK`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(TrafficTimeframe.WEEK, vm.timeframe.value)
        }

        @Test
        fun `initial sessions is empty`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(emptyList(), vm.sessions.value)
        }

        @Test
        fun `initial summary is all zeros`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(TrafficSummary(0L, 0L, 0, 0L), vm.summary.value)
        }

        @Test
        fun `initial sessionsExpanded is false`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertFalse(vm.sessionsExpanded.value)
        }
    }

    @Nested
    inner class TimeframeSwitch {

        @Test
        fun `setTimeframe DAY queries observeFrom with 24h window`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            val beforeCall = System.currentTimeMillis()
            vm.setTimeframe(TrafficTimeframe.DAY)
            advanceUntilIdle()
            val dayMs = 24L * 3_600_000L
            assertTrue(dao.lastObserveFromSince >= beforeCall - dayMs - 1_000L)
            assertTrue(dao.lastObserveFromSince <= beforeCall)
        }

        @Test
        fun `setTimeframe ALL uses observeAll not observeFrom`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.setTimeframe(TrafficTimeframe.ALL)
            advanceUntilIdle()
            assertTrue(dao.observeAllCalled)
        }

        @Test
        fun `setTimeframe clears engine filter`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(sample(1L, engineId = "byedpi"))
            vm.toggleEngineFilter("byedpi")
            advanceUntilIdle()
            assertTrue(vm.engineFilter.value.isNotEmpty())
            vm.setTimeframe(TrafficTimeframe.MONTH)
            advanceUntilIdle()
            assertTrue(vm.engineFilter.value.isEmpty())
        }
    }

    @Nested
    inner class EngineFilter {

        @Test
        fun `toggleEngineFilter adds engine to set`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.toggleEngineFilter("byedpi")
            advanceUntilIdle()
            assertEquals(setOf("byedpi"), vm.engineFilter.value)
        }

        @Test
        fun `toggleEngineFilter twice removes engine`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.toggleEngineFilter("byedpi")
            vm.toggleEngineFilter("byedpi")
            advanceUntilIdle()
            assertTrue(vm.engineFilter.value.isEmpty())
        }

        @Test
        fun `clearEngineFilter empties the set`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.toggleEngineFilter("byedpi")
            vm.toggleEngineFilter("warp")
            advanceUntilIdle()
            vm.clearEngineFilter()
            advanceUntilIdle()
            assertTrue(vm.engineFilter.value.isEmpty())
        }

        @Test
        fun `engine filter restricts sessions to matching engine`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi"),
                sample(2L, engineId = "warp"),
                sample(3L, engineId = "byedpi"),
            )
            vm.toggleEngineFilter("warp")
            advanceUntilIdle()
            assertEquals(listOf(2L), vm.sessions.value.map { it.id })
        }

        @Test
        fun `no filter returns all engines`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi"),
                sample(2L, engineId = "warp"),
            )
            advanceUntilIdle()
            assertEquals(2, vm.sessions.value.size)
        }
    }

    @Nested
    inner class Summary {

        @Test
        fun `summary sums rx and tx across all sessions`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, rxBytes = 100L, txBytes = 50L, durationMs = 1000L),
                sample(2L, rxBytes = 200L, txBytes = 80L, durationMs = 2000L),
            )
            advanceUntilIdle()
            assertEquals(300L, vm.summary.value.totalRx)
            assertEquals(130L, vm.summary.value.totalTx)
            assertEquals(2, vm.summary.value.sessionCount)
            assertEquals(3000L, vm.summary.value.totalDurationMs)
        }

        @Test
        fun `summary reflects active engine filter`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, txBytes = 50L),
                sample(2L, engineId = "warp", rxBytes = 200L, txBytes = 80L),
            )
            vm.toggleEngineFilter("byedpi")
            advanceUntilIdle()
            assertEquals(100L, vm.summary.value.totalRx)
            assertEquals(50L, vm.summary.value.totalTx)
            assertEquals(1, vm.summary.value.sessionCount)
        }

        @Test
        fun `empty sessions produce zero summary`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(TrafficSummary(0L, 0L, 0, 0L), vm.summary.value)
        }
    }

    @Nested
    inner class EngineSummaries {

        @Test
        fun `groups sessions by engineId with correct totals`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, txBytes = 20L),
                sample(2L, engineId = "warp", rxBytes = 300L, txBytes = 50L),
                sample(3L, engineId = "byedpi", rxBytes = 50L, txBytes = 10L),
            )
            advanceUntilIdle()
            val summaries = vm.engineSummaries.value
            assertEquals(2, summaries.size)
            val byedpi = summaries.first { it.engineId == "byedpi" }
            assertEquals(150L, byedpi.rx)
            assertEquals(30L, byedpi.tx)
            assertEquals(2, byedpi.sessionCount)
            val warp = summaries.first { it.engineId == "warp" }
            assertEquals(300L, warp.rx)
        }

        @Test
        fun `engine summaries sorted by total traffic descending`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, txBytes = 0L),
                sample(2L, engineId = "warp", rxBytes = 500L, txBytes = 0L),
            )
            advanceUntilIdle()
            assertEquals("warp", vm.engineSummaries.value.first().engineId)
        }

        @Test
        fun `single engine produces one summary entry`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi"),
                sample(2L, engineId = "byedpi"),
            )
            advanceUntilIdle()
            assertEquals(1, vm.engineSummaries.value.size)
        }
    }

    @Nested
    inner class ChartData {

        @Test
        fun `empty sessions produce empty chart`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(TrafficChartData.Empty, vm.chartData.value)
        }

        @Test
        fun `chart lines include ENGINE_ID_ALL key`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            val now = System.currentTimeMillis()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, txBytes = 50L, startedAt = now - 1_000L),
            )
            advanceUntilIdle()
            assertTrue(vm.chartData.value.lines.containsKey(ENGINE_ID_ALL))
        }

        @Test
        fun `chart lines include one entry per unique engine`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            val now = System.currentTimeMillis()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, startedAt = now - 1_000L),
                sample(2L, engineId = "warp", rxBytes = 200L, startedAt = now - 2_000L),
            )
            advanceUntilIdle()
            val lines = vm.chartData.value.lines
            assertTrue(lines.containsKey("byedpi"))
            assertTrue(lines.containsKey("warp"))
        }

        @Test
        fun `all line equals sum of per-engine lines at each bucket`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            val bucketMs = TrafficTimeframe.WEEK.bucketMs
            val now = (System.currentTimeMillis() / bucketMs) * bucketMs
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, txBytes = 10L, startedAt = now - 1_000L),
                sample(2L, engineId = "warp", rxBytes = 200L, txBytes = 20L, startedAt = now - 2_000L),
            )
            advanceUntilIdle()
            val data = vm.chartData.value
            if (data.buckets.isEmpty()) return@runTest
            val allLine = data.lines[ENGINE_ID_ALL] ?: return@runTest
            val byedpiLine = data.lines["byedpi"] ?: LongArray(allLine.size).asList()
            val warpLine = data.lines["warp"] ?: LongArray(allLine.size).asList()
            allLine.forEachIndexed { i, total ->
                assertEquals(byedpiLine[i] + warpLine[i], total)
            }
        }

        @Test
        fun `single session produces non-empty bucket list`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(sample(1L, rxBytes = 500L, startedAt = System.currentTimeMillis() - 1_000L))
            advanceUntilIdle()
            assertTrue(vm.chartData.value.buckets.isNotEmpty())
        }

        @Test
        fun `chart respects engine filter`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            val now = System.currentTimeMillis()
            dao.flow.value = listOf(
                sample(1L, engineId = "byedpi", rxBytes = 100L, startedAt = now - 1_000L),
                sample(2L, engineId = "warp", rxBytes = 200L, startedAt = now - 2_000L),
            )
            vm.toggleEngineFilter("byedpi")
            advanceUntilIdle()
            val lines = vm.chartData.value.lines
            assertFalse(lines.containsKey("warp"), "filtered-out engine must not appear in chart")
        }
    }

    @Nested
    inner class SessionSort {

        @Test
        fun `default sort is TIME_DESC`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(SessionSort.TIME_DESC, vm.sessionSort.value)
        }

        @Test
        fun `setSessionSort TIME_ASC orders oldest first`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, startedAt = 100L),
                sample(2L, startedAt = 300L),
            )
            vm.setSessionSort(SessionSort.TIME_ASC)
            advanceUntilIdle()
            assertEquals(listOf(1L, 2L), vm.sessions.value.map { it.id })
        }

        @Test
        fun `setSessionSort TRAFFIC_DESC orders by rx plus tx descending`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, rxBytes = 100L, txBytes = 100L),
                sample(2L, rxBytes = 500L, txBytes = 0L),
                sample(3L, rxBytes = 50L, txBytes = 50L),
            )
            vm.setSessionSort(SessionSort.TRAFFIC_DESC)
            advanceUntilIdle()
            assertEquals(listOf(2L, 1L, 3L), vm.sessions.value.map { it.id })
        }

        @Test
        fun `setSessionSort DURATION_DESC orders by duration descending`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, durationMs = 1_000L),
                sample(2L, durationMs = 5_000L),
                sample(3L, durationMs = 2_000L),
            )
            vm.setSessionSort(SessionSort.DURATION_DESC)
            advanceUntilIdle()
            assertEquals(listOf(2L, 3L, 1L), vm.sessions.value.map { it.id })
        }
    }

    @Nested
    inner class DrillDown {

        @Test
        fun `setSessionsExpanded true makes sessionsExpanded true`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.setSessionsExpanded(true)
            advanceUntilIdle()
            assertTrue(vm.sessionsExpanded.value)
        }

        @Test
        fun `setSessionsExpanded false collapses`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.setSessionsExpanded(true)
            vm.setSessionsExpanded(false)
            advanceUntilIdle()
            assertFalse(vm.sessionsExpanded.value)
        }
    }

    @Nested
    inner class AvailableEngines {

        @Test
        fun `availableEngines collects unique sorted engine ids`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            dao.flow.value = listOf(
                sample(1L, engineId = "warp"),
                sample(2L, engineId = "byedpi"),
                sample(3L, engineId = "warp"),
            )
            advanceUntilIdle()
            assertEquals(listOf("byedpi", "warp"), vm.availableEngines.value)
        }

        @Test
        fun `empty dao produces empty availableEngines`() = runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(emptyList(), vm.availableEngines.value)
        }
    }

    private fun sample(
        id: Long,
        engineId: String = "byedpi",
        startedAt: Long = id * 1_000L,
        durationMs: Long = 5_000L,
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
        var lastObserveFromSince: Long = -1L
        var observeAllCalled: Boolean = false

        override fun observeRecent(limit: Int): Flow<List<SessionStatsEntity>> = flow

        override fun observeFrom(since: Long): Flow<List<SessionStatsEntity>> {
            lastObserveFromSince = since
            return flow
        }

        override fun observeAll(): Flow<List<SessionStatsEntity>> {
            observeAllCalled = true
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
