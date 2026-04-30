package ru.ozero.corestorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.corestorage.entity.ServerEntity

@Database(
    entities = [ServerEntity::class, AppSplitRule::class],
    version = 4,
    exportSchema = true,
)
abstract class OzeroDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun appSplitRuleDao(): AppSplitRuleDao

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

        fun create(context: Context): OzeroDatabase =
            Room.databaseBuilder(context, OzeroDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
