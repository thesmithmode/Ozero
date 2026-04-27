package ru.ozero.app.logging

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

object BootFileLogger {

    private const val TAG = "BootFile"
    private const val DIR = "debug"
    private const val FILE = "boot.log"
    private const val PREV = "boot.log.prev"
    private const val MAX_BYTES = 1_000_000L

    private val targetRef = AtomicReference<File?>(null)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (targetRef.get() != null) return
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, FILE)
            rotateIfTooLarge(file)
            targetRef.set(file)
            log("INFO", TAG, "init pid=${Process.myPid()}")
        }.onFailure { Log.e(TAG, "init failed", it) }
    }

    fun file(): File? = targetRef.get()

    fun debug(tag: String, msg: String) = log("DEBUG", tag, msg)
    fun info(tag: String, msg: String) = log("INFO", tag, msg)
    fun warn(tag: String, msg: String, t: Throwable? = null) = log("WARN", tag, msg, t)
    fun error(tag: String, msg: String, t: Throwable? = null) = log("ERROR", tag, msg, t)

    @Synchronized
    fun clear() {
        targetRef.get()?.let { runCatching { it.writeText("") } }
    }

    fun read(): String = targetRef.get()?.takeIf { it.exists() }?.readText().orEmpty()

    @Synchronized
    private fun log(level: String, tag: String, msg: String, t: Throwable? = null) {
        when (level) {
            "ERROR" -> Log.e(tag, msg, t)
            "WARN" -> Log.w(tag, msg, t)
            "DEBUG" -> Log.d(tag, msg)
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
            target.appendText(sb.toString())
            if (target.length() > MAX_BYTES) rotateIfTooLarge(target)
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
