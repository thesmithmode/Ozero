package ru.ozero.corestorage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runtime-проверка [OzeroDatabase.MIGRATION_4_5] через реальный SQLite (Robolectric JVM SQLite).
 *
 * Закрывает C3 review concern: до этого теста миграция была покрыта только source-pattern regex,
 * что не ловило бы битый SQL синтаксис до первого install-over-upgrade у v0.0.1 юзеров.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MigrationFourToFiveRuntimeTest {

    private lateinit var openHelper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(V4_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS servers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            host TEXT NOT NULL,
                            port INTEGER NOT NULL,
                            isAlive INTEGER NOT NULL DEFAULT 0,
                            priority INTEGER NOT NULL DEFAULT 0,
                            pairId TEXT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS app_split_rules (
                            packageName TEXT NOT NULL PRIMARY KEY,
                            included INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // No-op: миграция применяется явно тестом.
                }
            })
            .build()
        openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        runCatching { openHelper.close() }
    }

    @Test
    fun migration_creates_session_stats_table() {
        OzeroDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='session_stats'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst(), "session_stats table должна существовать после migration")
                assertEquals("session_stats", cursor.getString(0))
            }
    }

    @Test
    fun migration_session_stats_has_all_required_columns() {
        OzeroDatabase.MIGRATION_4_5.migrate(db)

        val columns = db.query("PRAGMA table_info(session_stats)").use { cursor ->
            buildMap<String, String> {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    put(name, type)
                }
            }
        }

        assertEquals("INTEGER", columns["id"], "id колонка обязана быть INTEGER")
        assertEquals("TEXT", columns["engineId"])
        assertEquals("INTEGER", columns["startedAt"])
        assertEquals("INTEGER", columns["endedAt"])
        assertEquals("INTEGER", columns["rxBytes"])
        assertEquals("INTEGER", columns["txBytes"])
        assertEquals("INTEGER", columns["durationMs"])
        assertEquals("TEXT", columns["finalStatus"])
    }

    @Test
    fun migration_insert_select_roundtrip_with_defaults() {
        OzeroDatabase.MIGRATION_4_5.migrate(db)

        // Минимальный INSERT — только NOT NULL без defaults.
        db.execSQL("INSERT INTO session_stats (engineId, startedAt) VALUES ('BYEDPI', 1234567890)")

        db.query(
            """
            SELECT id, engineId, startedAt, endedAt, rxBytes, txBytes, durationMs, finalStatus
            FROM session_stats
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getLong(0) > 0L, "id обязан AUTOINCREMENT > 0")
            assertEquals("BYEDPI", cursor.getString(1))
            assertEquals(1_234_567_890L, cursor.getLong(2))
            assertTrue(cursor.isNull(3), "endedAt nullable → NULL без INSERT value")
            assertEquals(0L, cursor.getLong(4), "rxBytes default 0")
            assertEquals(0L, cursor.getLong(5), "txBytes default 0")
            assertEquals(0L, cursor.getLong(6), "durationMs default 0")
            assertEquals("running", cursor.getString(7), "finalStatus default 'running'")
        }
    }

    @Test
    fun migration_preserves_existing_v4_data() {
        // Симулируем v0.0.1 юзера с накопленными данными.
        db.execSQL(
            "INSERT INTO servers (host, port, isAlive, priority, pairId) " +
                "VALUES ('example.com', 443, 1, 10, 'pair-1')",
        )
        db.execSQL("INSERT INTO app_split_rules (packageName, included) VALUES ('com.example.app', 1)")

        OzeroDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT host, port, isAlive, priority, pairId FROM servers").use { c ->
            assertTrue(c.moveToFirst(), "v4 данные servers обязаны выжить после migration")
            assertEquals("example.com", c.getString(0))
            assertEquals(443, c.getInt(1))
            assertEquals(1, c.getInt(2))
            assertEquals(10, c.getInt(3))
            assertEquals("pair-1", c.getString(4))
        }
        db.query("SELECT packageName, included FROM app_split_rules").use { c ->
            assertTrue(c.moveToFirst(), "v4 данные app_split_rules обязаны выжить после migration")
            assertEquals("com.example.app", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
    }

    @Test
    fun migration_idempotent_when_session_stats_already_exists() {
        OzeroDatabase.MIGRATION_4_5.migrate(db)
        // Повторный запуск не должен падать благодаря IF NOT EXISTS.
        OzeroDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='session_stats'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0), "session_stats обязан существовать ровно одним")
            }
    }

    @Test
    fun migration_supports_finalStatus_values_used_by_recorder() {
        OzeroDatabase.MIGRATION_4_5.migrate(db)

        listOf("running", "DISCONNECTED", "FAILED").forEach { status ->
            db.execSQL(
                "INSERT INTO session_stats (engineId, startedAt, finalStatus) " +
                    "VALUES ('BYEDPI', 0, '$status')",
            )
        }

        db.query("SELECT COUNT(*) FROM session_stats WHERE finalStatus IN ('running','DISCONNECTED','FAILED')")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        val sample = db.query("SELECT finalStatus FROM session_stats WHERE engineId = 'BYEDPI' LIMIT 1")
        sample.use {
            assertTrue(it.moveToFirst())
            assertNotNull(it.getString(0))
        }
    }

    private companion object {
        const val V4_VERSION: Int = 4
    }
}
