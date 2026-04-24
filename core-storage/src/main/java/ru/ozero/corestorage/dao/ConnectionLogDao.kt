package ru.ozero.corestorage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.ozero.corestorage.entity.ConnectionLogEntity

@Dao
interface ConnectionLogDao {
    @Insert
    suspend fun insert(log: ConnectionLogEntity): Long

    @Update
    suspend fun update(log: ConnectionLogEntity)

    @Query("SELECT * FROM connection_logs ORDER BY connectedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ConnectionLogEntity>

    @Query("DELETE FROM connection_logs WHERE connectedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
