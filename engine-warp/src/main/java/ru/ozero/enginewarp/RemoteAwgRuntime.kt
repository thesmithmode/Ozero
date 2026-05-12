package ru.ozero.enginewarp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemoteAwgRuntime(
    private val context: Context,
    private val serviceComponent: ComponentName,
) : AwgRuntime {

    @Volatile private var engine: IWarpEngineProcess? = null
    private val bindLock = Any()

    private fun ensureConnected(): IWarpEngineProcess {
        engine?.let { return it }
        synchronized(bindLock) {
            engine?.let { return it }
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    engine = IWarpEngineProcess.Stub.asInterface(binder)
                    latch.countDown()
                    Log.i(TAG, "WarpEngineService connected process=$name")
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    engine = null
                    PersistentLoggers.warn(TAG, "WarpEngineService disconnected process=$name")
                }
            }
            val intent = Intent().setComponent(serviceComponent)
            val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            if (!bound) error("bindService failed for $serviceComponent — service не зарегистрирован в манифесте?")
            if (!latch.await(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)) {
                error("WarpEngineService connect timeout after ${CONNECT_TIMEOUT_S}s")
            }
            return engine ?: error("WarpEngineService engine null after connect")
        }
    }

    override fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int {
        val e = ensureConnected()
        val pfd = ParcelFileDescriptor.fromFd(tunFd)
        return try {
            e.turnOn(pfd, name, ini, uapiPath)
        } finally {
            runCatching { pfd.close() }
        }
    }

    override fun turnOff(handle: Int) {
        engine?.let { runCatching { it.turnOff(handle) } }
    }

    override fun getSocketV4(handle: Int): Int {
        val pfd = runCatching { engine?.socketV4Fd(handle) }.getOrNull() ?: return -1
        return pfd.detachFd()
    }

    override fun getSocketV6(handle: Int): Int {
        val pfd = runCatching { engine?.socketV6Fd(handle) }.getOrNull() ?: return -1
        return pfd.detachFd()
    }

    override fun version(): String =
        runCatching { engine?.version() ?: "disconnected" }.getOrDefault("error")

    private companion object {
        const val TAG = "RemoteAwgRuntime"
        const val CONNECT_TIMEOUT_S = 5L
    }
}
