package ru.ozero.singboxprocess

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.LogSanitizer
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginesingbox.SingboxRuntimeCheckpointStore
import ru.ozero.enginesingbox.singboxConfigFingerprint
import java.io.File
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.TrustManagerFactory

internal enum class SingboxRuntimeRole {
    VPN_TUN,
    VPN_PROXY,
    PROBE,
}

internal fun shouldIgnoreRuntimeStop(requestedOwnerId: Long?, activeOwnerId: Long?): Boolean =
    requestedOwnerId != null && activeOwnerId != requestedOwnerId

@Suppress("TooManyFunctions")
internal object SingboxRuntime {
    private const val TAG = "SingboxRuntime"
    private val mutex = Mutex()

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var platformInterface: OzeroPlatformInterface? = null

    @Volatile
    private var commandServerHandler: OzeroCommandServerHandler? = null

    @Volatile
    private var activeOwnerId: Long? = null

    @Volatile
    private var activeRuntimeRole: SingboxRuntimeRole? = null

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
        ownerId: Long,
        tunFd: Int,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
        detachedTunFd: DetachedTunFd? = null,
    ) =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                val oldServer = commandServer
                if (oldServer != null) {
                    PersistentLoggers.warn(
                        TAG,
                        "start: already running — graceful restart role=${activeRuntimeRole ?: "unknown"} " +
                            "owner=${activeOwnerId ?: "none"}",
                    )
                    val closeFailure = closeCommandServer(oldServer, closeService = true)
                    if (closeFailure != null) {
                        throw IllegalStateException("previous sing-box runtime teardown failed", closeFailure)
                    }
                    commandServer = null
                    activeOwnerId = null
                    activeRuntimeRole = null
                    releaseServerCallbacks()
                }
                val runtimeRole = if (tunFd == NO_TUN_FD) {
                    SingboxRuntimeRole.VPN_PROXY
                } else {
                    SingboxRuntimeRole.VPN_TUN
                }
                startLocked(
                    context,
                    ownerId,
                    tunFd,
                    singboxJsonConfig,
                    protectorBridge,
                    detachedTunFd,
                    runtimeRole,
                )
            }
        }

    suspend fun startIfIdle(
        context: Context,
        ownerId: Long,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            if (commandServer != null) return@withLock false
            startLocked(
                context,
                ownerId,
                NO_TUN_FD,
                singboxJsonConfig,
                protectorBridge,
                null,
                SingboxRuntimeRole.PROBE,
            )
            true
        }
    }

    private fun startLocked(
        context: Context,
        ownerId: Long,
        tunFd: Int,
        singboxJsonConfig: String,
        protectorBridge: SingboxProtectorBridge,
        detachedTunFd: DetachedTunFd?,
        runtimeRole: SingboxRuntimeRole,
    ) {
        check(detachedTunFd == null || detachedTunFd.fd == tunFd) {
            "detached TUN fd does not match runtime fd"
        }
        PersistentLoggers.debug(
            TAG,
            "start role=$runtimeRole owner=$ownerId configLen=${singboxJsonConfig.length} fd=$tunFd",
        )

        val socketFile = File(basePath, "command.sock")
        if (socketFile.exists()) {
            socketFile.delete()
            PersistentLoggers.debug(TAG, "cleaned stale command.sock")
        }

        val failureDiagnostics = NativeFailureDiagnostics()
        val platform = OzeroPlatformInterface(
            context.applicationContext,
            tunFd,
            protectorBridge,
            detachedTunFd,
            failureDiagnostics,
        )
        val handler = OzeroCommandServerHandler(
            ownerId = ownerId,
            runtimeRole = runtimeRole,
            failureDiagnostics = failureDiagnostics,
        )
        platformInterface = platform
        commandServerHandler = handler

        recordCheckpoint("pre-CommandServer")
        val server = createCommandServer(handler, platform)
        recordCheckpoint("post-CommandServer")
        try {
            server.start()
        } catch (e: Exception) {
            PersistentLoggers.error(TAG, "command server start failed exceptionClass=${e::class.java.simpleName}")
            cleanupFailedServerStart(server, ownerId, runtimeRole, e, closeService = false)
            throw e
        }
        recordCheckpoint("post-start socket-ready")

        try {
            server.checkConfig(singboxJsonConfig)
            recordCheckpoint("checkConfig-passed")
        } catch (e: Exception) {
            PersistentLoggers.error(
                TAG,
                "checkConfig failed exceptionClass=${e::class.java.simpleName} " +
                    "reason=${redactSingboxMessage(e.message.orEmpty())} " +
                    "fingerprint=${singboxJsonConfig.singboxConfigFingerprint()}",
            )
            cleanupFailedServerStart(server, ownerId, runtimeRole, e, closeService = false)
            throw e
        }

        try {
            // Go код дёргает options.AutoRedirect без nil-check → SIGABRT при null
            server.startOrReloadService(singboxJsonConfig, OverrideOptions())
            recordCheckpoint("post-startOrReloadService box-running")
        } catch (e: Exception) {
            PersistentLoggers.error(
                TAG,
                "startOrReloadService failed exceptionClass=${e::class.java.simpleName} " +
                    "reason=${redactSingboxMessage(e.message.orEmpty())}",
            )
            cleanupFailedServerStart(server, ownerId, runtimeRole, e, closeService = true)
            throw e
        }

        commandServer = server
        activeOwnerId = ownerId
        activeRuntimeRole = runtimeRole
        persistCheckpoint("runtime-started role=$runtimeRole owner=$ownerId fd=$tunFd")
        PersistentLoggers.info(TAG, "runtime started role=$runtimeRole owner=$ownerId fd=$tunFd")
    }

    suspend fun stop(ownerId: Long? = null) = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            if (shouldIgnoreRuntimeStop(ownerId, activeOwnerId)) {
                PersistentLoggers.debug(
                    TAG,
                    "stale stop ignored owner=$ownerId activeOwner=${activeOwnerId ?: "none"} " +
                        "role=${activeRuntimeRole ?: "unknown"}",
                )
                return@withLock
            }
            val stoppedRole = activeRuntimeRole
            val stoppedOwner = activeOwnerId
            val server = commandServer
            val closeFailure = server?.let { closeCommandServer(it, closeService = true) }
            if (closeFailure != null) {
                throw IllegalStateException("sing-box native teardown failed", closeFailure)
            }
            commandServer = null
            activeOwnerId = null
            activeRuntimeRole = null
            releaseServerCallbacks()
            persistCheckpoint("runtime-stopped role=${stoppedRole ?: "unknown"} owner=${stoppedOwner ?: "none"}")
            PersistentLoggers.info(
                TAG,
                "runtime stopped role=${stoppedRole ?: "unknown"} owner=${stoppedOwner ?: "none"}",
            )
        }
    }

    fun isRunning(): Boolean =
        commandServer != null && platformInterface != null && commandServerHandler != null

    private fun releaseServerCallbacks() {
        platformInterface?.closeTunFd()
        platformInterface = null
        commandServerHandler = null
    }

    private fun cleanupFailedServerStart(
        server: CommandServer,
        ownerId: Long,
        runtimeRole: SingboxRuntimeRole,
        startFailure: Exception,
        closeService: Boolean,
    ) {
        val closeFailure = closeCommandServer(server, closeService = closeService)
        if (closeFailure == null) {
            releaseServerCallbacks()
        } else {
            commandServer = server
            activeOwnerId = ownerId
            activeRuntimeRole = runtimeRole
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
        if (!closeService || failure == null) {
            runCatching { server.close() }
                .onFailure {
                    if (failure == null) failure = it else failure?.addSuppressed(it)
                    PersistentLoggers.warn(TAG, "close failed exceptionClass=${it::class.java.simpleName}")
                }
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

    private class OzeroCommandServerHandler(
        private val ownerId: Long,
        private val runtimeRole: SingboxRuntimeRole,
        private val failureDiagnostics: NativeFailureDiagnostics,
    ) : CommandServerHandler {
        override fun serviceStop() {
            PersistentLoggers.debug(TAG, "serviceStop requested by libbox role=$runtimeRole owner=$ownerId")
        }

        override fun serviceReload() {
            PersistentLoggers.debug(TAG, "serviceReload requested by libbox role=$runtimeRole owner=$ownerId")
        }

        override fun getSystemProxyStatus(): SystemProxyStatus {
            val status = SystemProxyStatus()
            status.available = false
            status.enabled = false
            return status
        }

        override fun setSystemProxyEnabled(enabled: Boolean) {}

        override fun writeDebugMessage(message: String) {
            failureDiagnostics.recordNative(message)
            val safe = redactSingboxMessage(message)
            if (safe.shouldPromoteSingboxMessage()) {
                PersistentLoggers.warn(TAG, "libbox role=$runtimeRole owner=$ownerId: $safe")
            } else {
                PersistentLoggers.trace(TAG, "libbox role=$runtimeRole owner=$ownerId: $safe")
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

internal fun requireConnectivityManager(context: Context): ConnectivityManager =
    checkNotNull(
        context.getSystemService(ConnectivityManager::class.java),
    ) {
        "ConnectivityManager unavailable in :engine_singbox process"
    }

internal fun redactSingboxMessage(message: String): String =
    LogSanitizer.sanitize(redactJsonFragments(message))

private fun redactJsonFragments(message: String): String = buildString(message.length) {
    var cursor = 0
    while (cursor < message.length) {
        val start = message.indexOf('{', cursor)
        if (start < 0) {
            append(message, cursor, message.length)
            break
        }
        append(message, cursor, start)
        val end = message.balancedJsonEnd(start)
        if (end == null) {
            append(message, start, message.length)
            break
        }
        append("<redacted-json>")
        cursor = end
    }
}

private fun String.balancedJsonEnd(start: Int): Int? {
    var depth = 0
    var insideString = false
    var escaped = false
    for (index in start until length) {
        val character = this[index]
        if (insideString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> insideString = false
            }
            continue
        }
        when (character) {
            '"' -> insideString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return index + 1
            }
        }
    }
    return null
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
