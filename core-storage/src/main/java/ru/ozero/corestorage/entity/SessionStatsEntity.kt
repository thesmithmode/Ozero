package ru.ozero.corestorage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_stats")
data class SessionStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val engineId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val durationMs: Long = 0L,
    val finalStatus: String = STATUS_RUNNING,
) {
    companion object {
        const val STATUS_RUNNING: String = "running"
        const val STATUS_DISCONNECTED: String = "disconnected"
        const val STATUS_FAILED: String = "failed"
    }
}
