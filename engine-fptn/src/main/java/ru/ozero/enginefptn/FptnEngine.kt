package ru.ozero.enginefptn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress

class FptnEngine(
    private val configStore: FptnConfigStore,
    private val onEngineFailed: (String) -> Unit = {},
) : EnginePlugin, TunFdAcceptor {

    private val wsClient = FptnNativeWebSocket()
    private val httpsClient = FptnNativeHttpsClient()

    private val _stats = MutableStateFlow(EngineStats())
    private var tunScope: CoroutineScope? = null
    private var _nativeHandle: Long = 0L
    private var _currentServer: FptnServer? = null
    private var _currentServerIp: String? = null
    private var _accessToken: String? = null
    private var _bypassMethod: String = FptnBypassMethod.DEFAULT.strategyName
    private var _sniDomain: String = FptnConfig.DEFAULT_SNI_DOMAIN
    private var _pfd: ParcelFileDescriptor? = null

    override val id = EngineId.FPTN

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = false,
        providesLocalSocks = false,
    )

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy =
        _currentServer?.name?.takeIf { it.isNotBlank() }
            ?.let { ExitNodeStrategy.ProviderLabel(it) }
            ?: ExitNodeStrategy.Unavailable("FPTN not connected")

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? {
        val cfg = configStore.currentConfig()
        if (cfg.token.isBlank()) return null
        return EngineConfig.Fptn(
            token = cfg.token,
            selectedServerName = cfg.selectedServerName,
            bypassMethod = cfg.bypassMethod,
            sniDomain = cfg.sniDomain,
            autoSelect = cfg.autoSelect,
            reconnectOnNetworkChange = cfg.reconnectOnNetworkChange,
            reconnectOnIpChange = cfg.reconnectOnIpChange,
            maxReconnectAttempts = cfg.maxReconnectAttempts,
            reconnectPauseSeconds = cfg.reconnectPauseSeconds,
            resetServerOnDisconnect = cfg.resetServerOnDisconnect,
        )
    }

    override suspend fun tunSpec(): TunSpec = TunSpec(
        sessionName = "FPTN",
        mtu = 1500,
        blocking = true,
        ipv4Address = "10.10.0.1",
        ipv4PrefixLength = 32,
        dnsServers = listOf("1.1.1.1", "8.8.8.8"),
        allowFamilyV4 = true,
        allowFamilyV6 = true,
        ipv6Address = "fd00::1",
        ipv6PrefixLength = 128,
        routeAllV4 = true,
        routeAllV6 = false,
    )

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        val fptn = config as? EngineConfig.Fptn
            ?: return StartResult.Failure("Expected EngineConfig.Fptn")

        if (fptn.token.isBlank()) {
            return StartResult.Failure("FPTN token not configured")
        }

        val tokenData = FptnToken.parse(fptn.token)
            ?: run {
                PersistentLoggers.error(TAG, "start: token parse failed (invalid/expired token format)")
                return StartResult.Failure("Invalid FPTN token")
            }

        val candidates = selectServerCandidates(fptn, tokenData)
        if (candidates.isEmpty()) {
            PersistentLoggers.error(
                TAG,
                "start: no server available - token contained ${tokenData.servers.size} server(s), " +
                    "selected=${fptn.selectedServerName ?: "<auto>"}",
            )
            return StartResult.Failure("No FPTN server available")
        }

        val firstServer = candidates.first()
        PersistentLoggers.debug(
            TAG,
            "start: server=${firstServer.name} port=${firstServer.port} bypass=${fptn.bypassMethod} " +
                "sniDomain=${fptn.sniDomain} tokenServers=${tokenData.servers.size} " +
                "candidates=${candidates.size} autoSelect=${fptn.autoSelect}",
        )
        Log.d(
            TAG,
            "start server=${firstServer.name}:${firstServer.port} bypass=${fptn.bypassMethod} " +
                "n=${tokenData.servers.size} candidates=${candidates.size}",
        )

        FptnNativeWebSocket.loadOnce()
        if (!FptnNativeWebSocket.libraryLoaded) {
            PersistentLoggers.error(TAG, "native lib load failed: ${FptnNativeWebSocket.loadError}")
            return StartResult.Failure(
                "fptn_native_lib not loaded: ${FptnNativeWebSocket.loadError}"
            )
        }

        val authResult = withContext(Dispatchers.IO) {
            withTimeoutOrNull(STARTUP_AUTH_BUDGET_MS) {
                authenticateFirstAvailable(
                    candidates = candidates,
                    data = tokenData,
                    bypassMethod = fptn.bypassMethod,
                    sniDomain = fptn.sniDomain,
                    deadlineMs = System.currentTimeMillis() + STARTUP_AUTH_BUDGET_MS,
                    perCandidateMaxTimeoutS = if (candidates.size > 1) {
                        AUTO_AUTH_CANDIDATE_TIMEOUT_S
                    } else {
                        AUTH_TIMEOUT_S
                    },
                )
            }
        } ?: FptnAuthResult.Failure(FPTN_SERVER_NO_RESPONSE)

        val authenticated = when (authResult) {
            is FptnAuthResult.Success -> authResult.server
            is FptnAuthResult.Failure -> return StartResult.Failure(authResult.reason)
        }

        _currentServer = authenticated.server
        _currentServerIp = authenticated.serverIp
        _accessToken = authenticated.accessToken
        _bypassMethod = fptn.bypassMethod
        _sniDomain = fptn.sniDomain

        Log.d(TAG, "start: authenticated, ready to attach TUN")
        return StartResult.Success(socksPort = 0)
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        val server = _currentServer ?: return TunAttachResult.Failure("Engine not started")
        val serverIp = _currentServerIp ?: return TunAttachResult.Failure("No resolved server IP")
        val token = _accessToken ?: return TunAttachResult.Failure("No access token")

        Log.d(TAG, "attachTun: fd=$tunFd server=${server.name}")

        val pfd = ParcelFileDescriptor.adoptFd(tunFd)
        _pfd = pfd

        val fos = FileOutputStream(pfd.fileDescriptor)

        wsClient.onOpen = {
            Log.d(TAG, "WS connected")
            _stats.value = _stats.value.copy(activeConnections = 1, connectedSince = System.currentTimeMillis())
        }
        wsClient.onMessage = { bytes ->
            try {
                fos.write(bytes)
            } catch (_: Exception) {
            }
        }
        wsClient.onFailure = {
            PersistentLoggers.error(TAG, "WS failure: all reconnect attempts exhausted")
            _stats.value = _stats.value.copy(activeConnections = 0, connectedSince = 0L)
            onEngineFailed("fptn-ws-reconnect-exhausted")
        }

        Log.d(TAG, "attachTun: creating native handle method=$_bypassMethod")
        val handle = runCatching {
            wsClient.nativeCreate(
                serverIp = serverIp,
                serverPort = server.port,
                tunIpv4 = "10.10.0.1",
                tunIpv6 = "fd00::1",
                sni = _sniDomain,
                accessToken = token,
                md5Fingerprint = server.md5Fingerprint,
                censorshipStrategy = _bypassMethod,
            )
        }.getOrElse { e ->
            fos.runCatching { close() }
            PersistentLoggers.error(TAG, "nativeCreate failed: ${e.message}")
            return TunAttachResult.Failure("nativeCreate failed: ${e.message}")
        }
        Log.d(TAG, "attachTun: handle=$handle, starting WS thread")
        runCatching { wsClient.nativeRun(handle) }.getOrElse { e ->
            fos.runCatching { close() }
            runCatching { wsClient.nativeDestroy(handle) }
            PersistentLoggers.error(TAG, "nativeRun failed: ${e.message}")
            return TunAttachResult.Failure("nativeRun failed: ${e.message}")
        }
        _nativeHandle = handle

        tunScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        tunScope = scope

        scope.launch {
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val buf = ByteArray(65535)
                try {
                    while (isActive) {
                        val n = fis.read(buf)
                        if (n > 0) {
                            wsClient.nativeSend(handle, buf.copyOf(n), n.toLong())
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        PersistentLoggers.error(TAG, "TUN read loop terminated: ${e.javaClass.simpleName}")
                    }
                }
            }
        }

        Log.d(TAG, "attachTun: TUN read loop launched")
        return TunAttachResult.Success
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val handle = _nativeHandle
        if (handle == 0L) {
            PersistentLoggers.error(TAG, "awaitReady: no native handle")
            return EnginePlugin.ReadyResult.Timeout("No WS handle")
        }

        Log.d(TAG, "awaitReady: polling WS started (timeout=${READY_TIMEOUT_MS}ms)")
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (wsClient.nativeIsStarted(handle)) {
                Log.d(TAG, "awaitReady: WS ready")
                return EnginePlugin.ReadyResult.Ready
            }
            delay(READY_POLL_MS)
        }
        PersistentLoggers.error(TAG, "awaitReady: timeout after ${READY_TIMEOUT_MS}ms - WS never started")
        return EnginePlugin.ReadyResult.Timeout("FPTN WebSocket not started within ${READY_TIMEOUT_MS}ms")
    }

    override suspend fun stop() {
        Log.d(TAG, "stop: begin")
        val scope = tunScope
        tunScope = null
        val h = _nativeHandle
        _nativeHandle = 0L

        if (h != 0L) {
            Log.d(TAG, "stop: nativeStop handle=$h")
            wsClient.nativeStop(h)
        }
        _pfd?.close()
        _pfd = null

        scope?.cancel()
        scope?.coroutineContext?.get(Job)?.join()
        Log.d(TAG, "stop: TUN loop joined")

        if (h != 0L) {
            wsClient.nativeDestroy(h)
            Log.d(TAG, "stop: nativeDestroy done")
        }
        _currentServer = null
        _currentServerIp = null
        _accessToken = null
        Log.d(TAG, "stop: done")
    }

    override suspend fun probe(): ProbeResult {
        val token = configStore.config().first().token
        if (token.isBlank()) return ProbeResult.Failure("No token configured")
        val parsed = FptnToken.parse(token) ?: return ProbeResult.Failure("Invalid token")
        return if (parsed.servers.isNotEmpty()) {
            ProbeResult.Success(latencyMs = 0L)
        } else {
            ProbeResult.Failure("No servers in token")
        }
    }

    internal fun selectServer(
        config: EngineConfig.Fptn,
        data: FptnTokenData,
    ): FptnServer? {
        if (config.autoSelect) return data.servers.firstOrNull()
        val selectedName = config.selectedServerName ?: return null
        return data.servers.firstOrNull { it.name == selectedName }
    }

    internal fun selectServerCandidates(config: EngineConfig.Fptn, data: FptnTokenData): List<FptnServer> {
        if (config.autoSelect) return data.servers
        val selectedName = config.selectedServerName ?: return emptyList()
        val selected = data.servers.firstOrNull { it.name == selectedName } ?: return emptyList()
        return listOf(selected)
    }

    private suspend fun authenticateFirstAvailable(
        candidates: List<FptnServer>,
        data: FptnTokenData,
        bypassMethod: String,
        sniDomain: String,
        deadlineMs: Long,
        perCandidateMaxTimeoutS: Int,
    ): FptnAuthResult {
        val failures = mutableListOf<String>()
        for (index in candidates.indices) {
            currentCoroutineContext().ensureActive()
            val remainingMs = deadlineMs - System.currentTimeMillis()
            if (remainingMs <= 0L) break
            val server = candidates[index]
            val timeoutS = authTimeoutSeconds(remainingMs, perCandidateMaxTimeoutS)
            when (val auth = authenticate(server, data, bypassMethod, sniDomain, timeoutS)) {
                is ServerAuthResult.Success -> {
                    currentCoroutineContext().ensureActive()
                    val resolveRemainingMs = deadlineMs - System.currentTimeMillis()
                    if (resolveRemainingMs <= 0L) break
                    val serverIp = withTimeoutOrNull(resolveRemainingMs) { resolveServerIp(server) }
                    currentCoroutineContext().ensureActive()
                    if (serverIp != null) {
                        return FptnAuthResult.Success(AuthenticatedServer(server, serverIp, auth.accessToken))
                    }
                    failures += FPTN_SERVER_IP_NOT_FOUND
                    PersistentLoggers.warn(TAG, "authenticate: resolved IP missing server=${server.name}")
                }
                is ServerAuthResult.Failure -> {
                    failures += auth.reason
                    if (auth.terminal) return FptnAuthResult.Failure(auth.reason)
                }
            }
            if (index < candidates.lastIndex) {
                Log.d(TAG, "authenticate: fallback from ${server.name} to ${candidates[index + 1].name}")
            }
        }
        val reason = failures.preferredFptnFailureReason()
        PersistentLoggers.error(TAG, "authenticate: all ${candidates.size} server candidate(s) failed: $reason")
        return FptnAuthResult.Failure(reason)
    }

    private fun authTimeoutSeconds(remainingMs: Long, perCandidateMaxTimeoutS: Int): Int =
        ((remainingMs + 999L) / 1_000L)
            .coerceAtLeast(1L)
            .coerceAtMost(perCandidateMaxTimeoutS.toLong())
            .toInt()

    private suspend fun authenticate(
        server: FptnServer,
        data: FptnTokenData,
        bypassMethod: String,
        sniDomain: String,
        timeoutS: Int,
    ): ServerAuthResult {
        currentCoroutineContext().ensureActive()
        val handle = try {
            httpsClient.nativeCreate(
                host = server.host,
                port = server.port,
                sni = sniDomain,
                md5Fingerprint = server.md5Fingerprint,
                censorshipStrategy = bypassMethod,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = classifyFptnAuthFailure(error = e.message, exceptionName = e.javaClass.simpleName)
            PersistentLoggers.error(
                TAG,
                "authenticate: create exception ${e.javaClass.simpleName}: ${e.message} " +
                    "server=${server.name}:${server.port} sni=$sniDomain bypass=$bypassMethod reason=$reason",
            )
            return ServerAuthResult.Failure(reason)
        }
        return try {
            currentCoroutineContext().ensureActive()
            PersistentLoggers.debug(
                TAG,
                "authenticate: POST /api/v1/login server=${server.name}:${server.port} " +
                    "sni=$sniDomain bypass=$bypassMethod timeout=${timeoutS}s",
            )
            val body = JSONObject().apply {
                put("username", data.username)
                put("password", data.password)
            }.toString()
            val resp = httpsClient.nativePost(handle, "/api/v1/login", body, timeoutS)
            currentCoroutineContext().ensureActive()
            if (resp.code == 200) {
                val token = JSONObject(resp.body).optString("access_token").takeIf { it.isNotBlank() }
                if (token != null) {
                    PersistentLoggers.debug(TAG, "authenticate: success server=${server.name}")
                } else {
                    PersistentLoggers.error(TAG, "authenticate: 200 but no access_token in response")
                }
                token?.let { ServerAuthResult.Success(it) }
                    ?: ServerAuthResult.Failure(FPTN_TOKEN_REJECTED, terminal = true)
            } else {
                val reason = classifyFptnAuthFailure(resp)
                PersistentLoggers.error(
                    TAG,
                    "authenticate: HTTP ${resp.code} error=${resp.error} " +
                        "server=${server.name}:${server.port} sni=$sniDomain bypass=$bypassMethod reason=$reason",
                )
                ServerAuthResult.Failure(reason, terminal = reason == FPTN_TOKEN_REJECTED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PersistentLoggers.error(
                TAG,
                "authenticate: exception ${e.javaClass.simpleName}: ${e.message} " +
                    "server=${server.name}:${server.port} sni=$sniDomain bypass=$bypassMethod",
            )
            ServerAuthResult.Failure(
                classifyFptnAuthFailure(error = e.message, exceptionName = e.javaClass.simpleName),
            )
        } finally {
            httpsClient.nativeDestroy(handle)
        }
    }

    private suspend fun resolveServerIp(server: FptnServer): String? {
        currentCoroutineContext().ensureActive()
        return try {
            InetAddress.getAllByName(server.host)
                .firstOrNull { it is Inet4Address }
                ?.hostAddress
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "resolve: failed server=${server.name} ${e.javaClass.simpleName}")
            null
        }
    }

    private data class AuthenticatedServer(
        val server: FptnServer,
        val serverIp: String,
        val accessToken: String,
    )

    private sealed class FptnAuthResult {
        data class Success(val server: AuthenticatedServer) : FptnAuthResult()
        data class Failure(val reason: String) : FptnAuthResult()
    }

    private sealed class ServerAuthResult {
        data class Success(val accessToken: String) : ServerAuthResult()
        data class Failure(val reason: String, val terminal: Boolean = false) : ServerAuthResult()
    }

    companion object {
        private const val TAG = "FptnEngine"
        private const val AUTH_TIMEOUT_S = 15
        private const val AUTO_AUTH_CANDIDATE_TIMEOUT_S = 5
        private const val STARTUP_AUTH_BUDGET_MS = 15_000L
        private const val READY_TIMEOUT_MS = 30_000L
        private const val READY_POLL_MS = 300L
        internal const val FPTN_TOKEN_REJECTED = "Токен FPTN не принят"
        internal const val FPTN_SERVER_NO_RESPONSE = "Сервер FPTN не ответил"
        internal const val FPTN_MASK_DOMAIN_UNAVAILABLE = "Домен маскировки недоступен"
        internal const val FPTN_SERVER_IP_NOT_FOUND = "Не удалось найти IP сервера"
    }
}

internal fun classifyFptnAuthFailure(
    response: FptnNativeResponse? = null,
    error: String? = response?.error,
    body: String? = response?.body,
    exceptionName: String? = null,
): String {
    val code = response?.code
    val signal = listOfNotNull(error, body, exceptionName).joinToString(" ").lowercase()
    return when {
        code == 401 || code == 403 -> FptnEngine.FPTN_TOKEN_REJECTED
        signal.hasAny("unauthorized", "forbidden", "invalid token", "auth failed", "authentication failed") ->
            FptnEngine.FPTN_TOKEN_REJECTED
        signal.hasAny("sni", "preflight", "handshake", "certificate", "cert", "tls", "ssl") ->
            FptnEngine.FPTN_MASK_DOMAIN_UNAVAILABLE
        code == 608 || signal.hasAny("operation timeout", "timed out", "timeout") ->
            FptnEngine.FPTN_SERVER_NO_RESPONSE
        signal.hasAny("unknownhost", "unresolved", "resolve", "dns", "nodename nor servname") ->
            FptnEngine.FPTN_SERVER_IP_NOT_FOUND
        else -> FptnEngine.FPTN_SERVER_NO_RESPONSE
    }
}

private fun List<String>.preferredFptnFailureReason(): String = when {
    contains(FptnEngine.FPTN_TOKEN_REJECTED) -> FptnEngine.FPTN_TOKEN_REJECTED
    contains(FptnEngine.FPTN_MASK_DOMAIN_UNAVAILABLE) -> FptnEngine.FPTN_MASK_DOMAIN_UNAVAILABLE
    contains(FptnEngine.FPTN_SERVER_IP_NOT_FOUND) -> FptnEngine.FPTN_SERVER_IP_NOT_FOUND
    contains(FptnEngine.FPTN_SERVER_NO_RESPONSE) -> FptnEngine.FPTN_SERVER_NO_RESPONSE
    else -> FptnEngine.FPTN_SERVER_NO_RESPONSE
}

private fun String.hasAny(vararg needles: String): Boolean = needles.any(::contains)
