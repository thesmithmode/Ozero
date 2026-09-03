package ru.ozero.singboxprocess

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import ru.ozero.enginesingbox.ISingboxEngineProcess
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginesingbox.SingboxStats
import ru.ozero.enginesingbox.singboxConfigFingerprint
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.singboxcore.Libsingboxgojni

class SingboxEngineService : Service() {
    private val stopLock = Any()

    private val binder = object : ISingboxEngineProcess.Stub() {

        override fun startWithConfig(
            ownerId: Long,
            tunFd: ParcelFileDescriptor,
            singboxJsonConfig: String,
            protector: ISingboxProtector,
        ) {
            val rawFd = tunFd.detachFd()
            val detachedTunFd = DetachedTunFd(rawFd)
            try {
                PersistentLoggers.debug(
                    TAG,
                    "startWithConfig entry rawFd=$rawFd configLen=${singboxJsonConfig.length} " +
                        "fingerprint=${singboxJsonConfig.singboxConfigFingerprint()}",
                )
                startRuntimeWithWatchdog {
                    runBlocking {
                        SingboxRuntime.start(
                            this@SingboxEngineService,
                            ownerId,
                            rawFd,
                            singboxJsonConfig,
                            SingboxProtectorBridge(protector),
                            detachedTunFd,
                        )
                    }
                }
                check(detachedTunFd.state == TunFdOwnershipState.PROVIDED_TO_LIBBOX)
            } catch (t: Throwable) {
                detachedTunFd.closeIfDetached()
                PersistentLoggers.error(TAG, "startWithConfig failed exceptionClass=${t::class.java.simpleName}")
                stopAndWait(ownerId, DEFAULT_STOP_TIMEOUT_MS)
                throw t
            }
        }

        override fun startWithConfigFile(
            ownerId: Long,
            tunFd: ParcelFileDescriptor,
            configFilePath: String,
            protector: ISingboxProtector,
        ) {
            val rawFd = tunFd.detachFd()
            val detachedTunFd = DetachedTunFd(rawFd)
            try {
                startRuntimeWithWatchdog {
                    val json = java.io.File(configFilePath).readText()
                    PersistentLoggers.debug(
                        TAG,
                        "startWithConfigFile entry rawFd=$rawFd configLen=${json.length} " +
                            "fingerprint=${json.singboxConfigFingerprint()}",
                    )
                    runBlocking {
                        SingboxRuntime.start(
                            this@SingboxEngineService,
                            ownerId,
                            rawFd,
                            json,
                            SingboxProtectorBridge(protector),
                            detachedTunFd,
                        )
                    }
                }
                check(detachedTunFd.state == TunFdOwnershipState.PROVIDED_TO_LIBBOX)
            } catch (t: Throwable) {
                detachedTunFd.closeIfDetached()
                PersistentLoggers.error(TAG, "startWithConfigFile failed exceptionClass=${t::class.java.simpleName}")
                stopAndWait(ownerId, DEFAULT_STOP_TIMEOUT_MS)
                throw t
            }
        }

        override fun startProxyMode(
            ownerId: Long,
            singboxJsonConfig: String,
            protector: ISingboxProtector,
        ) {
            PersistentLoggers.debug(
                TAG,
                "startProxyMode entry configLen=${singboxJsonConfig.length} " +
                    "fingerprint=${singboxJsonConfig.singboxConfigFingerprint()}",
            )
            try {
                startRuntimeWithWatchdog {
                    runBlocking {
                        SingboxRuntime.start(
                            this@SingboxEngineService,
                            ownerId,
                            NO_TUN_FD,
                            singboxJsonConfig,
                            SingboxProtectorBridge(protector),
                        )
                    }
                }
            } catch (t: Throwable) {
                PersistentLoggers.error(
                    TAG,
                    "startProxyMode failed exceptionClass=${t::class.java.simpleName} stableCategory=runtime-start " +
                        "sanitizedMessage=${redactSingboxMessage(t.message.orEmpty())}",
                )
                stopAndWait(ownerId, DEFAULT_STOP_TIMEOUT_MS)
                throw t
            }
        }

        override fun startProxyModeIfIdle(
            ownerId: Long,
            singboxJsonConfig: String,
            protector: ISingboxProtector,
        ): Boolean = try {
            startRuntimeWithWatchdog {
                runBlocking {
                    SingboxRuntime.startIfIdle(
                        this@SingboxEngineService,
                        ownerId,
                        singboxJsonConfig,
                        SingboxProtectorBridge(protector),
                    )
                }
            }
        } catch (t: Throwable) {
            stopAndWait(ownerId, DEFAULT_STOP_TIMEOUT_MS)
            throw t
        }

        override fun stop(ownerId: Long) {
            stopAndWait(ownerId, DEFAULT_STOP_TIMEOUT_MS)
        }

        override fun stopAndWait(ownerId: Long, timeoutMs: Long): Boolean = synchronized(stopLock) {
            stopRuntimeAndWait(ownerId, timeoutMs)
        }

        override fun runtimeRunning(): Boolean = SingboxRuntime.isRunning()

        override fun processId(): Int = android.os.Process.myPid()

        private fun <T> startRuntimeWithWatchdog(block: () -> T): T {
            val finished = launchHardWatchdog(
                DEFAULT_START_TIMEOUT_MS,
                "native start watchdog expired; terminating isolated runtime",
                START_WATCHDOG_THREAD_NAME,
            )
            return try {
                val result = block()
                check(finished.compareAndSet(false, true)) { "native start watchdog expired" }
                result
            } catch (t: Throwable) {
                finished.set(true)
                throw t
            }
        }

        fun stopRuntimeAndWait(ownerId: Long?, timeoutMs: Long): Boolean {
            val boundedTimeoutMs = timeoutMs.coerceAtLeast(1L)
            val finished = launchHardWatchdog(
                boundedTimeoutMs,
                "native stop watchdog expired; terminating isolated runtime",
                STOP_WATCHDOG_THREAD_NAME,
            )
            val stopped = runCatching {
                runBlocking {
                    withTimeoutOrNull(boundedTimeoutMs) {
                        SingboxRuntime.stop(ownerId)
                        true
                    } == true
                }
            }.onFailure {
                PersistentLoggers.error(
                    TAG,
                    "stop failed exceptionClass=${it::class.java.simpleName} stableCategory=runtime-stop " +
                        "sanitizedMessage=${redactSingboxMessage(it.message.orEmpty())}",
                )
            }.getOrDefault(false)
            if (stopped) {
                finished.set(true)
            } else if (finished.compareAndSet(false, true)) {
                PersistentLoggers.error(TAG, "native stop failed; terminating isolated runtime")
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            return stopped
        }

        private fun launchHardWatchdog(
            timeoutMs: Long,
            timeoutMessage: String,
            threadName: String,
        ): AtomicBoolean {
            val finished = AtomicBoolean(false)
            Thread({
                android.os.SystemClock.sleep(timeoutMs)
                if (finished.compareAndSet(false, true)) {
                    PersistentLoggers.error(TAG, timeoutMessage)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }, threadName).apply {
                isDaemon = true
                start()
            }
            return finished
        }

        override fun getStats(): SingboxStats = SingboxStats(available = false)
    }

    override fun onCreate() {
        super.onCreate()
        Libsingboxgojni.loadOnce()
        val dataDir = applicationContext.filesDir.absolutePath + "/singbox"
        java.io.File(dataDir).mkdirs()
        java.io.File("$dataDir/tmp").mkdirs()
        SingboxRuntime.setup(dataDir)
        PersistentLoggers.debug(
            TAG,
            "SingboxEngineService created pid=${android.os.Process.myPid()} " +
                "libraryLoaded=${Libsingboxgojni.libraryLoaded} loadError=${Libsingboxgojni.loadError}",
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        synchronized(stopLock) { binder.stopRuntimeAndWait(null, DEFAULT_STOP_TIMEOUT_MS) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SingboxEngineService"
        private const val DEFAULT_START_TIMEOUT_MS = 15_000L
        private const val DEFAULT_STOP_TIMEOUT_MS = 3_000L
        private const val NO_TUN_FD = -1
        private const val START_WATCHDOG_THREAD_NAME = "singbox-runtime-start-watchdog"
        private const val STOP_WATCHDOG_THREAD_NAME = "singbox-runtime-watchdog"
    }
}
