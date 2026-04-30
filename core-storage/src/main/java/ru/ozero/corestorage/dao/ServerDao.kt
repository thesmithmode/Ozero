package ru.ozero.corestorage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.ozero.corestorage.entity.ServerEntity

@Dao
interface ServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(servers: List<ServerEntity>)

    @Query("SELECT * FROM servers ORDER BY priority ASC, lastCheckedAt DESC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE isAlive = 1 ORDER BY priority ASC, lastCheckedAt DESC")
    suspend fun getLiveServers(): List<ServerEntity>

    @Query("SELECT * FROM servers ORDER BY priority ASC, lastCheckedAt DESC")
    suspend fun getAllServers(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ServerEntity?

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM servers")
    suspend fun deleteAll()

    @Query("UPDATE servers SET isAlive = :alive, lastCheckedAt = :ts WHERE id = :id")
    suspend fun setAlive(id: String, alive: Boolean, ts: Long)
}
