package ru.ozero.engineurnetwork

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.NetworkSpaceManager
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers

object UrnetworkRuntime {
    private const val TAG = "UrnetworkRuntime"
    private const val HOST = "ur.network"
    private const val ENV = "main"
    private const val LINK_HOST = "ur.io"
    private const val MIGRATION_HOST = "bringyour.com"
    private const val WALLET = "solana"
    private const val MEMORY_MIB_CAP = 64L
    private const val DEFAULT_MEM_MIB = 32L
    private const val MIB = 1024L * 1024L

    @Volatile
    private var manager: NetworkSpaceManager? = null

    @Volatile
    private var space: NetworkSpace? = null

    private val mutex = Mutex()

    suspend fun ensure(app: Application): NetworkSpace = withContext(Dispatchers.Main.immediate) {
        space?.let { return@withContext it }
        mutex.withLock {
            space?.let { return@withLock it }
            val filesPath = app.filesDir.absolutePath
            runCatching { Sdk.setLogDir(filesPath) }
                .onFailure { PersistentLoggers.warn(TAG, "Sdk.setLogDir threw: ${it.message}") }
            runCatching {
                val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val maxMib = am?.memoryClass?.toLong() ?: DEFAULT_MEM_MIB
                val sdkMib = minOf((3L * maxMib) / 4L, MEMORY_MIB_CAP)
                Sdk.setMemoryLimit(sdkMib * MIB)
            }.onFailure { PersistentLoggers.warn(TAG, "Sdk.setMemoryLimit threw: ${it.message}") }

            val mgr = manager ?: Sdk.newNetworkSpaceManager(filesPath).also { manager = it }
            val key = Sdk.newNetworkSpaceKey(HOST, ENV)
            val existing = mgr.getNetworkSpace(key)
            val ns = mgr.updateNetworkSpace(key) { v ->
                v.envSecret = null
                v.bundled = true
                v.netExposeServerIps = true
                v.netExposeServerHostNames = true
                v.linkHostName = LINK_HOST
                v.migrationHostName = MIGRATION_HOST
                v.store = null
                v.wallet = WALLET
            } ?: error("updateNetworkSpace returned null")
            if (existing == null || mgr.activeNetworkSpace == null) {
                runCatching { mgr.setActiveNetworkSpace(ns) }
                    .onFailure { PersistentLoggers.warn(TAG, "setActiveNetworkSpace threw: ${it.message}") }
            }
            runCatching { Sdk.newLoginViewController(ns.api) }
                .onFailure { PersistentLoggers.warn(TAG, "newLoginViewController threw: ${it.message}") }
            space = ns
            Log.i(TAG, "runtime ready host=$HOST env=$ENV mem=${app.filesDir.absolutePath}")
            ns
        }
    }

    fun manager(): NetworkSpaceManager? = manager
    fun space(): NetworkSpace? = space

    suspend fun release() = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            val mgr = manager
            if (mgr == null && space == null) {
                Log.i(TAG, "release skipped — runtime not initialised")
                return@withLock
            }
            runCatching { mgr?.setActiveNetworkSpace(null) }
                .onFailure { PersistentLoggers.warn(TAG, "release setActiveNetworkSpace(null) threw: ${it.message}") }
            space = null
            manager = null
            runCatching { Sdk.freeMemory() }
                .onFailure { PersistentLoggers.warn(TAG, "Sdk.freeMemory threw: ${it.message}") }
            Log.i(TAG, "runtime released — UDP sockets, file handles, Go heap freed")
        }
    }
}
