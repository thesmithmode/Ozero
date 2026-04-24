package ru.ozero.corestorage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.ozero.corestorage.entity.AppSplitRule

@Dao
interface AppSplitRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppSplitRule)

    @Query("SELECT * FROM app_split_rules")
    fun observeAll(): Flow<List<AppSplitRule>>

    @Query("DELETE FROM app_split_rules WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
