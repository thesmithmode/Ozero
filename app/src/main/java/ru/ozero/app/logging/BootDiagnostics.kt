package ru.ozero.app.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

object BootDiagnostics {

    const val TAG = "BootDiag"

    /** Ловит только Throwable. SIGSEGV/SIGABRT в JNI_OnLoad обходит JVM stack — не оборачивать loadLibrary. */
    fun <T> guard(name: String, default: T, block: () -> T): T {
        val started = System.nanoTime()
        return try {
            block()
        } catch (t: Throwable) {
            val ms = (System.nanoTime() - started) / 1_000_000
            BootFileLogger.error(TAG, "$name FAILED after ${ms}ms", t)
            default
        }
    }

    fun guardUnit(name: String, block: () -> Unit) {
        guard(name, Unit, block)
    }

    val coroutineHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { ctx, t ->
            BootFileLogger.error(
                TAG,
                "coroutine uncaught name=${ctx[kotlinx.coroutines.CoroutineName]?.name}",
                t,
            )
        }

    @Volatile
    private var uncaughtInstalled: Boolean = false

    fun installUncaughtHandler(crashSink: ((Thread, Throwable) -> Unit)? = null) {
        if (uncaughtInstalled) return
        uncaughtInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                BootFileLogger.error(
                    TAG,
                    "uncaught thread=${thread.name} tid=${thread.id} type=${throwable.javaClass.name} " +
                        "heap=${heapSummary()}",
                    throwable,
                )
            }
            if (crashSink != null) {
                runCatching { crashSink(thread, throwable) }
            }
            runCatching { previous?.uncaughtException(thread, throwable) }
        }
    }

    fun dumpExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        guardUnit("dumpExitReasons") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@guardUnit
            val list = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_REASONS)
            if (list.isEmpty()) return@guardUnit
            BootFileLogger.info(TAG, "exitReasons count=${list.size}")
            for (info in list) {
                val reasonName = reasonToString(info.reason)
                val msg = "exit pid=${info.pid} reason=$reasonName status=${info.status} " +
                    "importance=${info.importance} ts=${info.timestamp} desc=${info.description}"
                BootFileLogger.info(TAG, msg)
                if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    runCatching {
                        info.traceInputStream?.use { stream ->
                            val result = readBytesTruncated(stream, MAX_TOMBSTONE_BYTES)
                            val debugDir = File(context.filesDir, "debug")
                            val saved = saveTombstone(debugDir, info.pid, info.timestamp, result.bytes)
                            BootFileLogger.error(
                                TAG,
                                "tombstone saved pid=${info.pid} bytes=${result.bytes.size} " +
                                    "truncated=${result.truncated} path=${saved.absolutePath}",
                            )
                        }
                    }.onFailure { BootFileLogger.warn(TAG, "tombstone read failed pid=${info.pid}", it) }
                } else if (info.reason == ApplicationExitInfo.REASON_ANR ||
                    info.reason == ApplicationExitInfo.REASON_CRASH
                ) {
                    runCatching {
                        info.traceInputStream?.use { stream ->
                            val reader = BufferedReader(InputStreamReader(stream))
                            val result = readTextTruncated(reader, MAX_TRACE_CHARS)
                            BootFileLogger.error(
                                TAG,
                                "trace pid=${info.pid} truncated=${result.truncated}:\n${result.text}",
                            )
                        }
                    }.onFailure { BootFileLogger.warn(TAG, "trace read failed pid=${info.pid}", it) }
                }
                if (info.reason == ApplicationExitInfo.REASON_SIGNALED) {
                    val signalName = signalToString(info.status)
                    BootFileLogger.warn(
                        TAG,
                        "signaled pid=${info.pid} signal=$signalName(${info.status}) desc=${info.description}",
                    )
                }
            }
        }
    }

    private fun reasonToString(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH_JVM"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "code=$reason"
    }

    internal fun saveTombstone(debugDir: File, pid: Int, timestamp: Long, bytes: ByteArray): File {
        if (!debugDir.exists()) debugDir.mkdirs()
        val file = File(debugDir, "tombstone-$pid-$timestamp.pb")
        file.writeBytes(bytes)
        return file
    }

    internal fun readBytesTruncated(input: InputStream, maxBytes: Int): TruncatedBytes {
        val out = ByteArrayOutputStream(minOf(maxBytes, READ_BUFFER_BYTES))
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read == -1) return TruncatedBytes(out.toByteArray(), truncated = false)
            out.write(buffer, 0, read)
            total += read
        }
        return TruncatedBytes(out.toByteArray(), truncated = input.read() != -1)
    }

    internal fun readTextTruncated(reader: BufferedReader, maxChars: Int): TruncatedText {
        val out = StringBuilder(minOf(maxChars, READ_BUFFER_BYTES))
        val buffer = CharArray(READ_BUFFER_BYTES)
        var total = 0
        while (total < maxChars) {
            val read = reader.read(buffer, 0, minOf(buffer.size, maxChars - total))
            if (read == -1) return TruncatedText(out.toString(), truncated = false)
            out.append(buffer, 0, read)
            total += read
        }
        return TruncatedText(out.toString(), truncated = reader.read() != -1)
    }

    internal class TruncatedBytes(
        val bytes: ByteArray,
        val truncated: Boolean,
    )

    internal data class TruncatedText(
        val text: String,
        val truncated: Boolean,
    )

    internal fun extractAsciiStrings(bytes: ByteArray, minLen: Int = 6): String {
        val out = StringBuilder()
        val current = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7E) {
                current.append(v.toChar())
            } else {
                flushRun(out, current, minLen)
            }
        }
        flushRun(out, current, minLen)
        return out.toString()
    }

    private fun flushRun(out: StringBuilder, current: StringBuilder, minLen: Int) {
        if (current.length >= minLen) {
            if (out.isNotEmpty()) out.append('\n')
            out.append(current)
        }
        current.setLength(0)
    }

    internal fun signalToString(signal: Int): String = when (signal) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        6 -> "SIGABRT"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        13 -> "SIGPIPE"
        15 -> "SIGTERM"
        19 -> "SIGSTOP"
        else -> "signal=$signal"
    }

    internal fun heapSummary(): String = runCatching {
        val runtime = Runtime.getRuntime()
        val max = runtime.maxMemory()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        "used=${used / BYTES_IN_MIB}MiB total=${total / BYTES_IN_MIB}MiB " +
            "free=${free / BYTES_IN_MIB}MiB max=${max / BYTES_IN_MIB}MiB"
    }.getOrDefault("unavailable")

    private const val BYTES_IN_MIB = 1024L * 1024L
    private const val MAX_REASONS = 10
    private const val MAX_TOMBSTONE_BYTES = 512 * 1024
    private const val MAX_TRACE_CHARS = 64 * 1024
    private const val READ_BUFFER_BYTES = 8 * 1024
}
