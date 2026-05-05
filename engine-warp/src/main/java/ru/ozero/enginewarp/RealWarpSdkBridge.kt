package ru.ozero.enginewarp

import android.content.Context
import com.getkeepsafe.relinker.ReLinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.awg.GoBackend
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtector

class RealWarpSdkBridge internal constructor(
    private val awgRuntime: AwgRuntime,
) : WarpSdkBridge {

    constructor(context: Context) : this(ReLinkerAwgRuntime(context))

    @Volatile
    private var tunnelHandle: Int = INVALID_HANDLE

    override suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.AttachResult = withContext(Dispatchers.IO) {
        if (tunFd < 0) return@withContext WarpSdkBridge.AttachResult.Failed("invalid tunFd=$tunFd")
        if (iniConfig.isBlank()) return@withContext WarpSdkBridge.AttachResult.Failed("empty iniConfig")
        if (uapiPath.isBlank()) return@withContext WarpSdkBridge.AttachResult.Failed("empty uapiPath")
        try {
            val handle = awgRuntime.turnOn(tunnelName, tunFd, iniConfig, uapiPath)
            if (handle < 0) {
                PersistentLoggers.error(TAG, "awgTurnOn returned negative handle=$handle")
                return@withContext WarpSdkBridge.AttachResult.Failed("awgTurnOn handle=$handle")
            }
            tunnelHandle = handle
            protectUnderlyingSockets(handle, protector)
            PersistentLoggers.info(TAG, "awgTurnOn OK handle=$handle name=$tunnelName")
            WarpSdkBridge.AttachResult.Success
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            PersistentLoggers.error(TAG, "awgTurnOn threw: $msg")
            WarpSdkBridge.AttachResult.Failed("awgTurnOn failed: $msg")
        }
    }

    private fun protectUnderlyingSockets(handle: Int, protector: VpnSocketProtector) {
        runCatching { awgRuntime.getSocketV4(handle) }
            .onSuccess { sock ->
                if (sock > 0) {
                    val ok = protector.protect(sock)
                    PersistentLoggers.info(TAG, "protect v4 sock=$sock ok=$ok")
                }
            }
            .onFailure { PersistentLoggers.warn(TAG, "awgGetSocketV4 threw: ${it.message}") }
        runCatching { awgRuntime.getSocketV6(handle) }
            .onSuccess { sock ->
                if (sock > 0) {
                    val ok = protector.protect(sock)
                    PersistentLoggers.info(TAG, "protect v6 sock=$sock ok=$ok")
                }
            }
            .onFailure { PersistentLoggers.warn(TAG, "awgGetSocketV6 threw: ${it.message}") }
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
    fun getSocketV4(handle: Int): Int
    fun getSocketV6(handle: Int): Int
}

class ReLinkerAwgRuntime(context: Context) : AwgRuntime {
    private val appContext = context.applicationContext

    @Volatile private var loaded = false
    private val lock = Any()

    private fun loadOnce() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            try {
                ReLinker.loadLibrary(appContext, LIB_NAME)
                loaded = true
                PersistentLoggers.info(TAG, "ReLinker loaded $LIB_NAME")
            } catch (e: Throwable) {
                PersistentLoggers.error(TAG, "ReLinker.loadLibrary $LIB_NAME failed: ${e.message}")
                throw e
            }
        }
    }

    override fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int {
        loadOnce()
        return GoBackend.awgTurnOn(name, tunFd, ini, uapiPath)
    }

    override fun turnOff(handle: Int) {
        loadOnce()
        GoBackend.awgTurnOff(handle)
    }

    override fun getSocketV4(handle: Int): Int {
        loadOnce()
        return GoBackend.awgGetSocketV4(handle)
    }

    override fun getSocketV6(handle: Int): Int {
        loadOnce()
        return GoBackend.awgGetSocketV6(handle)
    }

    private companion object {
        const val TAG = "ReLinkerAwgRuntime"
        const val LIB_NAME = "am-go"
    }
}
