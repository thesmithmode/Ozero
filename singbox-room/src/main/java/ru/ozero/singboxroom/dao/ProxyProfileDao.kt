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
@Suppress("TooManyFunctions")
interface ProxyProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProxyProfile): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(profiles: List<ProxyProfile>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long>

    @Query("SELECT * FROM proxy_profiles WHERE id = :id")
    suspend fun getById(id: Long): ProxyProfile?

    @Query("SELECT * FROM proxy_profiles WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<ProxyProfile?>

    @Query("SELECT * FROM proxy_profiles ORDER BY groupId ASC, userOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles ORDER BY groupId ASC, userOrder ASC, id ASC LIMIT :limit")
    fun getAllLimitedFlow(limit: Int): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles ORDER BY groupId ASC, userOrder ASC, id ASC LIMIT :limit")
    fun getAutoCandidatesFlow(limit: Int): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles WHERE groupId = :groupId ORDER BY userOrder ASC, id ASC")
    fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM proxy_profiles WHERE groupId = :groupId ORDER BY userOrder ASC, id ASC")
    suspend fun getByGroupId(groupId: Long): List<ProxyProfile>

    @Query("SELECT * FROM proxy_profiles WHERE groupId = :groupId ORDER BY userOrder ASC, id ASC LIMIT :limit")
    suspend fun getByGroupIdLimited(groupId: Long, limit: Int): List<ProxyProfile>

    @Query(
        """
        SELECT * FROM proxy_profiles
        WHERE groupId = :groupId
        ORDER BY
            CASE WHEN latencyMs >= 0 THEN 0 WHEN latencyMs = -1 THEN 1 ELSE 2 END ASC,
            CASE WHEN latencyMs >= 0 THEN latencyMs ELSE userOrder END ASC,
            userOrder ASC,
            id ASC
        LIMIT :limit
        """,
    )
    suspend fun getAutoCandidatesByGroupId(groupId: Long, limit: Int): List<ProxyProfile>

    @Query("DELETE FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: Long)

    @Query("SELECT id FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun getIdsByGroupId(groupId: Long): List<Long>

    @Query("DELETE FROM proxy_profiles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Transaction
    suspend fun replaceForGroup(groupId: Long, profiles: List<ProxyProfile>) {
        val stableIds = profiles.mapNotNull { profile -> profile.id.takeIf { it != 0L } }
        val existingIds = getIdsByGroupId(groupId).toHashSet()
        if (stableIds.isEmpty()) {
            deleteByGroupId(groupId)
        } else {
            val stableIdSet = stableIds.toHashSet()
            existingIds
                .asSequence()
                .filterNot { id -> id in stableIdSet }
                .chunked(MAX_SQL_BIND_IDS)
                .forEach { ids -> deleteByIds(ids) }
        }
        val (existingProfiles, newProfiles) = profiles.partition { profile ->
            profile.id != 0L && profile.id in existingIds
        }
        existingProfiles.forEach { profile -> update(profile) }
        val inserted = insertAllIgnoringConflicts(newProfiles)
        newProfiles.zip(inserted).forEach { (profile, rowId) ->
            if (rowId == -1L && profile.id != 0L) update(profile)
        }
    }

    @Transaction
    suspend fun replaceForGroupAndReturnRemovedIds(
        groupId: Long,
        profiles: List<ProxyProfile>,
    ): Set<Long> {
        val existingIds = getIdsByGroupId(groupId).toSet()
        val retainedIds = profiles.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
        replaceForGroup(groupId, profiles)
        return existingIds - retainedIds
    }

    companion object {
        const val MAX_SQL_BIND_IDS = 500
    }

    @Query(
        """
        UPDATE proxy_profiles
        SET latencyMs = :latency, probeError = :probeError, lastProbeAt = :lastProbeAt
        WHERE id = :id
        """,
    )
    suspend fun updateProbeResult(id: Long, latency: Int, probeError: String?, lastProbeAt: Long)

    @Query(
        """
        UPDATE proxy_profiles
        SET latencyMs = :latency, probeError = :probeError, lastProbeAt = :lastProbeAt
        WHERE id = :id AND protocolType = :protocolType AND beanBlob = :beanBlob
        """,
    )
    suspend fun updateProbeResultIfCurrent(
        id: Long,
        protocolType: Int,
        beanBlob: ByteArray,
        latency: Int,
        probeError: String?,
        lastProbeAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun countByGroupId(groupId: Long): Int

    @Update
    suspend fun update(profile: ProxyProfile)

    @Delete
    suspend fun delete(profile: ProxyProfile)
}
