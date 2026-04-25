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
    // v2: добавлено `pairId` в ServerEntity для double-hop chains (E8).
    // До stable релиза fallbackToDestructiveMigration() в RoomBuilder — миграция не нужна.
    version = 2,
    exportSchema = true
)
abstract class OzeroDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun appSplitRuleDao(): AppSplitRuleDao
}
