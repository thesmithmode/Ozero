package ru.ozero.app.ui.stats

import org.junit.jupiter.api.Test
import ru.ozero.corestorage.entity.SessionStatsEntity
import kotlin.test.assertEquals

class SessionHistoryFilterTest {

    @Test
    fun `пустой filter и TIME_DESC возвращают все элементы по убыванию startedAt`() {
        val s1 = sample(1L, startedAt = 100L)
        val s2 = sample(2L, startedAt = 300L)
        val s3 = sample(3L, startedAt = 200L)
        val out = applySessionFilterAndSort(listOf(s1, s2, s3), SessionFilter(), SessionSort.TIME_DESC)
        assertEquals(listOf(2L, 3L, 1L), out.map { it.id })
    }

    @Test
    fun `TIME_ASC сортирует по возрастанию`() {
        val out = applySessionFilterAndSort(
            listOf(sample(1L, startedAt = 200L), sample(2L, startedAt = 100L)),
            SessionFilter(),
            SessionSort.TIME_ASC,
        )
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun `TRAFFIC_DESC сортирует по сумме rx плюс tx`() {
        val out = applySessionFilterAndSort(
            listOf(sample(1L, rxBytes = 10L, txBytes = 10L), sample(2L, rxBytes = 100L, txBytes = 0L)),
            SessionFilter(),
            SessionSort.TRAFFIC_DESC,
        )
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun `DURATION_DESC сортирует по durationMs`() {
        val out = applySessionFilterAndSort(
            listOf(sample(1L, durationMs = 1000L), sample(2L, durationMs = 5000L)),
            SessionFilter(),
            SessionSort.DURATION_DESC,
        )
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun `engines filter оставляет только указанные`() {
        val s1 = sample(1L, engineId = "BYEDPI")
        val s2 = sample(2L, engineId = "WARP")
        val out = applySessionFilterAndSort(
            listOf(s1, s2),
            SessionFilter(engines = setOf("WARP")),
            SessionSort.TIME_DESC,
        )
        assertEquals(listOf(2L), out.map { it.id })
    }

    @Test
    fun `пустой engines set означает all engines`() {
        val s1 = sample(1L, engineId = "BYEDPI")
        val s2 = sample(2L, engineId = "WARP")
        val out = applySessionFilterAndSort(
            listOf(s1, s2),
            SessionFilter(engines = emptySet()),
            SessionSort.TIME_DESC,
        )
        assertEquals(2, out.size)
    }

    @Test
    fun `periodMs cutoff отсекает сессии старше окна`() {
        val now = 1_000_000L
        val out = applySessionFilterAndSort(
            sessions = listOf(
                sample(1L, startedAt = now - 1000L),
                sample(2L, startedAt = now - 100_000L),
            ),
            filter = SessionFilter(periodMs = 50_000L),
            sort = SessionSort.TIME_DESC,
            nowMs = now,
        )
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun `null periodMs не применяет cutoff`() {
        val out = applySessionFilterAndSort(
            sessions = listOf(sample(1L, startedAt = 0L), sample(2L, startedAt = 1L)),
            filter = SessionFilter(periodMs = null),
            sort = SessionSort.TIME_DESC,
            nowMs = 1_000_000_000L,
        )
        assertEquals(2, out.size)
    }

    @Test
    fun `engines и periodMs одновременно работают`() {
        val now = 1_000_000L
        val out = applySessionFilterAndSort(
            sessions = listOf(
                sample(1L, engineId = "BYEDPI", startedAt = now - 1000L),
                sample(2L, engineId = "WARP", startedAt = now - 1000L),
                sample(3L, engineId = "WARP", startedAt = now - 100_000L),
            ),
            filter = SessionFilter(engines = setOf("WARP"), periodMs = 50_000L),
            sort = SessionSort.TIME_DESC,
            nowMs = now,
        )
        assertEquals(listOf(2L), out.map { it.id })
    }

    @Test
    fun `пустой input возвращает пустой output для любого filter`() {
        val out = applySessionFilterAndSort(
            sessions = emptyList(),
            filter = SessionFilter(engines = setOf("WARP")),
            sort = SessionSort.TIME_DESC,
        )
        assertEquals(emptyList(), out)
    }

    private fun sample(
        id: Long,
        engineId: String = "BYEDPI",
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
}
