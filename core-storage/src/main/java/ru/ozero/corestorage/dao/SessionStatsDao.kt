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

    @Query(
        """
        SELECT * FROM session_stats
        WHERE endedAt IS NOT NULL
        ORDER BY startedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int = 30): Flow<List<SessionStatsEntity>>

    @Query(
        """
        SELECT * FROM session_stats
        WHERE endedAt IS NOT NULL AND startedAt >= :since
        ORDER BY startedAt ASC
        """,
    )
    fun observeFrom(since: Long): Flow<List<SessionStatsEntity>>

    @Query(
        """
        SELECT * FROM session_stats
        WHERE endedAt IS NOT NULL
        ORDER BY startedAt ASC
        """,
    )
    fun observeAll(): Flow<List<SessionStatsEntity>>

    @Query("DELETE FROM session_stats WHERE startedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
}
