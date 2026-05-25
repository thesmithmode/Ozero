package ru.ozero.singboxprocess

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
            serviceScope.launch {
                runCatching {
                    SingboxRuntime.start(rawFd, singboxJsonConfig, SingboxProtectorBridge(protector))
                }.onFailure {
                    PersistentLoggers.error(TAG, "startWithConfig failed: ${it.message}", it)
                }
            }
        }

        override fun startWithConfigFile(
            tunFd: ParcelFileDescriptor,
            configFilePath: String,
            protector: ISingboxProtector,
        ) {
            val rawFd = tunFd.detachFd()
            val json = java.io.File(configFilePath).readText()
            serviceScope.launch {
                runCatching {
                    SingboxRuntime.start(rawFd, json, SingboxProtectorBridge(protector))
                }.onFailure {
                    PersistentLoggers.error(TAG, "startWithConfigFile failed: ${it.message}", it)
                }
            }
        }

        override fun stop() {
            serviceScope.launch {
                runCatching { SingboxRuntime.stop() }
                    .onFailure { PersistentLoggers.error(TAG, "stop failed: ${it.message}", it) }
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
                SingboxStats(
                    activeConnections = if (SingboxRuntime.isRunning()) 1 else 0,
                )
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
        PersistentLoggers.info(
            TAG,
            "SingboxEngineService created pid=${android.os.Process.myPid()} " +
                "libraryLoaded=${Libsingboxgojni.libraryLoaded} loadError=${Libsingboxgojni.loadError}",
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        kotlinx.coroutines.runBlocking(Dispatchers.Main.immediate) {
            runCatching { SingboxRuntime.stop() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SingboxEngineService"
    }
}
