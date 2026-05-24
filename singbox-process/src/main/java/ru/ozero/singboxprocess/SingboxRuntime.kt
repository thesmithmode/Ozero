package ru.ozero.singboxprocess

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.singboxcore.Tun2ray
import ru.ozero.singboxcore.TunConfig
import ru.ozero.singboxcore.V2RayInstance

internal object SingboxRuntime {
    private const val TAG = "SingboxRuntime"
    private const val DEFAULT_MTU = 9000
    private const val TUN_ADDR4 = "172.19.0.1"
    private const val TUN_ADDR6 = "fdfe:dcba:9876::1"
    private const val DNS4 = "1.1.1.1"
    private const val DNS6 = "2606:4700:4700::1111"

    private val mutex = Mutex()

    @Volatile
    private var v2ray: V2RayInstance? = null

    @Volatile
    private var tun2ray: Tun2ray? = null

    suspend fun start(tunFd: Int, singboxJsonConfig: String, protectorBridge: SingboxProtectorBridge) =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                check(v2ray == null) { "SingboxRuntime already running" }
                val instance = V2RayInstance()
                instance.loadConfig(singboxJsonConfig)
                instance.start()

                val tunConfig = TunConfig().apply {
                    fileDescriptor = tunFd
                    mtu = DEFAULT_MTU
                    v2Ray = instance
                    protect = true
                    protector = protectorBridge
                    addr4 = "$TUN_ADDR4/30"
                    addr6 = "$TUN_ADDR6/126"
                    dns4 = DNS4
                    dns6 = DNS6
                    enableIPv6 = true
                    sniffing = true
                    overrideDestination = true
                    trafficStats = true
                }

                val tun = Tun2ray.create(tunConfig)
                v2ray = instance
                tun2ray = tun
                Log.i(TAG, "runtime started fd=$tunFd")
            }
        }

    suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            tun2ray?.close()
            tun2ray = null
            v2ray?.stop()
            v2ray = null
            Log.i(TAG, "runtime stopped")
        }
    }

    fun queryStats(tag: String, direction: String): Long = v2ray?.queryStats(tag, direction) ?: 0L

    fun isRunning(): Boolean = v2ray != null
}
