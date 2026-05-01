package ru.ozero.corestorage

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationFourToFiveTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/corestorage/OzeroDatabase.kt")
        assertTrue(f.exists(), "OzeroDatabase.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `database version обязан быть 5 после Migration_4_5`() {
        assertTrue(
            Regex("""version\s*=\s*5""").containsMatchIn(source),
            "Database.version должен быть 5 после добавления session_stats. " +
                "Понижение версии без Migration ломает install over upgrade.",
        )
    }

    @Test
    fun `MIGRATION_4_5 declared`() {
        assertTrue(source.contains("MIGRATION_4_5"))
        assertTrue(source.contains("Migration(4, 5)"))
    }

    @Test
    fun `MIGRATION_4_5 создаёт session_stats table`() {
        val migration = source.substringAfter("MIGRATION_4_5").substringBefore("addMigrations")
        assertTrue(
            migration.contains("CREATE TABLE IF NOT EXISTS session_stats"),
            "MIGRATION_4_5 обязан CREATE TABLE session_stats — иначе SessionStatsDao крашится",
        )
        assertTrue(migration.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"))
        assertTrue(migration.contains("engineId TEXT NOT NULL"))
        assertTrue(migration.contains("startedAt INTEGER NOT NULL"))
        assertTrue(migration.contains("endedAt INTEGER"))
        assertTrue(migration.contains("rxBytes INTEGER NOT NULL DEFAULT 0"))
        assertTrue(migration.contains("txBytes INTEGER NOT NULL DEFAULT 0"))
        assertTrue(migration.contains("durationMs INTEGER NOT NULL DEFAULT 0"))
        assertTrue(migration.contains("finalStatus TEXT NOT NULL"))
    }

    @Test
    fun `addMigrations chain включает все 4 migrations`() {
        val builder = source.substringAfter("Room.databaseBuilder").substringBefore("}\n    }")
        assertTrue(builder.contains("MIGRATION_1_2"))
        assertTrue(builder.contains("MIGRATION_2_3"))
        assertTrue(builder.contains("MIGRATION_3_4"))
        assertTrue(builder.contains("MIGRATION_4_5"))
    }

    @Test
    fun `всего ровно 4 Migration_x_y declarations`() {
        val migrations = Regex("""Migration\(\d,\s*\d\)""").findAll(source).toList()
        assertEquals(
            4,
            migrations.size,
            "Ровно 4 migrations: 1→2, 2→3, 3→4, 4→5. Меняется → обновить test.",
        )
    }
}
