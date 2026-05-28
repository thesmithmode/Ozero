package ru.ozero.singboxroom.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.ozero.singboxroom.entity.ProxyChainStep

@Dao
interface ProxyChainDao {
    @Query("SELECT * FROM proxy_chain_steps ORDER BY userOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<ProxyChainStep>>

    @Query("SELECT * FROM proxy_chain_steps ORDER BY userOrder ASC, id ASC")
    suspend fun getAll(): List<ProxyChainStep>

    @Query("DELETE FROM proxy_chain_steps")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<ProxyChainStep>)

    @Transaction
    suspend fun replace(profileIds: List<Long>) {
        clear()
        insertAll(profileIds.mapIndexed { index, id -> ProxyChainStep(profileId = id, userOrder = index) })
    }
}
