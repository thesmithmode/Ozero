package ru.ozero.app.warp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import org.amnezia.awg.GoBackend
import ru.ozero.enginewarp.IWarpEngineProcess

class WarpEngineService : Service() {

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
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun ensureLibraryLoaded() {
        if (libraryLoaded) return
        synchronized(this) {
            if (libraryLoaded) return
            runCatching { System.loadLibrary("am-go") }
                .onFailure { Log.e(TAG, "am-go load failed: ${it.message}") }
            libraryLoaded = true
        }
    }

    private companion object {
        const val TAG = "WarpEngineService"

        @Volatile var libraryLoaded = false
    }
}
