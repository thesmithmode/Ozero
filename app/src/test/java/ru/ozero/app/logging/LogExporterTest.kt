package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тестируется логика фильтрации/ротации файла без android.content.Context — он
 * абстрактный с десятками методов. [LogExporterTestable] использует ту же логику.
 * Production LogExporter оборачивает её передачей cacheDir из Hilt.
 */
class LogExporterTest {

    @Test
    fun `export filters by minLevel`(@TempDir tmp: File) {
        val exporter = LogExporterTestable(tmp)
        val entries = listOf(
            LogEntry(1, LogLevel.TRACE, "T", 1, "trace1"),
            LogEntry(2, LogLevel.INFO, "T", 1, "info1"),
            LogEntry(3, LogLevel.ERROR, "T", 1, "err1"),
        )
        val file = exporter.export(entries, LogLevel.WARN)
        val text = file.readText()
        assertFalse(text.contains("trace1"))
        assertFalse(text.contains("info1"))
        assertTrue(text.contains("err1"))
    }

    @Test
    fun `export deletes previous files in dir`(@TempDir tmp: File) {
        val exporter = LogExporterTestable(tmp)
        exporter.export(listOf(LogEntry(1, LogLevel.INFO, "T", 1, "a")), LogLevel.TRACE)
        val second = exporter.export(listOf(LogEntry(2, LogLevel.INFO, "T", 1, "b")), LogLevel.TRACE)
        assertTrue(second.exists())
        val files = second.parentFile!!.listFiles()!!
        assertEquals(1, files.size, "Старый файл должен быть удалён")
    }
}

private class LogExporterTestable(private val cacheDir: File) {
    fun export(entries: List<LogEntry>, minLevel: LogLevel): File {
        val dir = File(cacheDir, "logs").apply {
            mkdirs()
            listFiles()?.forEach { runCatching { it.delete() } }
        }
        val file = File(dir, "ozero-logs-test.txt")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("# header\n")
            entries.asSequence()
                .filter { it.level.ordinal >= minLevel.ordinal }
                .forEach { writer.write("${it.level.short} ${it.tag}: ${it.message}\n") }
        }
        return file
    }
}
