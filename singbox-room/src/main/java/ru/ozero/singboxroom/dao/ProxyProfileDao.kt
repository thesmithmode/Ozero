package ru.ozero.singboxroom.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.ozero.singboxroom.entity.ProxyProfile

@Dao
interface ProxyProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProxyProfile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProxyProfile>)

    @Query("SELECT * FROM proxy_profiles WHERE id = :id")
    suspend fun getById(id: Long): ProxyProfile?

    @Query("SELECT * FROM proxy_profiles ORDER BY groupId ASC, userOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles WHERE groupId = :groupId ORDER BY userOrder ASC, id ASC")
    fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles WHERE groupId = :groupId ORDER BY userOrder ASC, id ASC")
    suspend fun getByGroupId(groupId: Long): List<ProxyProfile>

    @Query("DELETE FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: Long)

    @Query("SELECT id FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun getIdsByGroupId(groupId: Long): List<Long>

    @Query("DELETE FROM proxy_profiles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Transaction
    suspend fun replaceForGroup(groupId: Long, profiles: List<ProxyProfile>) {
        val stableIds = profiles.mapNotNull { profile -> profile.id.takeIf { it != 0L } }
        if (stableIds.isEmpty()) {
            deleteByGroupId(groupId)
        } else {
            val stableIdSet = stableIds.toHashSet()
            getIdsByGroupId(groupId)
                .asSequence()
                .filterNot { id -> id in stableIdSet }
                .chunked(MAX_SQL_BIND_IDS)
                .forEach { ids -> deleteByIds(ids) }
        }
        insertAll(profiles)
    }

    companion object {
        const val MAX_SQL_BIND_IDS = 500
    }

    @Query("UPDATE proxy_profiles SET latencyMs = :latency WHERE id = :id")
    suspend fun updateLatency(id: Long, latency: Int)

    @Query("SELECT COUNT(*) FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun countByGroupId(groupId: Long): Int

    @Update
    suspend fun update(profile: ProxyProfile)

    @Delete
    suspend fun delete(profile: ProxyProfile)
}
