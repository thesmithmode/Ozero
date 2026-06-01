package ru.ozero.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.SessionStatsRecorder
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomSessionStatsRecorderTest {

    @Test
    fun `startSession inserts running entity and returns dao id`() = runTest {
        val dao = FakeSessionStatsDao(insertResult = 42L)
        val recorder = RoomSessionStatsRecorder(dao)

        val id = recorder.startSession(engineId = "warp", startedAt = 100L)

        assertEquals(42L, id)
        assertEquals(
            SessionStatsEntity(engineId = "warp", startedAt = 100L, finalStatus = SessionStatsEntity.STATUS_RUNNING),
            dao.inserted,
        )
    }

    @Test
    fun `startSession returns minus one when dao throws or returns invalid id`() = runTest {
        val throwingDao = FakeSessionStatsDao(insertFailure = IllegalStateException("db"))
        val invalidDao = FakeSessionStatsDao(insertResult = -1L)

        assertEquals(-1L, RoomSessionStatsRecorder(throwingDao).startSession("warp", 1L))
        assertEquals(-1L, RoomSessionStatsRecorder(invalidDao).startSession("warp", 1L))
    }

    @Test
    fun `endSession ignores negative ids`() = runTest {
        val dao = FakeSessionStatsDao()

        RoomSessionStatsRecorder(dao).endSession(
            id = -1L,
            endedAt = 200L,
            rxBytes = 1L,
            txBytes = 2L,
            durationMs = 3L,
            status = SessionStatsRecorder.Status.DISCONNECTED,
        )

        assertNull(dao.updated)
    }

    @Test
    fun `endSession maps disconnected and failed statuses`() = runTest {
        val dao = FakeSessionStatsDao()
        val recorder = RoomSessionStatsRecorder(dao)

        recorder.endSession(7L, 200L, 10L, 20L, 30L, SessionStatsRecorder.Status.DISCONNECTED)
        assertEquals(SessionStatsEntity.STATUS_DISCONNECTED, dao.updated?.finalStatus)

        recorder.endSession(8L, 300L, 11L, 21L, 31L, SessionStatsRecorder.Status.FAILED)
        assertEquals(
            UpdateCall(
                id = 8L,
                endedAt = 300L,
                rxBytes = 11L,
                txBytes = 21L,
                durationMs = 31L,
                finalStatus = SessionStatsEntity.STATUS_FAILED,
            ),
            dao.updated,
        )
    }

    @Test
    fun `endSession swallows dao update failure`() = runTest {
        val dao = FakeSessionStatsDao(updateFailure = IllegalStateException("db"))

        RoomSessionStatsRecorder(dao).endSession(
            id = 1L,
            endedAt = 2L,
            rxBytes = 3L,
            txBytes = 4L,
            durationMs = 5L,
            status = SessionStatsRecorder.Status.FAILED,
        )

        assertEquals(SessionStatsEntity.STATUS_FAILED, dao.updated?.finalStatus)
    }

    private data class UpdateCall(
        val id: Long,
        val endedAt: Long,
        val rxBytes: Long,
        val txBytes: Long,
        val durationMs: Long,
        val finalStatus: String,
    )

    private class FakeSessionStatsDao(
        private val insertResult: Long = 1L,
        private val insertFailure: Throwable? = null,
        private val updateFailure: Throwable? = null,
    ) : SessionStatsDao {
        var inserted: SessionStatsEntity? = null
        var updated: UpdateCall? = null

        override suspend fun insertStart(entity: SessionStatsEntity): Long {
            inserted = entity
            insertFailure?.let { throw it }
            return insertResult
        }

        override suspend fun updateEnd(
            id: Long,
            endedAt: Long,
            rxBytes: Long,
            txBytes: Long,
            durationMs: Long,
            finalStatus: String,
        ) {
            updated = UpdateCall(id, endedAt, rxBytes, txBytes, durationMs, finalStatus)
            updateFailure?.let { throw it }
        }

        override fun observeRecent(limit: Int): Flow<List<SessionStatsEntity>> = emptyFlow()

        override fun observeFrom(since: Long): Flow<List<SessionStatsEntity>> = emptyFlow()

        override fun observeAll(): Flow<List<SessionStatsEntity>> = emptyFlow()

        override suspend fun deleteOlderThan(olderThan: Long): Int = 0
    }
}
