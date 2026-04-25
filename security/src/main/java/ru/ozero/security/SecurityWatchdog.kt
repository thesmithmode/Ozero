package ru.ozero.security

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Периодическая re-проверка [SecurityGuard].
 *
 * Одноразовая enforce()-проверка на старте уязвима к late-attach (frida/gdb
 * подключаются после прохождения проверки). Watchdog тикает каждые
 * [intervalMs] миллисекунд и при первом Compromised-вердикте вызывает
 * [onCompromised] — обычно в callback'е приложение должно убить TUN, очистить
 * ключи и завершиться.
 *
 * После триггера watchdog останавливается: повторно вызывать onCompromised
 * нет смысла, и ресурсы освобождаются.
 *
 * Контракт: безопасно вызывать [start] повторно — второй вызов игнорируется
 * пока активен job. [stop] завершает корутину детерминированно.
 */
class SecurityWatchdog(
    private val guard: SecurityGuard,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val initialDelayMs: Long = intervalMs,
    private val context: CoroutineContext = Dispatchers.Default,
    private val onCompromised: (List<String>) -> Unit,
) {

    @Volatile private var job: Job? = null

    fun start(scope: CoroutineScope) {
        synchronized(this) {
            if (job?.isActive == true) {
                Log.w(TAG, "start: уже активен, игнорирую")
                return
            }
            job = scope.launch(context) { loop() }
            Log.i(TAG, "started interval=${intervalMs}ms")
        }
    }

    fun stop() {
        synchronized(this) {
            job?.cancel()
            job = null
            Log.i(TAG, "stopped")
        }
    }

    fun isRunning(): Boolean = job?.isActive == true

    private suspend fun loop() {
        delay(initialDelayMs)
        while (kotlin.coroutines.coroutineContext.isActive) {
            when (val v = guard.check()) {
                is SecurityGuard.Verdict.Clean -> Unit
                is SecurityGuard.Verdict.Compromised -> {
                    Log.e(TAG, "compromised detected, причины=${v.reasons}")
                    runCatching { onCompromised(v.reasons) }
                        .onFailure { Log.e(TAG, "onCompromised callback бросил исключение", it) }
                    return
                }
            }
            delay(intervalMs)
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 15_000L
        private const val TAG = "SecurityWatchdog"
    }
}
