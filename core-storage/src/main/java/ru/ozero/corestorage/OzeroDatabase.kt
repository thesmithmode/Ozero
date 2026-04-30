package ru.ozero.corestorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.corestorage.entity.SessionStatsEntity

@Database(
    entities = [ServerEntity::class, AppSplitRule::class, SessionStatsEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class OzeroDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun appSplitRuleDao(): AppSplitRuleDao
    abstract fun sessionStatsDao(): SessionStatsDao

    companion object {
        const val DATABASE_NAME = "ozero.db"

        private val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE servers ADD COLUMN pairId TEXT DEFAULT NULL")
                }
            }

        private val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_isAlive ON servers(isAlive)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_priority ON servers(priority)")
                }
            }

        private val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS connection_logs")
                }
            }

        private val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS session_stats (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            engineId TEXT NOT NULL,
                            startedAt INTEGER NOT NULL,
                            endedAt INTEGER,
                            rxBytes INTEGER NOT NULL DEFAULT 0,
                            txBytes INTEGER NOT NULL DEFAULT 0,
                            durationMs INTEGER NOT NULL DEFAULT 0,
                            finalStatus TEXT NOT NULL DEFAULT 'running'
                        )
                        """.trimIndent(),
                    )
                }
            }

        fun create(context: Context): OzeroDatabase =
            Room.databaseBuilder(context, OzeroDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
