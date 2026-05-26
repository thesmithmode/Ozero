package ru.ozero.singboxprocess

import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.OverrideOptions
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
    private val UUID_REDACT_REGEX = Regex(""""uuid":"[^"]*"""")

    private val mutex = Mutex()

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var lastStatus: StatusMessage? = null

    @Volatile
    private var setupDone = false

    @Volatile
    private var basePath: String = ""

    fun setup(basePath: String) {
        if (setupDone) return
        this.basePath = basePath
        val options = SetupOptions()
        options.basePath = basePath
        options.workingPath = basePath
        options.tempPath = "$basePath/tmp"
        Libbox.setup(options)
        setupDone = true
        PersistentLoggers.info(TAG, "libbox setup basePath=$basePath")
    }

    suspend fun start(tunFd: Int, singboxJsonConfig: String, protectorBridge: SingboxProtectorBridge) =
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

                // uuid редактируется из логов — репо публичный, subscription UUID = личные данные
                val configPreview = singboxJsonConfig.take(200).replace(UUID_REDACT_REGEX, """"uuid":"***"""")
                PersistentLoggers.info(
                    TAG,
                    "start configLen=${singboxJsonConfig.length} fd=$tunFd" +
                        " configPreview=$configPreview",
                )

                val socketFile = java.io.File(basePath, "command.sock")
                if (socketFile.exists()) {
                    socketFile.delete()
                    PersistentLoggers.info(TAG, "cleaned stale command.sock")
                }

                val platform = OzeroPlatformInterface(tunFd, protectorBridge)
                val handler = OzeroCommandServerHandler()

                PersistentLoggers.info(TAG, "checkpoint: pre-CommandServer")
                val server = CommandServer(handler, platform)
                PersistentLoggers.info(TAG, "checkpoint: post-CommandServer")
                server.start()
                PersistentLoggers.info(TAG, "checkpoint: post-start (socket ready)")

                try {
                    server.checkConfig(singboxJsonConfig)
                    PersistentLoggers.info(TAG, "checkpoint: checkConfig passed")
                } catch (e: Exception) {
                    PersistentLoggers.error(TAG, "checkConfig failed: ${e.message}")
                    server.close()
                    throw e
                }

                try {
                    // Go код дёргает options.AutoRedirect без nil-check → SIGABRT при null
                    server.startOrReloadService(singboxJsonConfig, OverrideOptions())
                    PersistentLoggers.info(TAG, "checkpoint: post-startOrReloadService (box running)")
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
