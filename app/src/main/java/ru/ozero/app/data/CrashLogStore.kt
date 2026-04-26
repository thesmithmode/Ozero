package ru.ozero.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RT.11.1: локальный crash-лог. Никаких внешних crash reporting сервисов
 * (см. docs/privacy.md). Stack trace падающих исключений из uncaught handler'а
 * пишется в `filesDir/crashes/<timestamp>.txt`. Экспорт — по явной кнопке
 * пользователя через ACTION_SEND из DiagnosticsScreen.
 *
 * Контракт: I/O синхронный (uncaught handler нельзя запускать через coroutines —
 * процесс может погибнуть до завершения).
 */
@Singleton
class CrashLogStore(
    private val baseDir: File,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.filesDir)

    private val dir: File = File(baseDir, DIR_NAME)

    fun directory(): File = dir.also { it.mkdirs() }

    /** Возвращает crash-файлы отсортированные от свежих к старым. */
    fun list(): List<File> = dir.takeIf { it.exists() }
        ?.listFiles()?.toList()?.sortedByDescending { it.lastModified() }
        .orEmpty()

    fun write(thread: Thread, throwable: Throwable) {
        runCatching {
            dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val file = File(dir, "$ts.txt")
            val sw = StringWriter()
            sw.append("thread=").append(thread.name).append('\n')
            sw.append("time=").append(ts).append('\n')
            PrintWriter(sw).use { throwable.printStackTrace(it) }
            file.writeText(sw.toString())
            Log.e(TAG, "crash → ${file.absolutePath}")
            rotate()
        }.onFailure { Log.w(TAG, "crash write failed", it) }
    }

    /** Оставляет последние [MAX_FILES] crash-файлов, остальные удаляет. */
    private fun rotate() {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val DIR_NAME: String = "crashes"
        const val TAG: String = "CrashLogStore"
        const val MAX_FILES: Int = 10
    }
}
