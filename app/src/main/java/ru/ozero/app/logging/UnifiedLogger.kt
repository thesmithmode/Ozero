package ru.ozero.app.logging

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import ru.ozero.coreapi.PersistentLogger
import ru.ozero.coreapi.PersistentLoggers
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

object UnifiedLogger : PersistentLogger {

    private const val TAG = "UnifiedLogger"
    private const val DIR = "logs"
    private const val FILE = "ozero.log"
    private const val PREV = "ozero.log.prev"
    private const val MAX_BYTES = 5_000_000L

    private val targetRef = AtomicReference<File?>(null)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (targetRef.get() != null) return
        runCatching {
            val dir = resolveDir(context)
            dir.mkdirs()
            val file = File(dir, FILE)
            rotateIfTooLarge(file)
            targetRef.set(file)
            PersistentLoggers.instance = this
            log(
                "INFO",
                TAG,
                "init pid=${Process.myPid()} path=${file.absolutePath} " +
                    "sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString()} " +
                    "device=${Build.MANUFACTURER}/${Build.MODEL}",
            )
        }.onFailure { Log.e(TAG, "init failed", it) }
    }

    private fun resolveDir(context: Context): File {
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        return if (external != null) File(external, DIR) else File(context.filesDir, DIR)
    }

    fun file(): File? = targetRef.get()

    fun debug(tag: String, msg: String) = log("DEBUG", tag, msg)
    override fun info(tag: String, msg: String) = log("INFO", tag, msg)
    override fun warn(tag: String, msg: String, t: Throwable?) = log("WARN", tag, msg, t)
    override fun error(tag: String, msg: String, t: Throwable?) = log("ERROR", tag, msg, t)

    @Synchronized
    fun clear() {
        targetRef.get()?.let { runCatching { it.writeText("") } }
    }

    fun read(): String =
        targetRef.get()?.takeIf { it.exists() }?.readText().orEmpty()

    fun readTail(maxBytes: Long = 256_000L): String {
        val f = targetRef.get() ?: return ""
        if (!f.exists()) return ""
        val len = f.length()
        if (len <= maxBytes) return f.readText()
        return runCatching {
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(len - maxBytes)
                val buf = ByteArray(maxBytes.toInt())
                val read = raf.read(buf)
                if (read <= 0) "" else String(buf, 0, read, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    fun fileSize(): Long = targetRef.get()?.takeIf { it.exists() }?.length() ?: 0L

    @Synchronized
    fun log(level: String, tag: String, msg: String, t: Throwable? = null) {
        when (level) {
            "ERROR" -> Log.e(tag, msg, t)
            "WARN" -> Log.w(tag, msg, t)
            "DEBUG" -> Log.d(tag, msg)
            "VERBOSE", "TRACE" -> Log.v(tag, msg)
            else -> Log.i(tag, msg)
        }
        val target = targetRef.get() ?: return
        runCatching {
            val sb = StringBuilder()
            sb.append(tsFmt.format(Date()))
                .append(' ').append(level)
                .append(" [").append(Thread.currentThread().name).append("] ")
                .append(tag).append(": ").append(msg).append('\n')
            if (t != null) {
                val sw = StringWriter()
                PrintWriter(sw).use { t.printStackTrace(it) }
                sb.append(sw.toString())
            }
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(raf.length())
                raf.write(sb.toString().toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
            if (target.length() > MAX_BYTES) rotateIfTooLarge(target)
        }
    }

    @Synchronized
    fun writeRawSync(text: String) {
        val target = targetRef.get() ?: return
        runCatching {
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(raf.length())
                raf.write(text.toByteArray(Charsets.UTF_8))
                if (!text.endsWith("\n")) raf.write("\n".toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
        }
    }

    private fun rotateIfTooLarge(file: File) {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val prev = File(file.parentFile, PREV)
        runCatching {
            if (prev.exists()) prev.delete()
            file.renameTo(prev)
            file.createNewFile()
        }
    }
}
