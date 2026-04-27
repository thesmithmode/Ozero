package ru.ozero.app.logging

import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Читает logcat собственного процесса в фоне и кладёт распарсенные строки в
 * [LogBuffer]. На API 16+ apps могут читать только свой process logcat без
 * READ_LOGS permission (sandboxed) — это нам и нужно.
 *
 * Перезапускает logcat при падении (kill, full buffer overflow) с backoff.
 * Останавливается при cancel scope (например через [stop]).
 *
 * Безопасность: ProcessBuilder с массивом argv (без shell-интерпретации),
 * аргумент --pid формируется из Process.myPid() (int) — никакого user-input.
 */
class LogcatReader(private val buffer: LogBuffer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun shutdown() {
        scope.cancel()
    }

    /** Очистка system-logcat ring и in-memory buffer. */
    fun clearAll() {
        runCatching {
            // -c очищает logcat ring чтобы не присыпало старыми строками.
            ProcessBuilder("logcat", "-c").redirectErrorStream(true).start().waitFor()
        }.onFailure { Log.w(TAG, "logcat -c failed", it) }
        buffer.clear()
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            val process = runCatching {
                ProcessBuilder(
                    "logcat",
                    "-v",
                    "threadtime",
                    "--pid=${Process.myPid()}",
                ).redirectErrorStream(true).start()
            }.onFailure { Log.e(TAG, "logcat spawn failed", it) }.getOrNull()

            if (process == null) {
                delay(backoffMs(attempt++))
                continue
            }

            try {
                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).useLines { lines ->
                    for (raw in lines) {
                        if (!scope.isActive) break
                        val entry = LogcatLineParser.parse(raw)
                        if (entry != null) buffer.append(entry)
                    }
                }
                attempt = 0
            } catch (t: Throwable) {
                Log.w(TAG, "logcat read error", t)
            } finally {
                runCatching { process.destroy() }
            }
            if (scope.isActive) delay(backoffMs(attempt++))
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val base = 250L
        return (base shl attempt.coerceAtMost(MAX_SHIFT)).coerceAtMost(MAX_DELAY_MS)
    }

    private companion object {
        const val TAG = "LogcatReader"
        const val MAX_SHIFT = 5
        const val MAX_DELAY_MS = 8_000L
    }
}
