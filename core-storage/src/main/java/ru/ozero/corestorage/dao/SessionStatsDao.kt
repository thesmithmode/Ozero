package ru.ozero.corestorage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.ozero.corestorage.entity.SessionStatsEntity

@Dao
interface SessionStatsDao {

    @Insert
    suspend fun insertStart(entity: SessionStatsEntity): Long

    @Query(
        """
        UPDATE session_stats
        SET endedAt = :endedAt, rxBytes = :rxBytes, txBytes = :txBytes,
            durationMs = :durationMs, finalStatus = :finalStatus
        WHERE id = :id
        """,
    )
    suspend fun updateEnd(
        id: Long,
        endedAt: Long,
        rxBytes: Long,
        txBytes: Long,
        durationMs: Long,
        finalStatus: String,
    )

    @Query("SELECT * FROM session_stats ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<SessionStatsEntity>>

    @Query("DELETE FROM session_stats WHERE startedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
}
