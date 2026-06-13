package ru.ozero.singboxprocess

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginesingbox.ISingboxEngineProcess
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginesingbox.ISingboxStatusCallback
import ru.ozero.enginesingbox.SingboxStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.singboxcore.Libsingboxgojni

class SingboxEngineService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val binder = object : ISingboxEngineProcess.Stub() {

        override fun startWithConfig(
            tunFd: ParcelFileDescriptor,
            singboxJsonConfig: String,
            protector: ISingboxProtector,
        ) {
            val rawFd = tunFd.detachFd()
            kotlinx.coroutines.runBlocking {
                SingboxRuntime.start(rawFd, singboxJsonConfig, SingboxProtectorBridge(protector))
            }
        }

        override fun startWithConfigFile(
            tunFd: ParcelFileDescriptor,
            configFilePath: String,
            protector: ISingboxProtector,
        ) {
            val rawFd = tunFd.detachFd()
            val json = java.io.File(configFilePath).readText()
            kotlinx.coroutines.runBlocking {
                SingboxRuntime.start(rawFd, json, SingboxProtectorBridge(protector))
            }
        }

        override fun startProxyMode(
            singboxJsonConfig: String,
            protector: ISingboxProtector,
        ) {
            kotlinx.coroutines.runBlocking {
                SingboxRuntime.start(NO_TUN_FD, singboxJsonConfig, SingboxProtectorBridge(protector))
            }
        }

        override fun stop() {
            stopAndWait(DEFAULT_STOP_TIMEOUT_MS)
        }

        override fun stopAndWait(timeoutMs: Long): Boolean = stopRuntimeAndWait(timeoutMs)

        override fun runtimeRunning(): Boolean = SingboxRuntime.isRunning()

        private fun stopRuntimeAndWait(timeoutMs: Long): Boolean {
            val boundedTimeoutMs = timeoutMs.coerceAtLeast(1L)
            return runCatching {
                runBlocking {
                    withTimeoutOrNull(boundedTimeoutMs) {
                        SingboxRuntime.stop()
                        true
                    } == true
                }
            }.onFailure {
                PersistentLoggers.error(TAG, "stop failed: ${it.message}", it)
            }.getOrDefault(false).also { stopped ->
                if (!stopped) PersistentLoggers.warn(TAG, "stop timed out after ${boundedTimeoutMs}ms")
            }
        }

        override fun getStats(): SingboxStats {
            val status = SingboxRuntime.getLastStatus()
            return if (status != null) {
                SingboxStats(
                    txRateProxy = status.uplink,
                    rxRateProxy = status.downlink,
                    txTotal = status.uplinkTotal,
                    rxTotal = status.downlinkTotal,
                    activeConnections = status.connectionsIn + status.connectionsOut,
                )
            } else {
                SingboxStats()
            }
        }

        override fun registerStatusCallback(cb: ISingboxStatusCallback?) {}

        override fun urlTest(profileId: Long): Long = -1

        override fun setPerAppPackages(packages: Array<String>?, isAllowList: Boolean) {
            PersistentLoggers.warn(TAG, "per-app routing not yet implemented")
        }
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
        binder.stopAndWait(DEFAULT_STOP_TIMEOUT_MS)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SingboxEngineService"
        private const val DEFAULT_STOP_TIMEOUT_MS = 3_000L
        private const val NO_TUN_FD = -1
    }
}
