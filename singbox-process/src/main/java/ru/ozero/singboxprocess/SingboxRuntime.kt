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
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginesingbox.SingboxRuntimeCheckpointStore
import java.io.File
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.TrustManagerFactory

@Suppress("TooManyFunctions")
internal object SingboxRuntime {
    private const val TAG = "SingboxRuntime"
    private const val MAX_NATIVE_LOG_BATCH = 100
    private const val STATUS_INTERVAL_NANOS = 1_000_000_000L
    private const val NATIVE_LOG_CONNECT_TIMEOUT_MS = 2_500L
    private val mutex = Mutex()
    private val nativeLogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var platformInterface: OzeroPlatformInterface? = null

    @Volatile
    private var commandServerHandler: OzeroCommandServerHandler? = null

    @Volatile
    private var nativeLogConnection: NativeLogConnection? = null

    private val retainedFailedLogConnections = ConcurrentHashMap.newKeySet<NativeLogConnection>()

    @Volatile
    private var nativeLogJob: Job? = null

    @Volatile
    private var nativeLogSessionActive = false

    private val nativeLogSessions = NativeDiagnosticsSessionGuard()

    @Volatile
    private var nativeFailureDiagnostics: NativeFailureDiagnostics? = null

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
        detachedTunFd: DetachedTunFd? = null,
    ) =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                val oldServer = commandServer
                if (oldServer != null) {
                    PersistentLoggers.warn(TAG, "start: already running — graceful restart")
                    val diagnosticsStopped = stopNativeLogSubscription()
                    val closeFailure = closeCommandServer(oldServer, closeService = true)
                    if (!diagnosticsStopped || closeFailure != null) {
                        throw IllegalStateException("previous sing-box runtime teardown failed", closeFailure)
                    }
                    commandServer = null
                    releaseServerCallbacks()
                    retainedFailedLogConnections.clear()
                    lastStatus = null
                }
                startLocked(context, tunFd, singboxJsonConfig, protectorBridge, detachedTunFd)
            }
        }

    suspend fun startIfIdle(
        context: Context,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            if (commandServer != null) return@withLock false
            startLocked(context, NO_TUN_FD, singboxJsonConfig, protectorBridge, null)
            true
        }
    }

    private fun startLocked(
        context: Context,
        tunFd: Int,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
        detachedTunFd: DetachedTunFd?,
    ) {
        check(detachedTunFd == null || detachedTunFd.fd == tunFd) {
            "detached TUN fd does not match runtime fd"
        }
        PersistentLoggers.debug(TAG, "start configLen=${singboxJsonConfig.length} fd=$tunFd")

        val socketFile = File(basePath, "command.sock")
        if (socketFile.exists()) {
            socketFile.delete()
            PersistentLoggers.debug(TAG, "cleaned stale command.sock")
        }

        val failureDiagnostics = NativeFailureDiagnostics()
        nativeFailureDiagnostics = failureDiagnostics
        val platform = OzeroPlatformInterface(
            context.applicationContext,
            tunFd,
            protectorBridge,
            detachedTunFd,
            failureDiagnostics,
        )
        val handler = OzeroCommandServerHandler()
        platformInterface = platform
        commandServerHandler = handler

        recordCheckpoint("pre-CommandServer")
        val server = createCommandServer(handler, platform)
        recordCheckpoint("post-CommandServer")
        try {
            server.start()
        } catch (e: Exception) {
            PersistentLoggers.error(TAG, "command server start failed exceptionClass=${e::class.java.simpleName}")
            cleanupFailedServerStart(server, e)
            throw e
        }
        recordCheckpoint("post-start socket-ready")

        try {
            server.checkConfig(singboxJsonConfig)
            recordCheckpoint("checkConfig-passed")
        } catch (e: Exception) {
            PersistentLoggers.error(TAG, "checkConfig failed exceptionClass=${e::class.java.simpleName}")
            cleanupFailedServerStart(server, e)
            throw e
        }

        try {
            // Go код дёргает options.AutoRedirect без nil-check → SIGABRT при null
            server.startOrReloadService(singboxJsonConfig, OverrideOptions())
            recordCheckpoint("post-startOrReloadService box-running")
        } catch (e: Exception) {
            PersistentLoggers.error(
                TAG,
                "startOrReloadService failed exceptionClass=${e::class.java.simpleName}",
            )
            cleanupFailedServerStart(server, e)
            throw e
        }

        commandServer = server
        persistCheckpoint("runtime-started fd=$tunFd")
        PersistentLoggers.info(TAG, "runtime started fd=$tunFd")
    }

    suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            val diagnosticsStopped = stopNativeLogSubscription()
            val server = commandServer
            val closeFailure = server?.let { closeCommandServer(it, closeService = true) }
            if (!diagnosticsStopped || closeFailure != null) {
                throw IllegalStateException("sing-box native teardown failed", closeFailure)
            }
            commandServer = null
            releaseServerCallbacks()
            retainedFailedLogConnections.clear()
            lastStatus = null
            nativeFailureDiagnostics = null
            persistCheckpoint("runtime-stopped")
            PersistentLoggers.info(TAG, "runtime stopped")
        }
    }

    fun isRunning(): Boolean =
        commandServer != null && platformInterface != null && commandServerHandler != null

    fun getLastStatus(): StatusMessage? = lastStatus

    private fun releaseServerCallbacks() {
        platformInterface?.closeTunFd()
        platformInterface = null
        commandServerHandler = null
    }

    private fun cleanupFailedServerStart(server: CommandServer, startFailure: Exception) {
        val closeFailure = closeCommandServer(server, closeService = false)
        if (closeFailure == null) {
            releaseServerCallbacks()
        } else {
            commandServer = server
            startFailure.addSuppressed(closeFailure)
        }
    }

    private fun closeCommandServer(server: CommandServer, closeService: Boolean): Throwable? {
        var failure: Throwable? = null
        if (closeService) {
            runCatching { server.closeService() }
                .onFailure {
                    failure = it
                    PersistentLoggers.warn(TAG, "closeService failed exceptionClass=${it::class.java.simpleName}")
                }
        }
        runCatching { server.close() }
            .onFailure {
                if (failure == null) failure = it else failure?.addSuppressed(it)
                PersistentLoggers.warn(TAG, "close failed exceptionClass=${it::class.java.simpleName}")
            }
        return failure
    }

    private fun createCommandServer(
        handler: OzeroCommandServerHandler,
        platform: OzeroPlatformInterface,
    ): CommandServer = try {
        CommandServer(handler, platform)
    } catch (e: Exception) {
        PersistentLoggers.error(TAG, "command server creation failed exceptionClass=${e::class.java.simpleName}")
        releaseServerCallbacks()
        throw e
    }

    // Keep detached from runtime startup: gomobile CommandClient callbacks can abort the isolated process.
    private fun launchNativeLogSubscription(
        failureDiagnostics: NativeFailureDiagnostics = checkNotNull(nativeFailureDiagnostics),
        reconnect: Boolean = false,
    ) {
        val generation = if (reconnect) nativeLogSessions.activeGeneration() else nativeLogSessions.begin()
        if (!reconnect) {
            nativeLogSessionActive = true
        }
        nativeLogJob = nativeLogScope.launch {
            connectNativeLogSubscription(generation, failureDiagnostics)
        }
    }

    private suspend fun connectNativeLogSubscription(
        generation: Long,
        failureDiagnostics: NativeFailureDiagnostics,
    ) {
        var expectedConnection: NativeLogConnection? = null
        runCatching {
            val options = CommandClientOptions()
            options.addCommand(Libbox.CommandLog)
            options.addCommand(Libbox.CommandStatus)
            options.statusInterval = STATUS_INTERVAL_NANOS
            lateinit var client: CommandClient
            val handler = NativeLogHandler(
                failureDiagnostics = failureDiagnostics,
                isCurrent = { nativeLogSessions.isCurrent(generation, client, nativeLogConnection?.client) },
                onDisconnected = { handleNativeLogDisconnected(generation, client) },
            )
            client = CommandClient(
                handler,
                options,
            )
            val connection = NativeLogConnection(client, handler)
            expectedConnection = connection
            if (!nativeLogSessions.isActive(generation)) {
                disconnectNativeLogClient(connection)
                return@runCatching
            }
            nativeLogConnection = connection
            withTimeout(NATIVE_LOG_CONNECT_TIMEOUT_MS) {
                runInterruptible { client.connect() }
            }
            if (!nativeLogSessions.isCurrent(generation, client, nativeLogConnection?.client)) {
                disconnectNativeLogClient(connection)
                return
            }
            PersistentLoggers.debug(TAG, "native log subscription connected")
        }.onFailure {
            expectedConnection?.let(::disconnectNativeLogClient)
            if (nativeLogSessions.isActive(generation) && nativeLogSessionActive) {
                PersistentLoggers.warn(
                    TAG,
                    "native diagnostics unavailable exceptionClass=${it::class.java.simpleName}",
                )
            }
        }
    }

    private suspend fun stopNativeLogSubscription(): Boolean {
        nativeLogSessions.invalidate()
        nativeLogSessionActive = false
        nativeLogJob?.cancelAndJoin()
        nativeLogJob = null
        val connection = nativeLogConnection
        val disconnected = connection == null || withContext(Dispatchers.IO) { disconnectNativeLogClient(connection) }
        lastStatus = null
        return disconnected && retainedFailedLogConnections.isEmpty()
    }

    private fun disconnectNativeLogClient(connection: NativeLogConnection): Boolean {
        val disconnected = runCatching { connection.client.disconnect() }
            .onFailure { failure ->
                retainedFailedLogConnections.add(connection)
                PersistentLoggers.warn(
                    TAG,
                    "native diagnostics disconnect failed exceptionClass=${failure::class.java.simpleName}",
                )
            }
            .isSuccess
        synchronized(connection.handler) { Unit }
        if (disconnected) {
            if (nativeLogConnection === connection) nativeLogConnection = null
            retainedFailedLogConnections.remove(connection)
        }
        return disconnected
    }

    private fun handleNativeLogDisconnected(generation: Long, client: CommandClient) {
        if (!nativeLogSessions.isCurrent(generation, client, nativeLogConnection?.client)) return
        lastStatus = null
        nativeLogConnection = null
        if (nativeLogSessionActive && nativeLogSessions.claimReconnect(generation)) {
            launchNativeLogSubscription(reconnect = true)
        }
    }

    private class NativeLogConnection(
        val client: CommandClient,
        val handler: NativeLogHandler,
    )

    private class NativeLogHandler(
        private val failureDiagnostics: NativeFailureDiagnostics,
        private val isCurrent: () -> Boolean,
        private val onDisconnected: () -> Unit,
    ) : CommandClientHandler {
        override fun connected() {}

        override fun disconnected(message: String?) {
            onDisconnected()
        }

        override fun setDefaultLogLevel(level: Int) {}

        override fun clearLogs() {}

        override fun writeLogs(messageList: LogIterator?) {
            if (messageList == null || !isCurrent()) return
            var inspected = 0
            while (messageList.hasNext() && inspected < MAX_NATIVE_LOG_BATCH) {
                if (!isCurrent()) return
                val entry = messageList.next()
                inspected++
                failureDiagnostics.recordNative(entry.message)
                persistCheckpoint("native ${redactSingboxMessage(entry.message)}")
            }
        }

        override fun writeStatus(message: StatusMessage) {
            if (isCurrent()) lastStatus = message
        }

        override fun writeGroups(message: OutboundGroupIterator?) {}

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
        private val detachedTunFd: DetachedTunFd?,
        private val failureDiagnostics: NativeFailureDiagnostics,
    ) : PlatformInterface {
        private val connectivity: ConnectivityManager = requireConnectivityManager(context)
        private val defaultInterfaceMonitor = DefaultInterfaceMonitor(connectivity)
        private val localDnsTransport = AndroidLocalDnsTransport(defaultInterfaceMonitor)
        private val protectFailureLogged = AtomicBoolean(false)

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun autoDetectInterfaceControl(fd: Int) {
            val protected = protector.protect(fd)
            if (!protected && protectFailureLogged.compareAndSet(false, true)) {
                failureDiagnostics.record(NativeFailureCategory.PROTECT_FAILED, "platform", "active VPN protect failed")
                PersistentLoggers.warn(TAG, "active VPN protect failed")
            }
            check(protected) { "active VPN protect failed" }
        }

        override fun openTun(options: TunOptions): Int {
            PersistentLoggers.debug(TAG, "openTun mtu=${options.mtu}")
            val providedFd = detachedTunFd?.provideToLibbox()
            return providedFd ?: tunFd
        }

        fun closeTunFd() {
            detachedTunFd?.closeOwnedByHost()
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

    private fun recordCheckpoint(message: String) {
        persistCheckpoint(message)
        PersistentLoggers.debug(TAG, "checkpoint: $message")
    }

    private fun persistCheckpoint(message: String) {
        if (basePath.isNotBlank()) SingboxRuntimeCheckpointStore.record(File(basePath), message)
    }

    private const val NO_TUN_FD = -1
}

internal class NativeDiagnosticsSessionGuard {
    private var generation = 0L
    private var reconnectClaimed = false

    @Synchronized
    fun begin(): Long {
        generation += 1
        reconnectClaimed = false
        return generation
    }

    @Synchronized
    fun invalidate() {
        generation += 1
        reconnectClaimed = true
    }

    @Synchronized
    fun activeGeneration(): Long = generation

    @Synchronized
    fun isActive(expectedGeneration: Long): Boolean = expectedGeneration == generation

    @Synchronized
    fun isCurrent(expectedGeneration: Long, expectedClient: Any, currentClient: Any?): Boolean =
        expectedGeneration == generation && expectedClient === currentClient

    @Synchronized
    fun claimReconnect(expectedGeneration: Long): Boolean {
        if (expectedGeneration != generation || reconnectClaimed) return false
        reconnectClaimed = true
        return true
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
    val noHeaders = noJson
        .replace(Regex("(?i)\\bauthorization\\s*:\\s*[^\\r\\n]+"), "authorization: <redacted>")
        .replace(Regex("(?i)\\bcookie\\s*:\\s*[^\\r\\n]+"), "cookie: <redacted>")
        .replace(Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer <redacted>")
        .replace(Regex("(?i)\\bbasic\\s+[A-Za-z0-9+/=]+"), "Basic <redacted>")
    val noUrlSecrets = noHeaders
        .replace(
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            "<redacted-uuid>",
        )
        .replace(Regex("(?i)(https?://)([^/@\\s]+)@"), "$1<redacted>@")
        .replace(Regex("(https?://[^\\s?#]+)[?#][^\\s]+"), "$1?<redacted>")
    return noUrlSecrets
        .replace(
            Regex(
                "(?i)\\b(password|username|token|authorization|cookie|private_key|public_key|" +
                    "short_id|serverAddress|server_address|server_name|server|host|sni|headers)" +
                    "([\\\"'=:\\s]+)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\\",&;\\s}]+)",
            ),
            "$1$2<redacted>",
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

internal enum class NativeFailureCategory(val legacyName: String, val priority: Int) {
    PROTECT_FAILED("protect", 0),
    DEFAULT_INTERFACE("default-interface", 1),
    DNS("dns", 2),
    REALITY_HANDSHAKE("reality-handshake", 3),
    TLS("tls-handshake", 4),
    CONNECT("connect", 5),
    REMOTE_CLOSED("remote-closed", 6),
    TIMEOUT("timeout", 7),
}

internal class NativeFailureDiagnostics(
    private val emit: (String) -> Unit = { message -> PersistentLoggers.warn("SingboxRuntime", message) },
) {
    private val sessionId = java.util.UUID.randomUUID().toString().substringBefore('-')
    private val recorded = mutableSetOf<Pair<NativeFailureCategory, String>>()
    private var dominant: NativeFailureCategory? = null

    fun recordNative(message: String) {
        val category = nativeFailureCategory(message) ?: return
        record(category, nativeOutboundTag(message), redactSingboxMessage(message).take(MAX_NATIVE_MESSAGE_LENGTH))
    }

    @Synchronized
    fun record(category: NativeFailureCategory, outboundTag: String, message: String) {
        if (dominant == null || category.priority < requireNotNull(dominant).priority) {
            dominant = category
        }
        val key = category to outboundTag
        if (recorded.size >= MAX_RECORDS && key !in recorded) return
        if (!recorded.add(key)) return
        emit(
            "native failure session=$sessionId outbound=$outboundTag category=${category.name} " +
                "dominant=${requireNotNull(dominant).name} message=${message.take(MAX_NATIVE_MESSAGE_LENGTH)}",
        )
    }

    private fun nativeOutboundTag(message: String): String =
        Regex("(?i)\\boutbound(?:=|\\s+)([a-z0-9_.-]{1,48})")
            .find(message)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            ?: "unknown"

    private companion object {
        const val MAX_NATIVE_MESSAGE_LENGTH = 320
        const val MAX_RECORDS = 16
    }
}

internal fun nativeLogCategory(message: String): String? {
    val normalized = message.lowercase()
    return when {
        "reality" in normalized &&
            "handshake" in normalized -> "reality-handshake"
        "certificate" in normalized ||
            "tls handshake" in normalized -> "tls-certificate"
        "eof" in normalized ||
            "connection closed" in normalized ||
            "reset" in normalized ||
            "broken pipe" in normalized -> "remote-closed"
        "connect" in normalized ||
            "refused" in normalized -> "connect"
        "dial" in normalized -> "dial"
        "route" in normalized -> "route"
        "network unavailable" in normalized -> "network-unavailable"
        else -> null
    }
}

internal fun nativeFailureCategory(message: String): NativeFailureCategory? {
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
        "protect" in normalized -> NativeFailureCategory.PROTECT_FAILED
        "default interface" in normalized -> NativeFailureCategory.DEFAULT_INTERFACE
        "dns" in normalized ||
            "resolve" in normalized -> NativeFailureCategory.DNS
        "reality" in normalized &&
            "handshake" in normalized -> NativeFailureCategory.REALITY_HANDSHAKE
        "certificate" in normalized ||
            "tls" in normalized &&
            "handshake" in normalized -> NativeFailureCategory.TLS
        "timeout" in normalized ||
            "timed out" in normalized -> NativeFailureCategory.TIMEOUT
        "eof" in normalized ||
            "reset" in normalized ||
            "closed" in normalized ||
            "broken pipe" in normalized ->
            NativeFailureCategory.REMOTE_CLOSED
        "connect" in normalized ||
            "refused" in normalized ||
            "dial" in normalized ||
            "no route" in normalized ||
            "route" in normalized -> NativeFailureCategory.CONNECT
        else -> null
    }
}
