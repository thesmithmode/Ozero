package ru.ozero.app.data

import ru.ozero.commonvpn.SessionStatsRecorder
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import ru.ozero.enginescore.PersistentLoggers
import javax.inject.Inject

class RoomSessionStatsRecorder @Inject constructor(
    private val dao: SessionStatsDao,
) : SessionStatsRecorder {

    override suspend fun startSession(engineId: String, startedAt: Long): Long {
        val entity = SessionStatsEntity(
            engineId = engineId,
            startedAt = startedAt,
            finalStatus = SessionStatsEntity.STATUS_RUNNING,
        )
        val id = runCatching { dao.insertStart(entity) }
            .onFailure { PersistentLoggers.warn(TAG, "insertStart failed: ${it.message}") }
            .getOrDefault(-1L)
        if (id < 0) {
            PersistentLoggers.warn(TAG, "insertStart returned -1 для engineId=$engineId")
        }
        return id
    }

    override suspend fun endSession(
        id: Long,
        endedAt: Long,
        rxBytes: Long,
        txBytes: Long,
        durationMs: Long,
        status: SessionStatsRecorder.Status,
    ) {
        if (id < 0) return
        val statusName = when (status) {
            SessionStatsRecorder.Status.DISCONNECTED -> SessionStatsEntity.STATUS_DISCONNECTED
            SessionStatsRecorder.Status.FAILED -> SessionStatsEntity.STATUS_FAILED
        }
        runCatching {
            dao.updateEnd(
                id = id,
                endedAt = endedAt,
                rxBytes = rxBytes,
                txBytes = txBytes,
                durationMs = durationMs,
                finalStatus = statusName,
            )
        }.onFailure { PersistentLoggers.warn(TAG, "updateEnd failed для id=$id: ${it.message}") }
    }

    private companion object {
        const val TAG: String = "RoomSessionStats"
    }
}
