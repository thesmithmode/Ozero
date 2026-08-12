package ru.ozero.app.warp

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.amnezia.awg.GoBackend
import org.amnezia.awg.ProxyGoBackend
import org.amnezia.awg.backend.SocketProtector
import ru.ozero.commonvpn.OzeroNotificationFactory
import ru.ozero.enginewarp.IWarpEngineProcess
import ru.ozero.enginewarp.WarpEngineServiceActions
import ru.ozero.enginewarp.WarpTurnOnResult

class WarpEngineService : Service() {
    private val runtimeLock = Any()
    private val activeTunHandles = WarpNativeHandleRegistry(::turnOffNative)
    private val shutdownCoordinator = WarpRuntimeShutdownCoordinator(
        cleanup = ::stopActiveRuntime,
        terminateProcess = ::terminateCurrentProcess,
    )

    @Volatile private var proxyStarted = false
    private var lastRuntimeStopElapsedMs = NO_RUNTIME_STOP

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START_SESSION -> startForegroundSession()
            ACTION_STOP_SESSION -> {
                shutdownCoordinator.request()
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
            return synchronized(runtimeLock) {
                ensureLibraryLoaded()
                if (!prepareForNewRuntime()) {
                    runCatching { tunFd.close() }
                    return@synchronized -1
                }
                val rawFd = tunFd.detachFd()
                Log.i(TAG, "awgTurnOn name=$name fd=$rawFd iniLen=${iniConfig.length}")
                GoBackend.awgTurnOn(name, rawFd, iniConfig, uapiPath)
                    .also(activeTunHandles::register)
            }
        }

        override fun turnOff(handle: Int) {
            synchronized(runtimeLock) {
                activeTunHandles.release(handle)
            }
        }

        override fun socketV4Fd(handle: Int): ParcelFileDescriptor? {
            return synchronized(runtimeLock) {
                ensureLibraryLoaded()
                val fd = GoBackend.awgGetSocketV4(handle)
                if (fd <= 0) return@synchronized null
                runCatching { ParcelFileDescriptor.fromFd(fd) }.getOrNull()
            }
        }

        override fun socketV6Fd(handle: Int): ParcelFileDescriptor? {
            return synchronized(runtimeLock) {
                ensureLibraryLoaded()
                val fd = GoBackend.awgGetSocketV6(handle)
                if (fd <= 0) return@synchronized null
                runCatching { ParcelFileDescriptor.fromFd(fd) }.getOrNull()
            }
        }

        override fun version(): String = synchronized(runtimeLock) {
            runCatching {
                ensureLibraryLoaded()
                GoBackend.awgVersion() ?: "null"
            }.getOrDefault("error")
        }

        override fun turnOnAndGetSockets(
            tunFd: ParcelFileDescriptor,
            name: String,
            iniConfig: String,
            uapiPath: String,
        ): WarpTurnOnResult {
            return synchronized(runtimeLock) {
                ensureLibraryLoaded()
                if (!prepareForNewRuntime()) {
                    runCatching { tunFd.close() }
                    return@synchronized WarpTurnOnResult(-1, null, null)
                }
                val rawFd = tunFd.detachFd()
                Log.i(TAG, "awgTurnOn(combined) name=$name fd=$rawFd iniLen=${iniConfig.length}")
                val handle = GoBackend.awgTurnOn(name, rawFd, iniConfig, uapiPath)
                // amnezia AWG: errors → -1, 0 = валидный первый tunnel slot. Не менять на `<= 0` (ломает чистый старт).
                if (handle < 0) {
                    Log.w(TAG, "awgTurnOn returned handle=$handle (<0 = SDK error) — skip socket fetch")
                    return@synchronized WarpTurnOnResult(handle, null, null)
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
                WarpTurnOnResult(handle, v4Pfd, v6Pfd)
            }
        }

        override fun startProxy(
            name: String,
            iniConfig: String,
            uapiPath: String,
            port: Int,
        ): Int {
            return synchronized(runtimeLock) {
                ensureLibraryLoaded()
                if (!prepareForNewRuntime()) return@synchronized -1
                Log.i(TAG, "awgStartProxy name=$name port=$port iniLen=${iniConfig.length}")
                ProxyGoBackend.awgSetSocketProtector(SocketProtector { _ -> 1 })
                ProxyGoBackend.awgStartProxy(name, iniConfig, uapiPath, port).also { handle ->
                    proxyStarted = handle >= 0
                }
            }
        }

        override fun stopProxy() {
            synchronized(runtimeLock) {
                ensureLibraryLoaded()
                Log.i(TAG, "awgStopProxy")
                ProxyGoBackend.awgStopProxy()
                proxyStarted = false
                markRuntimeStopped()
            }
        }

        override fun resetProxyGlobals() {
            synchronized(runtimeLock) {
                ensureLibraryLoaded()
                ProxyGoBackend.awgResetJNIGlobals()
            }
        }

        override fun forceTerminate() = terminateCurrentProcess()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        shutdownCoordinator.request()
        leaveForeground()
        stopSelf()
        return false
    }

    override fun onDestroy() {
        shutdownCoordinator.request()
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

    private fun stopActiveRuntime(): Boolean = synchronized(runtimeLock) {
        val tunnelsStopped = releaseActiveTunnels()
        val proxyStopped = stopActiveProxy()
        tunnelsStopped && proxyStopped
    }

    private fun prepareForNewRuntime(): Boolean {
        val tunnelsStopped = releaseActiveTunnels()
        val proxyStopped = stopActiveProxy()
        val stopped = tunnelsStopped && proxyStopped
        if (stopped) awaitRuntimeRestartCooldown()
        return stopped
    }

    private fun releaseActiveTunnels(): Boolean {
        repeat(CLEANUP_ATTEMPTS) {
            if (activeTunHandles.releaseAll()) return true
        }
        return activeTunHandles.isEmpty()
    }

    private fun stopActiveProxy(): Boolean {
        if (!proxyStarted) return true
        repeat(CLEANUP_ATTEMPTS) {
            val reset = runCatching {
                ensureLibraryLoaded()
                ProxyGoBackend.awgResetJNIGlobals()
            }.onFailure { Log.e(TAG, "awgResetJNIGlobals cleanup failed: ${it.message}") }
            val stopped = runCatching {
                ProxyGoBackend.awgStopProxy()
            }.onSuccess {
                proxyStarted = false
                markRuntimeStopped()
            }.onFailure { Log.e(TAG, "awgStopProxy cleanup failed: ${it.message}") }
            if (reset.isSuccess && stopped.isSuccess) return true
            if (stopped.isSuccess) return false
        }
        return !proxyStarted
    }

    private fun markRuntimeStopped() {
        lastRuntimeStopElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun awaitRuntimeRestartCooldown() {
        if (lastRuntimeStopElapsedMs == NO_RUNTIME_STOP) return
        val elapsed = SystemClock.elapsedRealtime() - lastRuntimeStopElapsedMs
        val remaining = RUNTIME_RESTART_COOLDOWN_MS - elapsed
        if (remaining > 0L) SystemClock.sleep(remaining)
    }

    private fun terminateCurrentProcess() {
        Log.e(TAG, "native cleanup was not confirmed; terminating isolated WARP process")
        Process.killProcess(Process.myPid())
    }

    private fun turnOffNative(handle: Int) {
        try {
            ensureLibraryLoaded()
            Log.i(TAG, "awgTurnOff handle=$handle")
            GoBackend.awgTurnOff(handle)
            markRuntimeStopped()
        } catch (t: Throwable) {
            Log.e(TAG, "awgTurnOff cleanup failed: ${t.message}")
            throw t
        }
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
        const val CLEANUP_ATTEMPTS = 2
        const val RUNTIME_RESTART_COOLDOWN_MS = 300L
        const val NO_RUNTIME_STOP = -1L
    }
}
