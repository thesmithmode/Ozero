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
            val config = runCatching { java.io.File(configFilePath).readText() }.getOrElse {
                PersistentLoggers.error(TAG, "startWithConfigFile: cannot read $configFilePath: ${it.message}")
                return
            }
            startWithConfig(tunFd, config, protector)
        }

        override fun stop() {
            serviceScope.launch {
                runCatching { SingboxRuntime.stop() }
                    .onFailure { PersistentLoggers.error(TAG, "stop failed: ${it.message}", it) }
            }
        }

        override fun getStats(): SingboxStats {
            val txTotal = SingboxRuntime.queryStats("proxy", "uplink")
            val rxTotal = SingboxRuntime.queryStats("proxy", "downlink")
            return SingboxStats(
                txRateProxy = txTotal,
                rxRateProxy = rxTotal,
                txTotal = txTotal,
                rxTotal = rxTotal,
                activeConnections = if (SingboxRuntime.isRunning()) 1 else 0,
            )
        }

        override fun registerStatusCallback(cb: ISingboxStatusCallback?) {}

        override fun urlTest(profileId: Long, url: String): Int = -1

        override fun setPerAppPackages(packages: Array<out String>?, isAllowList: Boolean) {
            PersistentLoggers.warn(TAG, "setPerAppPackages called but per-app routing not yet implemented")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Libsingboxgojni.loadOnce()
        PersistentLoggers.info(TAG, "SingboxEngineService created pid=${android.os.Process.myPid()} libraryLoaded=${Libsingboxgojni.libraryLoaded} loadError=${Libsingboxgojni.loadError}")
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
