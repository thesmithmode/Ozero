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
import android.util.Log
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
                    Log.e(TAG, "startWithConfig failed: ${it.message}", it)
                }
            }
        }

        override fun stop() {
            serviceScope.launch {
                runCatching { SingboxRuntime.stop() }
                    .onFailure { Log.e(TAG, "stop failed: ${it.message}", it) }
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
    }

    override fun onCreate() {
        super.onCreate()
        Libsingboxgojni.loadOnce()
        val dataDir = applicationContext.filesDir.absolutePath + "/singbox"
        java.io.File(dataDir).mkdirs()
        java.io.File("$dataDir/tmp").mkdirs()
        SingboxRuntime.setup(dataDir)
        Log.i(
            TAG,
            "SingboxEngineService created pid=${android.os.Process.myPid()} " +
                "libraryLoaded=${Libsingboxgojni.libraryLoaded} loadError=${Libsingboxgojni.loadError}",
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.launch { SingboxRuntime.stop() }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SingboxEngineService"
    }
}
