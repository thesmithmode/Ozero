package ru.ozero.app.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.BufferedReader
import java.io.InputStreamReader

object BootDiagnostics {

    private const val TAG = "BootDiag"

    inline fun <T> guard(name: String, default: T, block: () -> T): T {
        val started = System.nanoTime()
        return try {
            val result = block()
            val ms = (System.nanoTime() - started) / 1_000_000
            BootFileLogger.debug(TAG, "$name OK (${ms}ms)")
            result
        } catch (t: Throwable) {
            val ms = (System.nanoTime() - started) / 1_000_000
            BootFileLogger.error(TAG, "$name FAILED after ${ms}ms", t)
            default
        }
    }

    inline fun guardUnit(name: String, block: () -> Unit) {
        guard(name, Unit, block)
    }

    val coroutineHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { ctx, t ->
            BootFileLogger.error(TAG, "coroutine uncaught name=${ctx[kotlinx.coroutines.CoroutineName]?.name}", t)
        }

    fun installWtfHandler() {
        guardUnit("installWtfHandler") {
            val previous = Log.setWtfHandler { tag, what, _ ->
                BootFileLogger.error(TAG, "WTF tag=$tag msg=${what.message}", what)
            }
            BootFileLogger.info(TAG, "wtfHandler installed (previous=${previous?.javaClass?.name})")
        }
    }

    fun dumpExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            BootFileLogger.info(TAG, "exitReasons skipped (sdk<30)")
            return
        }
        guardUnit("dumpExitReasons") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@guardUnit
            val list = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_REASONS)
            if (list.isEmpty()) {
                BootFileLogger.info(TAG, "exitReasons empty")
                return@guardUnit
            }
            BootFileLogger.info(TAG, "exitReasons count=${list.size}")
            for (info in list) {
                val reasonName = reasonToString(info.reason)
                val msg = "exit pid=${info.pid} reason=$reasonName status=${info.status} " +
                    "importance=${info.importance} ts=${info.timestamp} desc=${info.description}"
                BootFileLogger.info(TAG, msg)
                if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    info.reason == ApplicationExitInfo.REASON_ANR ||
                    info.reason == ApplicationExitInfo.REASON_CRASH
                ) {
                    runCatching {
                        info.traceInputStream?.use { stream ->
                            val text = BufferedReader(InputStreamReader(stream)).readText()
                            BootFileLogger.error(TAG, "trace pid=${info.pid}:\n$text")
                        }
                    }.onFailure { BootFileLogger.warn(TAG, "trace read failed pid=${info.pid}", it) }
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

    private const val MAX_REASONS = 10
}
