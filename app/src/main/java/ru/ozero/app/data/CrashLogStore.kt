package ru.ozero.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLogStore(
    private val baseDir: File,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.filesDir)

    private val dir: File = File(baseDir, DIR_NAME)

    fun directory(): File = dir.also { it.mkdirs() }

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
            FileOutputStream(file).use { fos ->
                fos.write(sanitize(sw.toString()).toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
            Log.e(TAG, "crash → ${file.absolutePath}")
            rotate()
        }.onFailure { Log.w(TAG, "crash write failed", it) }
    }

    internal fun sanitize(text: String): String {
        var out = text
        out = USERINFO_URI.replace(out) { m -> "${m.groupValues[1]}://<redacted>@${m.groupValues[3]}" }
        out = PROXY_URI.replace(out, "<redacted-uri>")
        out = LONG_TOKEN.replace(out, "<redacted-token>")
        return out
    }

    private fun rotate() {
        sortedFiles().drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }

    private fun sortedFiles(): List<File> = dir.takeIf { it.exists() }
        ?.listFiles()?.sortedByDescending { it.lastModified() }
        .orEmpty()

    companion object {
        const val DIR_NAME: String = "crashes"
        const val MAX_FILES: Int = 10
        private const val TAG: String = "CrashLogStore"

        private val USERINFO_URI = Regex(
            "(?i)(\\w+)://([^:/@\\s]+(?::[^@\\s]*)?)@([^\\s/]+)",
        )

        private val PROXY_URI = Regex(
            "(?i)\\b(vless|vmess|trojan|ss|hysteria2?|tuic|naive\\+https?|wireguard|awg)://\\S+",
        )

        private val LONG_TOKEN = Regex(
            "[A-Za-z0-9+/_=-]{32,}",
        )
    }
}
