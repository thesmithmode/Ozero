package ru.ozero.singboxprocess

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OverrideOptions
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.TrustManagerFactory

internal object SingboxRuntime {
    private const val TAG = "SingboxRuntime"
    private const val MAX_NATIVE_LOG_BATCH = 100
    private const val MAX_NATIVE_LOG_CATEGORIES = 16
    private const val STATUS_INTERVAL_NANOS = 1_000_000_000L
    private val mutex = Mutex()

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var logClient: CommandClient? = null

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
                    stopNativeLogSubscription()
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
                    startNativeLogSubscription()
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
            stopNativeLogSubscription()
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

    private suspend fun startNativeLogSubscription() {
        withContext(Dispatchers.IO) {
            stopNativeLogSubscription()
            val options = CommandClientOptions()
            options.addCommand(Libbox.CommandLog)
            options.addCommand(Libbox.CommandStatus)
            options.statusInterval = STATUS_INTERVAL_NANOS
            val client = CommandClient(NativeLogHandler(), options)
            runCatching { client.connect() }
                .onSuccess {
                    logClient = client
                    PersistentLoggers.debug(TAG, "native log subscription connected")
                }
                .onFailure {
                    runCatching { client.disconnect() }
                    PersistentLoggers.warn(TAG, "native log subscription failed")
                }
        }
    }

    private suspend fun stopNativeLogSubscription() {
        withContext(Dispatchers.IO) {
            val client = logClient ?: return@withContext
            logClient = null
            runCatching { client.disconnect() }
                .onFailure { PersistentLoggers.warn(TAG, "native log subscription stop failed") }
        }
    }

    private class NativeLogHandler : CommandClientHandler {
        private val emittedCategories = mutableSetOf<String>()

        override fun connected() {}

        override fun disconnected(message: String?) {}

        override fun setDefaultLogLevel(level: Int) {}

        override fun clearLogs() {}

        override fun writeLogs(messageList: LogIterator?) {
            if (messageList == null) return
            var inspected = 0
            while (messageList.hasNext() && inspected < MAX_NATIVE_LOG_BATCH) {
                val entry = messageList.next()
                inspected++
                val category = nativeLogCategory(entry.message) ?: continue
                val shouldEmit = synchronized(emittedCategories) {
                    emittedCategories.size < MAX_NATIVE_LOG_CATEGORIES && emittedCategories.add(category)
                }
                if (shouldEmit) PersistentLoggers.warn(TAG, "native outbound category=$category")
            }
        }

        override fun writeStatus(message: StatusMessage) {
            lastStatus = message
        }

        override fun writeGroups(message: OutboundGroupIterator?) {}

        override fun writeOutbounds(message: OutboundGroupItemIterator?) {}

        override fun initializeClashMode(modeList: StringIterator, currentMode: String) {}

        override fun updateClashMode(newMode: String) {}

        override fun writeConnectionEvents(events: ConnectionEvents?) {}
    }

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
            val safe = redactSingboxMessage(message)
            if (safe.shouldPromoteSingboxMessage()) {
                PersistentLoggers.warn(TAG, "libbox: $safe")
            } else {
                PersistentLoggers.trace(TAG, "libbox: $safe")
            }
        }
    }

    private class OzeroPlatformInterface(
        context: Context,
        private val tunFd: Int,
        private val protector: SingboxProtectorBridge,
    ) : PlatformInterface {
        private val connectivity: ConnectivityManager = requireConnectivityManager(context)
        private val defaultInterfaceMonitor = DefaultInterfaceMonitor(connectivity)
        private val localDnsTransport = AndroidLocalDnsTransport(defaultInterfaceMonitor)
        private val protectFailureLogged = AtomicBoolean(false)

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun autoDetectInterfaceControl(fd: Int) {
            val protected = protector.protect(fd)
            if (!protected && protectFailureLogged.compareAndSet(false, true)) {
                PersistentLoggers.warn(TAG, "active VPN protect failed")
            }
            check(protected) { "active VPN protect failed" }
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

        override fun localDNSTransport(): LocalDNSTransport = localDnsTransport

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

internal fun requireConnectivityManager(context: Context): ConnectivityManager =
    checkNotNull(
        context.getSystemService(ConnectivityManager::class.java),
    ) {
        "ConnectivityManager unavailable in :engine_singbox process"
    }

internal fun redactSingboxMessage(message: String): String {
    val noJson = message.replace(Regex("\\{.*}"), "<redacted-json>")
    val noSubscriptionQuery = noJson.replace(Regex("(https?://[^\\s?#]+)\\?[^\\s]+"), "$1?<redacted-query>")
    return noSubscriptionQuery
        .replace(Regex("(?i)(password[\\\"'=:\\s]+)([^\\\",&\\s}]+)"), "$1<redacted>")
        .replace(Regex("(?i)((?:private_key|public_key)[\\\"'=:\\s]+)([^\\\",&\\s}]+)"), "$1<redacted>")
        .replace(Regex("(?i)((?:serverAddress|server_address|server)[\\\"'=:\\s]+)([^\\\",&\\s}]+)"), "$1<redacted>")
        .replace(
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            "<redacted-uuid>",
        )
}

internal fun String.shouldPromoteSingboxMessage(): Boolean =
    contains("error", ignoreCase = true) ||
        contains("failed", ignoreCase = true) ||
        contains("tls", ignoreCase = true) ||
        contains("reality", ignoreCase = true) ||
        contains("certificate", ignoreCase = true) ||
        contains("handshake", ignoreCase = true) ||
        contains("dns", ignoreCase = true) ||
        contains("dial", ignoreCase = true) ||
        contains("connection closed", ignoreCase = true)

internal fun nativeLogCategory(message: String): String? {
    val normalized = message.lowercase()
    val failure = listOf(
        "fail",
        "error",
        "refused",
        "timeout",
        "timed out",
        "unavailable",
        "no route",
        "eof",
        "reset",
        "closed",
        "broken pipe",
        "rejected",
    ).any(normalized::contains)
    if (!failure) return null
    return when {
        "protect" in normalized -> "protect"
        "certificate" in normalized -> "tls-certificate"
        "reality" in normalized && "handshake" in normalized -> "reality-handshake"
        "tls" in normalized && "handshake" in normalized -> "tls-handshake"
        "default interface" in normalized -> "default-interface"
        "dns" in normalized || "resolve" in normalized -> "dns"
        "no route" in normalized || "route" in normalized -> "route"
        "eof" in normalized || "reset" in normalized || "closed" in normalized || "broken pipe" in normalized ->
            "remote-closed"
        "connect" in normalized || "refused" in normalized -> "connect"
        "dial" in normalized -> "dial"
        "network" in normalized && ("unavailable" in normalized || "down" in normalized) ->
            "network-unavailable"
        else -> null
    }
}
