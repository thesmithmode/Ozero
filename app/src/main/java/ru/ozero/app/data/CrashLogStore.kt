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
    fun list(): List<File> = sortedFiles()

    fun write(thread: Thread, throwable: Throwable) {
        runCatching {
            dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val file = File(dir, "$ts.txt")
            val sw = StringWriter()
            sw.append("thread=").append(thread.name).append('\n')
            sw.append("time=").append(ts).append('\n')
            PrintWriter(sw).use { throwable.printStackTrace(it) }
            // Санация: stack trace может содержать URI подписки/самообновления, ed25519
            // ключи в Base64, JWT — экспорт через ACTION_SEND из DiagnosticsScreen → утечка.
            // Маскируем агрессивно: лучше потеря деталей чем PII в шаренном файле.
            file.writeText(sanitize(sw.toString()))
            Log.e(TAG, "crash → ${file.absolutePath}")
            rotate()
        }.onFailure { Log.w(TAG, "crash write failed", it) }
    }

    internal fun sanitize(text: String): String {
        var out = text
        // 1) URI с user-info (vless://uuid@host, https://user:pass@host) — режем сегмент перед '@'.
        out = USERINFO_URI.replace(out) { m -> "${m.groupValues[1]}://<redacted>@${m.groupValues[3]}" }
        // 2) Голые URI proxy-схем (vless://, vmess://, trojan://, ss://, naive+https://, awg://) — заменяем целиком.
        out = PROXY_URI.replace(out, "<redacted-uri>")
        // 3) Длинные Base64/Hex blob'ы (ключи, подписи) — 32+ символов, без пробелов.
        out = LONG_TOKEN.replace(out, "<redacted-token>")
        return out
    }

    /** Оставляет последние [MAX_FILES] crash-файлов, остальные удаляет. */
    private fun rotate() {
        sortedFiles().drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }

    private fun sortedFiles(): List<File> = dir.takeIf { it.exists() }
        ?.listFiles()?.sortedByDescending { it.lastModified() }
        .orEmpty()

    companion object {
        const val DIR_NAME: String = "crashes"
        const val TAG: String = "CrashLogStore"
        const val MAX_FILES: Int = 10

        // (?i) case-insensitive. (\w+) — scheme; ([^:/@\s]+(?::[^@\s]*)?) — userinfo c опц. паролем; ([^\s/]+) — host.
        private val USERINFO_URI = Regex(
            "(?i)(\\w+)://([^:/@\\s]+(?::[^@\\s]*)?)@([^\\s/]+)",
        )

        // Голые proxy-схемы без user-info — режем целиком URI до пробела/конца строки.
        private val PROXY_URI = Regex(
            "(?i)\\b(vless|vmess|trojan|ss|hysteria2?|tuic|naive\\+https?|wireguard|awg)://\\S+",
        )

        // Base64/Base64URL/Hex блок 32+ символов — характерно для ключей и подписей.
        private val LONG_TOKEN = Regex(
            "[A-Za-z0-9+/_=-]{32,}",
        )
    }
}
