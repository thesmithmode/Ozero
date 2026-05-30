package ru.ozero.enginefptn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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
import kotlin.system.measureTimeMillis

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

        val authenticated = withContext(Dispatchers.IO) {
            withTimeoutOrNull(STARTUP_AUTH_BUDGET_MS) {
                authenticateFirstAvailable(
                    candidates = candidates,
                    data = tokenData,
                    bypassMethod = fptn.bypassMethod,
                    sniDomain = fptn.sniDomain,
                    deadlineMs = System.currentTimeMillis() + STARTUP_AUTH_BUDGET_MS,
                    autoSelect = fptn.autoSelect,
                )
            }
        } ?: return StartResult.Failure("FPTN authentication failed")

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
        autoSelect: Boolean,
    ): AuthenticatedServer? {
        val orderedCandidates = if (autoSelect && candidates.size > 1) {
            val healthResults = findReachableCandidates(candidates, bypassMethod, sniDomain, deadlineMs)
            prioritizeAuthenticationCandidates(candidates, healthResults)
        } else {
            candidates
        }
        val authenticated = authenticateCandidates(
            candidates = orderedCandidates,
            data = data,
            bypassMethod = bypassMethod,
            sniDomain = sniDomain,
            deadlineMs = deadlineMs,
            perCandidateMaxTimeoutS = if (autoSelect && orderedCandidates.size > 1) {
                AUTO_AUTH_FALLBACK_TIMEOUT_S
            } else {
                AUTH_TIMEOUT_S
            },
        )
        if (authenticated == null) {
            PersistentLoggers.error(TAG, "authenticate: all ${candidates.size} server candidate(s) failed")
        }
        return authenticated
    }

    private suspend fun findReachableCandidates(
        candidates: List<FptnServer>,
        bypassMethod: String,
        sniDomain: String,
        deadlineMs: Long,
    ): List<FptnHealthCheck> = coroutineScope {
        val results = mutableListOf<FptnHealthCheck>()
        val healthDeadlineMs = minOf(deadlineMs, System.currentTimeMillis() + AUTO_HEALTH_BUDGET_MS)
        for (batch in candidates.chunked(AUTO_HEALTH_CONCURRENCY)) {
            currentCoroutineContext().ensureActive()
            if (System.currentTimeMillis() >= healthDeadlineMs) break
            val checks = batch.map { server ->
                async {
                    val remainingMs = healthDeadlineMs - System.currentTimeMillis()
                    if (remainingMs <= 0L) {
                        FptnHealthCheck(server, null)
                    } else {
                        healthCheck(
                            server = server,
                            bypassMethod = bypassMethod,
                            sniDomain = sniDomain,
                            timeoutS = authTimeoutSeconds(remainingMs, AUTO_HEALTH_TIMEOUT_S),
                        )
                    }
                }
            }.awaitAll()
            results += checks
            if (results.size >= AUTO_HEALTH_MIN_CHECKS && results.any { it.latencyMs != null }) break
        }
        results
    }

    private suspend fun healthCheck(
        server: FptnServer,
        bypassMethod: String,
        sniDomain: String,
        timeoutS: Int,
    ): FptnHealthCheck {
        currentCoroutineContext().ensureActive()
        val handle = httpsClient.nativeCreate(
            host = server.host,
            port = server.port,
            sni = sniDomain,
            md5Fingerprint = server.md5Fingerprint,
            censorshipStrategy = bypassMethod,
        )
        return try {
            var ok = false
            val latencyMs = measureTimeMillis {
                val resp = httpsClient.nativeGet(handle, "/api/v1/test/file.bin", timeoutS)
                ok = resp.code in 200..399
            }
            FptnHealthCheck(server, latencyMs.takeIf { ok })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "health: failed server=${server.name} ${e.javaClass.simpleName}")
            FptnHealthCheck(server, null)
        } finally {
            httpsClient.nativeDestroy(handle)
        }
    }

    internal fun prioritizeAuthenticationCandidates(
        candidates: List<FptnServer>,
        healthResults: List<FptnHealthCheck>,
    ): List<FptnServer> {
        val checked = healthResults.map { it.server }.toSet()
        val reachable = healthResults
            .filter { it.latencyMs != null }
            .sortedWith(
                compareBy<FptnHealthCheck> { it.latencyMs ?: Long.MAX_VALUE }
                    .thenBy { candidates.indexOf(it.server) },
            )
            .map { it.server }
        if (reachable.isEmpty()) return candidates
        val unchecked = candidates.filterNot { checked.contains(it) }
        return reachable + unchecked
    }

    private suspend fun authenticateCandidates(
        candidates: List<FptnServer>,
        data: FptnTokenData,
        bypassMethod: String,
        sniDomain: String,
        deadlineMs: Long,
        perCandidateMaxTimeoutS: Int,
    ): AuthenticatedServer? {
        for (index in candidates.indices) {
            currentCoroutineContext().ensureActive()
            val remainingMs = deadlineMs - System.currentTimeMillis()
            if (remainingMs <= 0L) break
            val server = candidates[index]
            val timeoutS = authTimeoutSeconds(remainingMs, perCandidateMaxTimeoutS)
            val accessToken = authenticate(server, data, bypassMethod, sniDomain, timeoutS)
            currentCoroutineContext().ensureActive()
            if (accessToken != null) {
                val resolveRemainingMs = deadlineMs - System.currentTimeMillis()
                if (resolveRemainingMs <= 0L) break
                val serverIp = withTimeoutOrNull(resolveRemainingMs) { resolveServerIp(server) }
                currentCoroutineContext().ensureActive()
                if (serverIp != null) {
                    return AuthenticatedServer(server, serverIp, accessToken)
                }
                PersistentLoggers.warn(TAG, "authenticate: resolved IP missing server=${server.name}")
            }
            if (index < candidates.lastIndex) {
                Log.d(TAG, "authenticate: fallback from ${server.name} to ${candidates[index + 1].name}")
            }
        }
        return null
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
    ): String? {
        currentCoroutineContext().ensureActive()
        val handle = httpsClient.nativeCreate(
            host = server.host,
            port = server.port,
            sni = sniDomain,
            md5Fingerprint = server.md5Fingerprint,
            censorshipStrategy = bypassMethod,
        )
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
                token
            } else {
                PersistentLoggers.error(
                    TAG,
                    "authenticate: HTTP ${resp.code} error=${resp.error} " +
                        "server=${server.name}:${server.port} sni=$sniDomain bypass=$bypassMethod",
                )
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PersistentLoggers.error(
                TAG,
                "authenticate: exception ${e.javaClass.simpleName}: ${e.message} " +
                    "server=${server.name}:${server.port} sni=$sniDomain bypass=$bypassMethod",
            )
            null
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

    internal data class FptnHealthCheck(
        val server: FptnServer,
        val latencyMs: Long?,
    )

    private data class AuthenticatedServer(
        val server: FptnServer,
        val serverIp: String,
        val accessToken: String,
    )

    companion object {
        private const val TAG = "FptnEngine"
        private const val AUTH_TIMEOUT_S = 15
        private const val AUTO_HEALTH_TIMEOUT_S = 1
        private const val AUTO_HEALTH_CONCURRENCY = 4
        private const val AUTO_HEALTH_MIN_CHECKS = 12
        private const val AUTO_HEALTH_BUDGET_MS = 8_000L
        private const val AUTO_AUTH_FALLBACK_TIMEOUT_S = 5
        private const val STARTUP_AUTH_BUDGET_MS = 20_000L
        private const val READY_TIMEOUT_MS = 30_000L
        private const val READY_POLL_MS = 300L
    }
}
