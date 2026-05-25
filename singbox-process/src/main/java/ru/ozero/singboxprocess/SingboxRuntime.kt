package ru.ozero.singboxprocess

import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.InterfaceUpdateListener
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
import io.nekohasekai.libbox.ConnectionOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers

internal object SingboxRuntime {
    private const val TAG = "SingboxRuntime"

    private val mutex = Mutex()

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var lastStatus: StatusMessage? = null

    @Volatile
    private var setupDone = false

    fun setup(basePath: String) {
        if (setupDone) return
        val options = SetupOptions()
        options.basePath = basePath
        options.workingPath = basePath
        options.tempPath = "$basePath/tmp"
        Libbox.setup(options)
        setupDone = true
        PersistentLoggers.info(TAG, "libbox setup basePath=$basePath")
    }

    suspend fun start(
        tunFd: Int,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
    ) = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            check(commandServer == null) { "SingboxRuntime already running" }

            val platform = OzeroPlatformInterface(tunFd, protectorBridge)
            val handler = OzeroCommandServerHandler()

            val server = CommandServer(handler, platform)
            server.start()

            try {
                server.startOrReloadService(singboxJsonConfig, null)
            } catch (e: Exception) {
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
            PersistentLoggers.info(TAG, "serviceStop requested by libbox")
        }

        override fun serviceReload() {
            PersistentLoggers.info(TAG, "serviceReload requested by libbox")
        }

        override fun getSystemProxyStatus(): SystemProxyStatus {
            val status = SystemProxyStatus()
            status.available = false
            status.enabled = false
            return status
        }

        override fun setSystemProxyEnabled(enabled: Boolean) {}

        override fun writeDebugMessage(message: String) {
            PersistentLoggers.info(TAG, "debug: $message")
        }
    }

    private class OzeroPlatformInterface(
        private val tunFd: Int,
        private val protector: SingboxProtectorBridge,
    ) : PlatformInterface {

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun autoDetectInterfaceControl(fd: Int) {
            protector.protect(fd)
        }

        override fun openTun(options: TunOptions): Int {
            PersistentLoggers.info(TAG, "openTun mtu=${options.mtu}")
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

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

        override fun getInterfaces(): NetworkInterfaceIterator =
            object : NetworkInterfaceIterator {
                override fun hasNext(): Boolean = false
                override fun next(): io.nekohasekai.libbox.NetworkInterface =
                    error("empty iterator")
            }

        override fun underNetworkExtension(): Boolean = false

        override fun includeAllNetworks(): Boolean = false

        override fun readWIFIState(): WIFIState? = null

        override fun localDNSTransport(): LocalDNSTransport? = null

        override fun systemCertificates(): StringIterator =
            object : StringIterator {
                override fun hasNext(): Boolean = false
                override fun len(): Int = 0
                override fun next(): String = error("empty iterator")
            }

        override fun clearDNSCache() {}

        override fun sendNotification(notification: Notification) {}
    }
}
