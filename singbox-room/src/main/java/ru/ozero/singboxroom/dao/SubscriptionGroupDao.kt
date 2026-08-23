package ru.ozero.singboxroom.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.ozero.singboxroom.entity.SubscriptionGroup

@Dao
interface SubscriptionGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: SubscriptionGroup): Long

    @Query("SELECT * FROM subscription_groups WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionGroup?

    @Query("SELECT * FROM subscription_groups ORDER BY userOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<SubscriptionGroup>>

    @Query("SELECT * FROM subscription_groups ORDER BY userOrder ASC, id ASC")
    suspend fun getAll(): List<SubscriptionGroup>

    @Query("SELECT * FROM subscription_groups WHERE subscriptionUrl = :url LIMIT 1")
    suspend fun getByUrl(url: String): SubscriptionGroup?

    @Query("SELECT * FROM subscription_groups WHERE isBuiltin = 1 ORDER BY userOrder ASC, id ASC")
    suspend fun getBuiltins(): List<SubscriptionGroup>

    @Query("SELECT id FROM proxy_profiles WHERE groupId = :groupId")
    suspend fun getProfileIdsByGroupId(groupId: Long): List<Long>

    @Update
    suspend fun update(group: SubscriptionGroup)

    @Query(
        """
        UPDATE subscription_groups
        SET lastAttemptAt = :attemptAt, refreshGeneration = refreshGeneration + 1
        WHERE id = :id AND refreshGeneration = :expectedGeneration
        """,
    )
    suspend fun tryBeginRefresh(id: Long, expectedGeneration: Long, attemptAt: Long): Int

    @Query(
        """
        UPDATE subscription_groups
        SET lastUpdated = :lastUpdated,
            lastAttemptAt = :lastAttemptAt,
            lastRefreshErrorCode = :lastRefreshErrorCode,
            lastServerCount = :lastServerCount,
            bytesUsed = :bytesUsed,
            bytesRemaining = :bytesRemaining,
            expiryDate = :expiryDate
        WHERE id = :id AND refreshGeneration = :refreshGeneration
        """,
    )
    suspend fun commitRefresh(
        id: Long,
        refreshGeneration: Long,
        lastUpdated: Long,
        lastAttemptAt: Long,
        lastRefreshErrorCode: String?,
        lastServerCount: Int,
        bytesUsed: Long,
        bytesRemaining: Long,
        expiryDate: Long,
    ): Int

    @Delete
    suspend fun delete(group: SubscriptionGroup)

    @Transaction
    suspend fun deleteBuiltinGroupWithProfiles(group: SubscriptionGroup) {
        if (group.isBuiltin) delete(group)
    }

    @Query("SELECT COUNT(*) FROM subscription_groups")
    suspend fun count(): Int
}
