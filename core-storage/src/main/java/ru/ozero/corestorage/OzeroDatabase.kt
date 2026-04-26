package ru.ozero.corestorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.dao.ConnectionLogDao
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.corestorage.entity.ConnectionLogEntity
import ru.ozero.corestorage.entity.ServerEntity

@Database(
    entities = [ServerEntity::class, ConnectionLogEntity::class, AppSplitRule::class],
    // v3: индексы isAlive/priority в servers (hot-path getLiveServers/observeAll).
    version = 3,
    exportSchema = true,
)
abstract class OzeroDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun appSplitRuleDao(): AppSplitRuleDao

    companion object {
        const val DATABASE_NAME = "ozero.db"

        /**
         * v1 → v2: добавлена nullable колонка `pairId` в `servers` для double-hop chains (E8).
         * Безопасная неразрушающая миграция — `ALTER TABLE ADD COLUMN` с DEFAULT NULL.
         * Передаётся в Builder.addMigrations(MIGRATION_1_2) вместо fallbackToDestructiveMigration(),
         * иначе пользователи теряют сохранённые серверы.
         */
        private val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE servers ADD COLUMN pairId TEXT DEFAULT NULL")
                }
            }

        /** v2 → v3: индексы для hot-path filter/sort. Неразрушающие CREATE INDEX. */
        private val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_isAlive ON servers(isAlive)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_priority ON servers(priority)")
                }
            }

        fun create(context: Context): OzeroDatabase =
            Room.databaseBuilder(context, OzeroDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
