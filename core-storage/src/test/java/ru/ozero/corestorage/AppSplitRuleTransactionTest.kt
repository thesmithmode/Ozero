package ru.ozero.corestorage

import android.database.sqlite.SQLiteException
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ozero.corestorage.entity.AppSplitRule
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppSplitRuleTransactionTest {

    @Test
    fun `failed insert rolls back replaced rules`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), OzeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.appSplitRuleDao()
            val original = AppSplitRule("com.original", isExcluded = true)
            dao.upsert(original)
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER reject_split_rule BEFORE INSERT ON app_split_rules
                WHEN NEW.packageName = 'com.rejected'
                BEGIN SELECT RAISE(ABORT, 'rejected'); END
                """.trimIndent(),
            )

            assertFailsWith<SQLiteException> {
                dao.replaceAll(listOf(AppSplitRule("com.new"), AppSplitRule("com.rejected")))
            }

            assertEquals(listOf(original), dao.observeAll().first())
        } finally {
            database.close()
        }
    }
}
