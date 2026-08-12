package ru.ozero.app.warp

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import org.amnezia.awg.GoBackend
import org.amnezia.awg.ProxyGoBackend
import org.amnezia.awg.backend.SocketProtector
import ru.ozero.commonvpn.OzeroNotificationFactory
import ru.ozero.enginewarp.IWarpEngineProcess
import ru.ozero.enginewarp.WarpEngineServiceActions
import ru.ozero.enginewarp.WarpTurnOnResult

class WarpEngineService : Service() {
    private val activeTunHandles = WarpNativeHandleRegistry(::turnOffNative)
    @Volatile private var proxyStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START_SESSION -> startForegroundSession()
            ACTION_STOP_SESSION -> {
                stopActiveRuntime()
                leaveForeground()
                stopSelf(startId)
                START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    private val binder = object : IWarpEngineProcess.Stub() {

        override fun turnOn(
            tunFd: ParcelFileDescriptor,
            name: String,
            iniConfig: String,
            uapiPath: String,
        ): Int {
            ensureLibraryLoaded()
            val rawFd = tunFd.detachFd()
            Log.i(TAG, "awgTurnOn name=$name fd=$rawFd iniLen=${iniConfig.length}")
            return GoBackend.awgTurnOn(name, rawFd, iniConfig, uapiPath)
                .also(activeTunHandles::register)
        }

        override fun turnOff(handle: Int) {
            activeTunHandles.release(handle)
        }

        override fun socketV4Fd(handle: Int): ParcelFileDescriptor? {
            ensureLibraryLoaded()
            val fd = GoBackend.awgGetSocketV4(handle)
            if (fd <= 0) return null
            return runCatching { ParcelFileDescriptor.fromFd(fd) }.getOrNull()
        }

        override fun socketV6Fd(handle: Int): ParcelFileDescriptor? {
            ensureLibraryLoaded()
            val fd = GoBackend.awgGetSocketV6(handle)
            if (fd <= 0) return null
            return runCatching { ParcelFileDescriptor.fromFd(fd) }.getOrNull()
        }

        override fun version(): String = runCatching {
            ensureLibraryLoaded()
            GoBackend.awgVersion() ?: "null"
        }.getOrDefault("error")

        override fun turnOnAndGetSockets(
            tunFd: ParcelFileDescriptor,
            name: String,
            iniConfig: String,
            uapiPath: String,
        ): WarpTurnOnResult {
            ensureLibraryLoaded()
            val rawFd = tunFd.detachFd()
            Log.i(TAG, "awgTurnOn(combined) name=$name fd=$rawFd iniLen=${iniConfig.length}")
            val handle = GoBackend.awgTurnOn(name, rawFd, iniConfig, uapiPath)
            // amnezia AWG: errors → -1, 0 = валидный первый tunnel slot. Не менять на `<= 0` (ломает чистый старт).
            if (handle < 0) {
                Log.w(TAG, "awgTurnOn returned handle=$handle (<0 = SDK error) — skip socket fetch")
                return WarpTurnOnResult(handle, null, null)
            }
            activeTunHandles.register(handle)
            val v4Pfd = runCatching {
                val v4Fd = GoBackend.awgGetSocketV4(handle)
                if (v4Fd > 0) ParcelFileDescriptor.fromFd(v4Fd) else null
            }.getOrNull()
            val v6Pfd = runCatching {
                val v6Fd = GoBackend.awgGetSocketV6(handle)
                if (v6Fd > 0) ParcelFileDescriptor.fromFd(v6Fd) else null
            }.getOrNull()
            return WarpTurnOnResult(handle, v4Pfd, v6Pfd)
        }

        override fun startProxy(
            name: String,
            iniConfig: String,
            uapiPath: String,
            port: Int,
        ): Int {
            ensureLibraryLoaded()
            Log.i(TAG, "awgStartProxy name=$name port=$port iniLen=${iniConfig.length}")
            ProxyGoBackend.awgSetSocketProtector(SocketProtector { _ -> 1 })
            return ProxyGoBackend.awgStartProxy(name, iniConfig, uapiPath, port).also { handle ->
                proxyStarted = handle >= 0
            }
        }

        override fun stopProxy() {
            ensureLibraryLoaded()
            Log.i(TAG, "awgStopProxy")
            try {
                ProxyGoBackend.awgStopProxy()
            } finally {
                proxyStarted = false
            }
        }

        override fun resetProxyGlobals() {
            ensureLibraryLoaded()
            ProxyGoBackend.awgResetJNIGlobals()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        stopActiveRuntime()
        leaveForeground()
        super.onDestroy()
    }

    private fun startForegroundSession(): Int {
        val foreground = OzeroNotificationFactory(this).enterForeground(this)
        if (!foreground) stopSelf()
        return START_NOT_STICKY
    }

    private fun leaveForeground() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        }
    }

    private fun stopActiveRuntime() {
        activeTunHandles.releaseAll()
        if (!proxyStarted) return
        proxyStarted = false
        runCatching {
            ensureLibraryLoaded()
            ProxyGoBackend.awgStopProxy()
        }.onFailure { Log.e(TAG, "awgStopProxy cleanup failed: ${it.message}") }
    }

    private fun turnOffNative(handle: Int) {
        runCatching {
            ensureLibraryLoaded()
            Log.i(TAG, "awgTurnOff handle=$handle")
            GoBackend.awgTurnOff(handle)
        }.onFailure { Log.e(TAG, "awgTurnOff cleanup failed: ${it.message}") }
    }

    private fun ensureLibraryLoaded() {
        try {
            System.loadLibrary("am-go")
        } catch (t: Throwable) {
            Log.e(TAG, "am-go load failed: ${t.message}")
            throw t
        }
    }

    private companion object {
        const val TAG = "WarpEngineService"
        const val ACTION_START_SESSION = WarpEngineServiceActions.START_SESSION
        const val ACTION_STOP_SESSION = WarpEngineServiceActions.STOP_SESSION
    }
}
