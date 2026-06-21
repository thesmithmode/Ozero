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

    @Update
    suspend fun update(group: SubscriptionGroup)

    @Delete
    suspend fun delete(group: SubscriptionGroup)

    @Transaction
    suspend fun deleteBuiltinGroupWithProfiles(group: SubscriptionGroup) {
        if (group.isBuiltin) delete(group)
    }

    @Query("SELECT COUNT(*) FROM subscription_groups")
    suspend fun count(): Int
}
