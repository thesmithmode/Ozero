package ru.ozero.singboxprocess

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

internal object SingboxRuntime {
    private const val TAG = "SingboxRuntime"
    private val mutex = Mutex()

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var lastStatus: StatusMessage? = null

    @Volatile
    private var setupDone = false

    @Volatile
    private var basePath: String = ""

    private val systemCertificatePem by lazy { loadSystemCertificatePem() }

    fun setup(basePath: String) {
        if (setupDone) return
        this.basePath = basePath
        val options = SetupOptions()
        options.basePath = basePath
        options.workingPath = basePath
        options.tempPath = "$basePath/tmp"
        Libbox.setup(options)
        setupDone = true
        PersistentLoggers.debug(TAG, "libbox setup basePath=$basePath")
    }

    suspend fun start(
        context: Context,
        tunFd: Int,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
    ) =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                val oldServer = commandServer
                if (oldServer != null) {
                    PersistentLoggers.warn(TAG, "start: already running — graceful restart")
                    runCatching { oldServer.closeService() }
                    runCatching { oldServer.close() }
                    commandServer = null
                    lastStatus = null
                }

                PersistentLoggers.debug(
                    TAG,
                    "start configLen=${singboxJsonConfig.length} fd=$tunFd",
                )

                val socketFile = java.io.File(basePath, "command.sock")
                if (socketFile.exists()) {
                    socketFile.delete()
                    PersistentLoggers.debug(TAG, "cleaned stale command.sock")
                }

                val platform = OzeroPlatformInterface(context.applicationContext, tunFd, protectorBridge)
                val handler = OzeroCommandServerHandler()

                PersistentLoggers.debug(TAG, "checkpoint: pre-CommandServer")
                val server = CommandServer(handler, platform)
                PersistentLoggers.debug(TAG, "checkpoint: post-CommandServer")
                server.start()
                PersistentLoggers.debug(TAG, "checkpoint: post-start (socket ready)")

                try {
                    server.checkConfig(singboxJsonConfig)
                    PersistentLoggers.debug(TAG, "checkpoint: checkConfig passed")
                } catch (e: Exception) {
                    PersistentLoggers.error(TAG, "checkConfig failed: ${e.message}")
                    server.close()
                    throw e
                }

                try {
                    // Go код дёргает options.AutoRedirect без nil-check → SIGABRT при null
                    server.startOrReloadService(singboxJsonConfig, OverrideOptions())
                    PersistentLoggers.debug(TAG, "checkpoint: post-startOrReloadService (box running)")
                } catch (e: Exception) {
                    PersistentLoggers.error(TAG, "startOrReloadService failed: ${e.message}")
                    server.close()
                    throw e
                }

                commandServer = server
                PersistentLoggers.info(TAG, "runtime started fd=$tunFd")
            }
        }

    suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            val server = commandServer ?: return@withLock
            runCatching { server.closeService() }
                .onFailure { PersistentLoggers.warn(TAG, "closeService: ${it.message}") }
            runCatching { server.close() }
                .onFailure { PersistentLoggers.warn(TAG, "close: ${it.message}") }
            commandServer = null
            lastStatus = null
            PersistentLoggers.info(TAG, "runtime stopped")
        }
    }

    fun isRunning(): Boolean = commandServer != null

    fun getLastStatus(): StatusMessage? = lastStatus

    private class OzeroCommandServerHandler : CommandServerHandler {
        override fun serviceStop() {
            PersistentLoggers.debug(TAG, "serviceStop requested by libbox")
        }

        override fun serviceReload() {
            PersistentLoggers.debug(TAG, "serviceReload requested by libbox")
        }

        override fun getSystemProxyStatus(): SystemProxyStatus {
            val status = SystemProxyStatus()
            status.available = false
            status.enabled = false
            return status
        }

        override fun setSystemProxyEnabled(enabled: Boolean) {}

        override fun writeDebugMessage(message: String) {
            PersistentLoggers.trace(TAG, "debug: $message")
        }
    }

    private class OzeroPlatformInterface(
        context: Context,
        private val tunFd: Int,
        private val protector: SingboxProtectorBridge,
    ) : PlatformInterface {
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val defaultInterfaceMonitor = DefaultInterfaceMonitor(connectivity)

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun autoDetectInterfaceControl(fd: Int) {
            check(protector.protect(fd)) { "VpnService.protect($fd) failed" }
        }

        override fun openTun(options: TunOptions): Int {
            PersistentLoggers.debug(TAG, "openTun mtu=${options.mtu}")
            return tunFd
        }

        override fun useProcFS(): Boolean = false

        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String,
            sourcePort: Int,
            destinationAddress: String,
            destinationPort: Int,
        ): ConnectionOwner {
            val owner = ConnectionOwner()
            owner.userId = -1
            owner.userName = ""
            return owner
        }

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            defaultInterfaceMonitor.start(listener)
        }

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            defaultInterfaceMonitor.close(listener)
        }

        override fun getInterfaces(): NetworkInterfaceIterator = singboxNetworkInterfaces(connectivity)

        override fun underNetworkExtension(): Boolean = false

        override fun includeAllNetworks(): Boolean = false

        override fun readWIFIState(): WIFIState? = null

        override fun localDNSTransport(): LocalDNSTransport? = null

        override fun systemCertificates(): StringIterator = stringIterator(systemCertificatePem)

        override fun clearDNSCache() {}

        override fun sendNotification(notification: Notification) {}
    }

    private fun loadSystemCertificatePem(): List<String> = runCatching {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        TrustAnchorPemReader { bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
            .read(factory.trustManagers)
    }.onFailure {
        PersistentLoggers.warn(TAG, "systemCertificates load failed: ${it.message}")
    }.getOrDefault(emptyList())

    private fun stringIterator(values: List<String>): StringIterator =
        object : StringIterator {
            private var index = 0
            override fun hasNext(): Boolean = index < values.size
            override fun len(): Int = values.size
            override fun next(): String = values[index++]
        }
}
