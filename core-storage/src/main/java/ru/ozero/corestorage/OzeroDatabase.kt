package ru.ozero.corestorage

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.dao.ConnectionLogDao
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.corestorage.entity.ConnectionLogEntity
import ru.ozero.corestorage.entity.ServerEntity

@Database(
    entities = [ServerEntity::class, ConnectionLogEntity::class, AppSplitRule::class],
    version = 1,
    exportSchema = true
)
abstract class OzeroDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun appSplitRuleDao(): AppSplitRuleDao
}
