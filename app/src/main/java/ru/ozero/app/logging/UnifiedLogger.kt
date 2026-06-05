package ru.ozero.app.logging

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import ru.ozero.enginescore.LogSanitizer
import ru.ozero.enginescore.PersistentLogger
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UnifiedLogger : PersistentLogger {

    private const val TAG = "UnifiedLogger"
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (LogFileStore.current() != null) return
        val file = LogFileStore.init(context) ?: run {
            Log.e(TAG, "init failed: log file unavailable")
            return
        }
        PersistentLoggers.instance = this
        val abi = runCatching { Build.SUPPORTED_ABIS?.joinToString().orEmpty() }.getOrDefault("")
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        log(
            "INFO",
            TAG,
            "init pid=${Process.myPid()} path=${file.absolutePath} " +
                "sdk=${Build.VERSION.SDK_INT} abi=$abi " +
                "device=$manufacturer/$model",
        )
    }

    fun file(): File? = LogFileStore.current()

    override fun trace(tag: String, msg: String) = log("TRACE", tag, msg)
    override fun debug(tag: String, msg: String) = log("DEBUG", tag, msg)
    override fun info(tag: String, msg: String) = log("INFO", tag, msg)
    override fun warn(tag: String, msg: String, t: Throwable?) = log("WARN", tag, msg, t)
    override fun error(tag: String, msg: String, t: Throwable?) = log("ERROR", tag, msg, t)

    @Synchronized
    fun clear() = LogFileStore.clear()

    fun read(): String = LogTailReader.read(LogFileStore.current(), LogFileStore.prev())

    fun readTail(maxBytes: Long = 256_000L): String =
        LogTailReader.readTail(LogFileStore.current(), LogFileStore.prev(), maxBytes)

    fun fileSize(): Long = LogFileStore.totalSize()

    @Synchronized
    fun log(level: String, tag: String, msg: String, t: Throwable? = null) {
        val safeMsg = LogSanitizer.sanitize(msg)
        val safeThrowableText = t?.let { throwable ->
            val sw = StringWriter()
            PrintWriter(sw).use { throwable.printStackTrace(it) }
            LogSanitizer.sanitize(sw.toString())
        }
        val safeLogcatMsg = buildString {
            append(safeMsg)
            if (safeThrowableText != null) {
                append('\n').append(safeThrowableText)
            }
        }
        when (level) {
            "ERROR" -> Log.e(tag, safeLogcatMsg)
            "WARN" -> Log.w(tag, safeLogcatMsg)
            "DEBUG" -> Log.d(tag, safeMsg)
            "VERBOSE", "TRACE" -> Log.v(tag, safeMsg)
            else -> Log.i(tag, safeMsg)
        }
        val target = LogFileStore.current() ?: return
        runCatching {
            val sb = StringBuilder()
            sb.append(tsFmt.format(Date()))
                .append(' ').append(level)
                .append(" [").append(Thread.currentThread().name).append("] ")
                .append(tag).append(": ").append(safeMsg).append('\n')
            if (safeThrowableText != null) {
                sb.append(safeThrowableText)
            }
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(raf.length())
                raf.write(sb.toString().toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
            if (target.length() > LogFileStore.MAX_BYTES) LogFileStore.rotateIfTooLarge(target)
        }
    }

    @Synchronized
    fun writeRawSync(text: String) {
        val target = LogFileStore.current() ?: return
        runCatching {
            val safeText = LogSanitizer.sanitize(text)
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(raf.length())
                raf.write(safeText.toByteArray(Charsets.UTF_8))
                if (!safeText.endsWith("\n")) raf.write("\n".toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
        }
    }
}
