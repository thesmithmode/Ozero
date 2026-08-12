package ru.ozero.enginewarp

import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers

private const val DEFAULT_STOP_CLEANUP_TIMEOUT_MS = 1_250L
private const val DEFAULT_FORCE_TERMINATION_JOIN_TIMEOUT_MS = 250L
private const val DEFAULT_START_CLEANUP_WAIT_MS = 500L

class WarpRuntimeControl(
    val pluginScope: CoroutineScope? = null,
    val rawFdCloser: (Int) -> Unit = { fd -> ParcelFileDescriptor.adoptFd(fd).close() },
    val stopCleanupTimeoutMs: Long = DEFAULT_STOP_CLEANUP_TIMEOUT_MS,
    val forceTerminationJoinTimeoutMs: Long = DEFAULT_FORCE_TERMINATION_JOIN_TIMEOUT_MS,
    val startCleanupWaitMs: Long = DEFAULT_START_CLEANUP_WAIT_MS,
)

internal class WarpRuntimeCleanupCoordinator(
    private val sdkBridge: WarpSdkBridge,
    private val scope: CoroutineScope,
    private val control: WarpRuntimeControl,
) {
    private val savedTunFd = AtomicInteger(INVALID_TUN_FD)
    private val cleanupJobLock = Any()

    @Volatile private var cleanupJob: Job? = null

    fun currentTunFd(): Int = savedTunFd.get()

    fun ownTunFd(tunFd: Int) {
        val staleFd = savedTunFd.getAndSet(tunFd)
        if (staleFd != tunFd) closeTunFd(staleFd)
    }

    fun closeOwnedTunFd() {
        closeTunFd(savedTunFd.getAndSet(INVALID_TUN_FD))
    }

    suspend fun stopRuntime() {
        val cleanup = getOrStartCleanupJob()
        withContext(NonCancellable) {
            val completed = withTimeoutOrNull(control.stopCleanupTimeoutMs) {
                cleanup.join()
                true
            } == true
            if (!completed) {
                PersistentLoggers.error(TAG, "WARP cleanup timed out; terminating isolated runtime")
                forceTerminate()
                withTimeoutOrNull(control.forceTerminationJoinTimeoutMs) { cleanup.join() }
            }
        }
    }

    suspend fun awaitPreviousCleanup(): Boolean {
        val pending = synchronized(cleanupJobLock) {
            cleanupJob?.takeUnless { it.isCompleted }
        } ?: return true
        val completed = withTimeoutOrNull(control.startCleanupWaitMs) {
            pending.join()
            true
        } == true
        if (completed) return true
        forceTerminate()
        return withTimeoutOrNull(control.forceTerminationJoinTimeoutMs) {
            pending.join()
            true
        } == true
    }

    private fun getOrStartCleanupJob(): Job = synchronized(cleanupJobLock) {
        cleanupJob?.takeUnless { it.isCompleted } ?: scope.launch {
            runCatching { sdkBridge.stopProxy() }
                .onFailure { PersistentLoggers.error(TAG, "stopProxy cleanup failed: ${it.message}") }
            runCatching { sdkBridge.detachTun() }
                .onFailure { PersistentLoggers.error(TAG, "detachTun cleanup failed: ${it.message}") }
        }.also { launched ->
            cleanupJob = launched
            launched.invokeOnCompletion {
                synchronized(cleanupJobLock) {
                    if (cleanupJob === launched) cleanupJob = null
                }
            }
        }
    }

    private fun forceTerminate() {
        runCatching { sdkBridge.forceTerminate() }
            .onFailure { PersistentLoggers.error(TAG, "force termination failed: ${it.message}") }
    }

    private fun closeTunFd(fd: Int) {
        if (fd < 0) return
        runCatching { control.rawFdCloser(fd) }
            .onFailure { PersistentLoggers.warn(TAG, "saved TUN fd close failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "WarpRuntimeCleanup"
        const val INVALID_TUN_FD = -1
    }
}
