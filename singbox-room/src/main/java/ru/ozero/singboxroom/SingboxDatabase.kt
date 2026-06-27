package ru.ozero.singboxroom

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup

@Database(
    entities = [SubscriptionGroup::class, ProxyProfile::class, ProxyChainStep::class],
    version = 3,
    exportSchema = true,
)
abstract class SingboxDatabase : RoomDatabase() {
    abstract fun subscriptionGroupDao(): SubscriptionGroupDao
    abstract fun proxyProfileDao(): ProxyProfileDao
    abstract fun proxyChainDao(): ProxyChainDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `proxy_chain_steps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` INTEGER NOT NULL,
                        `userOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`profileId`) REFERENCES `proxy_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_proxy_chain_steps_profileId` " +
                        "ON `proxy_chain_steps` (`profileId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_proxy_chain_steps_userOrder` " +
                        "ON `proxy_chain_steps` (`userOrder`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `proxy_profiles` ADD COLUMN `probeError` TEXT")
                db.execSQL("ALTER TABLE `proxy_profiles` ADD COLUMN `lastProbeAt` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
