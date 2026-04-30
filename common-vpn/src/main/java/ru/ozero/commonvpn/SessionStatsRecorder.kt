package ru.ozero.commonvpn

interface SessionStatsRecorder {
    suspend fun startSession(engineId: String, startedAt: Long): Long
    suspend fun endSession(
        id: Long,
        endedAt: Long,
        rxBytes: Long,
        txBytes: Long,
        durationMs: Long,
        status: Status,
    )

    enum class Status { DISCONNECTED, FAILED }

    object NoOp : SessionStatsRecorder {
        override suspend fun startSession(engineId: String, startedAt: Long): Long = -1L
        override suspend fun endSession(
            id: Long,
            endedAt: Long,
            rxBytes: Long,
            txBytes: Long,
            durationMs: Long,
            status: Status,
        ) = Unit
    }
}
