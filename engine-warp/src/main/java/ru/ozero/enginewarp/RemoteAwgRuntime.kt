package ru.ozero.enginewarp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemoteAwgRuntime(
    private val context: Context,
    private val serviceComponent: ComponentName,
    private val onProcessDied: () -> Unit = {},
) : AwgRuntime {

    @Volatile
    private var engine: IWarpEngineProcess? = null

    @Volatile private var serviceConnection: ServiceConnection? = null

    @Volatile private var deathRecipient: IBinder.DeathRecipient? = null

    @Volatile private var engineBinder: IBinder? = null
    private val bindLock = Any()

    private fun ensureConnected(): IWarpEngineProcess {
        engine?.let { return it }
        synchronized(bindLock) {
            engine?.let { return it }
            serviceConnection?.let { stale ->
                runCatching { context.unbindService(stale) }
                serviceConnection = null
            }
            unlinkDeathRecipient()
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    engine = IWarpEngineProcess.Stub.asInterface(binder)
                    engineBinder = binder
                    val recipient = IBinder.DeathRecipient {
                        engine = null
                        engineBinder = null
                        val ref = serviceConnection
                        serviceConnection = null
                        if (ref != null) runCatching { context.unbindService(ref) }
                        PersistentLoggers.warn(
                            TAG,
                            "WarpEngineService binder died — Go runtime crash в :engine_warp",
                        )
                        runCatching { onProcessDied() }
                    }
                    deathRecipient = recipient
                    runCatching { binder.linkToDeath(recipient, 0) }
                        .onFailure {
                            PersistentLoggers.warn(
                                TAG,
                                "linkToDeath failed: ${it.javaClass.simpleName}: ${it.message}",
                            )
                        }
                    latch.countDown()
                    Log.i(TAG, "WarpEngineService connected process=$name")
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    engine = null
                    engineBinder = null
                    unlinkDeathRecipient()
                    val ref = serviceConnection
                    serviceConnection = null
                    if (ref != null) runCatching { context.unbindService(ref) }
                    runCatching { onProcessDied() }
                    PersistentLoggers.warn(
                        TAG,
                        "WarpEngineService disconnected process=$name - service process lost",
                    )
                }
                override fun onBindingDied(name: ComponentName?) {
                    engine = null
                    engineBinder = null
                    unlinkDeathRecipient()
                    val ref = serviceConnection
                    serviceConnection = null
                    if (ref != null) runCatching { context.unbindService(ref) }
                    PersistentLoggers.warn(TAG, "WarpEngineService binding died process=$name — connection unbound")
                    runCatching { onProcessDied() }
                }
            }
            val intent = Intent().setComponent(serviceComponent)
            val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            if (!bound) {
                runCatching { context.unbindService(conn) }
                error("bindService failed for $serviceComponent — service не зарегистрирован в манифесте?")
            }
            serviceConnection = conn
            if (!latch.await(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)) {
                runCatching { context.unbindService(conn) }
                serviceConnection = null
                error("WarpEngineService connect timeout after ${CONNECT_TIMEOUT_S}s")
            }
            return engine ?: run {
                runCatching { context.unbindService(conn) }
                serviceConnection = null
                error("WarpEngineService engine null after connect")
            }
        }
    }

    private fun unlinkDeathRecipient() {
        val binder = engineBinder
        val recipient = deathRecipient
        if (binder != null && recipient != null) {
            runCatching { binder.unlinkToDeath(recipient, 0) }
        }
        engineBinder = null
        deathRecipient = null
    }

    override fun close() {
        synchronized(bindLock) {
            unlinkDeathRecipient()
            engine = null
            serviceConnection?.let { conn ->
                runCatching { context.unbindService(conn) }
                serviceConnection = null
            }
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

    override fun turnOnAndGetSockets(name: String, tunFd: Int, ini: String, uapiPath: String): AwgTurnOnResult {
        val e = ensureConnected()
        val pfd = ParcelFileDescriptor.fromFd(tunFd)
        val combined = try {
            e.turnOnAndGetSockets(pfd, name, ini, uapiPath)
        } finally {
            runCatching { pfd.close() }
        }
        val v4 = combined.socketV4?.detachFd() ?: -1
        val v6 = combined.socketV6?.detachFd() ?: -1
        return AwgTurnOnResult(combined.handle, v4, v6)
    }

    override fun startProxy(
        name: String,
        ini: String,
        uapiPath: String,
        port: Int,
        protector: VpnSocketProtector,
    ): Int {
        val e = ensureConnected()
        return e.startProxy(name, ini, uapiPath, port)
    }

    override fun stopProxy() {
        engine?.let { runCatching { it.stopProxy() } }
    }

    override fun resetProxyGlobals() {
        engine?.let { runCatching { it.resetProxyGlobals() } }
    }

    override fun version(): String =
        runCatching { engine?.version() ?: "disconnected" }.getOrDefault("error")

    private companion object {
        const val TAG = "RemoteAwgRuntime"
        const val CONNECT_TIMEOUT_S = 5L
    }
}
