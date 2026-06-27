package ru.ozero.app.warp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import org.amnezia.awg.GoBackend
import org.amnezia.awg.ProxyGoBackend
import org.amnezia.awg.backend.SocketProtector
import ru.ozero.enginewarp.IWarpEngineProcess
import ru.ozero.enginewarp.WarpEngineServiceActions
import ru.ozero.enginewarp.WarpTurnOnResult

class WarpEngineService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START_SESSION -> startForegroundSession()
            ACTION_STOP_SESSION -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                START_NOT_STICKY
            }
            else -> startForegroundSession()
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
        }

        override fun turnOff(handle: Int) {
            ensureLibraryLoaded()
            Log.i(TAG, "awgTurnOff handle=$handle")
            GoBackend.awgTurnOff(handle)
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
            return ProxyGoBackend.awgStartProxy(name, iniConfig, uapiPath, port)
        }

        override fun stopProxy() {
            ensureLibraryLoaded()
            Log.i(TAG, "awgStopProxy")
            ProxyGoBackend.awgStopProxy()
        }

        override fun resetProxyGlobals() {
            ensureLibraryLoaded()
            ProxyGoBackend.awgResetJNIGlobals()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startForegroundSession(): Int =
        if (enterForeground()) START_STICKY else START_NOT_STICKY

    private fun enterForeground(): Boolean {
        createChannel()
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Ozero WARP")
            .setContentText("WARP engine active")
            .setOngoing(true)
            .build()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.recoverCatching { t ->
            Log.w(TAG, "startForeground SPECIAL_USE failed, fallback to MANIFEST: ${t.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { t ->
            Log.e(TAG, "startForeground failed: ${t.message}")
            stopSelf()
        }.isSuccess
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ozero WARP", NotificationManager.IMPORTANCE_LOW),
        )
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
        const val CHANNEL_ID = "ozero_warp_engine"
        const val NOTIFICATION_ID = 7302
    }
}
