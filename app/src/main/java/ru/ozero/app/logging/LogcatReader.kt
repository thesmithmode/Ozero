package ru.ozero.app.logging

import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

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

    fun clearAll() {
        runCatching {
            ProcessBuilder("logcat", "-c").redirectErrorStream(true).start().waitFor()
        }.onFailure { Log.w(TAG, "logcat -c failed", it) }
        buffer.clear()
    }

    private suspend fun runLoop() {
        buffer.append(diagnostic(LogLevel.INFO, "LogcatReader started pid=${Process.myPid()}"))
        var attempt = 0
        var everReadLines = false
        while (scope.isActive) {
            val process = runCatching {
                ProcessBuilder(
                    "logcat",
                    "-v",
                    "threadtime",
                    "--pid=${Process.myPid()}",
                ).redirectErrorStream(true).start()
            }.onFailure {
                Log.e(TAG, "logcat spawn failed", it)
                if (!everReadLines) buffer.append(diagnostic(LogLevel.ERROR, "logcat spawn failed: ${it.message}"))
            }.getOrNull()

            if (process == null) {
                delay(backoffMs(attempt++))
                continue
            }

            var linesRead = 0
            try {
                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).useLines { lines ->
                    for (raw in lines) {
                        if (!scope.isActive) break
                        val entry = LogcatLineParser.parse(raw)
                        if (entry != null) {
                            buffer.append(entry)
                            UnifiedLogger.writeRawSync("LOGCAT $raw")
                            linesRead++
                        }
                    }
                }
                if (linesRead == 0 && !everReadLines) {
                    buffer.append(
                        diagnostic(
                            LogLevel.WARN,
                            "logcat exited with 0 lines — device restricts log access. AppLogger still works.",
                        ),
                    )
                }
                if (linesRead > 0) {
                    everReadLines = true
                    attempt = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, "logcat read error", t)
                if (!everReadLines) buffer.append(diagnostic(LogLevel.WARN, "logcat read error: ${t.message}"))
            } finally {
                runCatching { process.destroy() }
            }
            if (scope.isActive) delay(backoffMs(attempt++))
        }
    }

    private fun diagnostic(level: LogLevel, msg: String) = LogEntry.now(level, TAG, msg)

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
