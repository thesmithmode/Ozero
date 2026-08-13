package ru.ozero.app.warp

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

internal class WarpRuntimeShutdownCoordinator(
    private val cleanup: () -> Boolean,
    private val terminateProcess: () -> Unit,
    private val waitForTimeout: () -> Unit = { SystemClock.sleep(SHUTDOWN_TIMEOUT_MS) },
    private val launchTask: (String, () -> Unit) -> Unit = ::launchDaemonThread,
) {
    private val requested = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)

    fun request() {
        if (!requested.compareAndSet(false, true)) return
        launchTask(WATCHDOG_THREAD_NAME, ::runWatchdog)
        launchTask(CLEANUP_THREAD_NAME, ::runCleanup)
    }

    private fun runCleanup() {
        val stopped = runCatching(cleanup).getOrDefault(false)
        if (stopped) {
            finished.set(true)
        } else {
            terminateOnce()
        }
    }

    private fun runWatchdog() {
        waitForTimeout()
        terminateOnce()
    }

    private fun terminateOnce() {
        if (finished.compareAndSet(false, true)) terminateProcess()
    }

    private companion object {
        const val SHUTDOWN_TIMEOUT_MS = 1_500L
        const val WATCHDOG_THREAD_NAME = "warp-runtime-watchdog"
        const val CLEANUP_THREAD_NAME = "warp-runtime-cleanup"

        fun launchDaemonThread(name: String, task: () -> Unit) {
            Thread({ task() }, name).apply {
                isDaemon = true
                start()
            }
        }
    }
}
