package ru.ozero.enginewarp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers

class RealWarpSdkBridge(
    private val awgRuntime: AwgRuntime = DefaultAwgRuntime,
) : WarpSdkBridge {

    @Volatile
    private var tunnelHandle: Int = INVALID_HANDLE

    override suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
    ): WarpSdkBridge.AttachResult = withContext(Dispatchers.IO) {
        if (tunFd < 0) {
            return@withContext WarpSdkBridge.AttachResult.Failed("invalid tunFd=$tunFd")
        }
        if (iniConfig.isBlank()) {
            return@withContext WarpSdkBridge.AttachResult.Failed("empty iniConfig")
        }
        if (uapiPath.isBlank()) {
            return@withContext WarpSdkBridge.AttachResult.Failed("empty uapiPath")
        }
        try {
            val handle = awgRuntime.turnOn(tunnelName, tunFd, iniConfig, uapiPath)
            if (handle < 0) {
                PersistentLoggers.error(TAG, "awgTurnOn returned negative handle=$handle")
                return@withContext WarpSdkBridge.AttachResult.Failed("awgTurnOn handle=$handle")
            }
            tunnelHandle = handle
            PersistentLoggers.info(TAG, "awgTurnOn OK handle=$handle name=$tunnelName")
            WarpSdkBridge.AttachResult.Success
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            PersistentLoggers.error(TAG, "awgTurnOn threw: $msg")
            WarpSdkBridge.AttachResult.Failed("awgTurnOn failed: $msg")
        }
    }

    override suspend fun detachTun() {
        withContext(Dispatchers.IO) {
            val h = tunnelHandle
            if (h == INVALID_HANDLE) return@withContext
            try {
                awgRuntime.turnOff(h)
                PersistentLoggers.info(TAG, "awgTurnOff OK handle=$h")
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "awgTurnOff failed: ${t.message}")
            } finally {
                tunnelHandle = INVALID_HANDLE
            }
        }
    }

    override fun isRunning(): Boolean = tunnelHandle != INVALID_HANDLE

    private companion object {
        const val TAG = "RealWarpSdkBridge"
        const val INVALID_HANDLE = -1
    }
}

interface AwgRuntime {
    fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int
    fun turnOff(handle: Int)
}

private object DefaultAwgRuntime : AwgRuntime {
    @Volatile private var loaded = false
    private val lock = Any()
    private var loadError: Throwable? = null

    private fun loadOnce() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            try {
                System.loadLibrary("am-go")
                loaded = true
            } catch (e: Throwable) {
                loadError = e
                PersistentLoggers.error("DefaultAwgRuntime", "loadLibrary am-go failed: ${e.message}")
                throw e
            }
        }
    }

    override fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int {
        loadOnce()
        return org.amnezia.awg.GoBackend.awgTurnOn(name, tunFd, ini, uapiPath)
    }

    override fun turnOff(handle: Int) {
        loadOnce()
        org.amnezia.awg.GoBackend.awgTurnOff(handle)
    }
}
