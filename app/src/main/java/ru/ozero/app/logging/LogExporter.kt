package ru.ozero.app.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogExporter(private val context: Context) {

    fun export(entries: List<LogEntry>, minLevel: LogLevel): File {
        val dir = File(context.cacheDir, DIR_NAME).apply {
            mkdirs()
            listFiles()?.forEach { runCatching { it.delete() } }
        }
        val ts = TIMESTAMP_FORMAT.format(Date())
        val file = File(dir, "ozero-logs-$ts.txt")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("# Ozero logs export — minLevel=${minLevel.name}, captured=${entries.size}\n")
            entries.asSequence()
                .filter { it.level.ordinal >= minLevel.ordinal }
                .forEach { entry ->
                    writer.write(formatLine(entry))
                    writer.write("\n")
                }
        }
        return file
    }

    private fun formatLine(entry: LogEntry): String {
        val ts = TIMESTAMP_FORMAT_LINE.format(Date(entry.timestampMs))
        return "$ts ${entry.level.short} ${entry.tag}: ${entry.message}"
    }

    companion object {
        const val DIR_NAME: String = "logs"
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        private val TIMESTAMP_FORMAT_LINE = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
