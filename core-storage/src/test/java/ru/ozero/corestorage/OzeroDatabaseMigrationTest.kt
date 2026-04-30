package ru.ozero.corestorage

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroDatabaseMigrationTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/corestorage/OzeroDatabase.kt")
        assertTrue(f.exists(), "OzeroDatabase.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `database version 4 после удаления ConnectionLog`() {
        assertTrue(
            source.contains("version = 4"),
            "OzeroDatabase должен быть version=4 после удаления ConnectionLog таблицы.",
        )
    }

    @Test
    fun `entities не содержит ConnectionLogEntity`() {
        assertTrue(
            !source.contains("ConnectionLogEntity"),
            "После удаления ConnectionLog — нет упоминаний в entities.",
        )
    }

    @Test
    fun `Migration_3_4 выполняет DROP TABLE connection_logs`() {
        assertTrue(
            source.contains("Migration(3, 4)") || source.contains("MIGRATION_3_4"),
            "Должна быть Migration(3, 4) для существующих установок.",
        )
        assertTrue(
            source.contains("DROP TABLE IF EXISTS connection_logs"),
            "Migration_3_4 обязан DROP TABLE connection_logs — иначе старые установки крашатся " +
                "при запуске на schema mismatch.",
        )
    }

    @Test
    fun `MIGRATION_3_4 зарегистрирован в addMigrations`() {
        val createBody = source.substringAfter("fun create(context: Context)").substringBefore("\n    }\n")
        assertTrue(
            createBody.contains("MIGRATION_3_4"),
            "addMigrations должен включать MIGRATION_3_4 — иначе Room проигнорирует и упадёт.",
        )
    }
}
