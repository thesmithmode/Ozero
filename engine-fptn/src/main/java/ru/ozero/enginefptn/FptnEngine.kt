package ru.ozero.enginefptn

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import android.util.Log
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

class FptnEngine(
    private val configStore: FptnConfigStore,
) : EnginePlugin, TunFdAcceptor {

    private val wsClient = FptnNativeWebSocket()
    private val httpsClient = FptnNativeHttpsClient()

    private val _stats = MutableStateFlow(EngineStats())
    private var tunScope: CoroutineScope? = null
    private var _nativeHandle: Long = 0L
    private var _currentServer: FptnServer? = null
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
    )

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

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
            ?: return StartResult.Failure("Invalid FPTN token")

        val server = selectServer(fptn, tokenData)
            ?: return StartResult.Failure("No FPTN server available")

        Log.d(TAG, "start server=${server.name}:${server.port} bypass=${fptn.bypassMethod} n=${tokenData.servers.size}")

        FptnNativeWebSocket.loadOnce()
        if (!FptnNativeWebSocket.libraryLoaded) {
            PersistentLoggers.error(TAG, "native lib load failed: ${FptnNativeWebSocket.loadError}")
            return StartResult.Failure(
                "fptn_native_lib not loaded: ${FptnNativeWebSocket.loadError}"
            )
        }

        val accessToken = withContext(Dispatchers.IO) {
            authenticate(server, tokenData, fptn.bypassMethod, fptn.sniDomain)
        } ?: return StartResult.Failure("FPTN authentication failed")

        _currentServer = server
        _accessToken = accessToken
        _bypassMethod = fptn.bypassMethod
        _sniDomain = fptn.sniDomain

        Log.d(TAG, "start: authenticated, ready to attach TUN")
        return StartResult.Success(socksPort = 0)
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        val server = _currentServer ?: return TunAttachResult.Failure("Engine not started")
        val token = _accessToken ?: return TunAttachResult.Failure("No access token")

        Log.d(TAG, "attachTun: fd=$tunFd server=${server.name}")

        val pfd = ParcelFileDescriptor.adoptFd(tunFd)
        _pfd = pfd

        val fos = FileOutputStream(pfd.fileDescriptor)

        wsClient.onOpen = { Log.d(TAG, "WS connected") }
        wsClient.onMessage = { bytes ->
            try {
                fos.write(bytes)
            } catch (_: Exception) {
            }
        }
        wsClient.onFailure = {
            PersistentLoggers.error(TAG, "WS failure: all reconnect attempts exhausted")
        }

        Log.d(TAG, "attachTun: creating native handle method=$_bypassMethod")
        val handle = runCatching {
            wsClient.nativeCreate(
                serverIp = server.host,
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
        PersistentLoggers.error(TAG, "awaitReady: timeout after ${READY_TIMEOUT_MS}ms — WS never started")
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

    private fun selectServer(config: EngineConfig.Fptn, data: FptnTokenData): FptnServer? {
        if (config.autoSelect || config.selectedServerName == null) return data.servers.firstOrNull()
        return data.servers.firstOrNull { it.name == config.selectedServerName }
            ?: data.servers.firstOrNull()
    }

    private fun authenticate(
        server: FptnServer,
        data: FptnTokenData,
        bypassMethod: String,
        sniDomain: String,
    ): String? {
        val handle = httpsClient.nativeCreate(
            host = server.host,
            port = server.port,
            sni = sniDomain,
            md5Fingerprint = server.md5Fingerprint,
            censorshipStrategy = bypassMethod,
        )
        return try {
            Log.d(TAG, "authenticate: POST /api/v1/login timeout=${AUTH_TIMEOUT_S}s")
            val body = JSONObject().apply {
                put("username", data.username)
                put("password", data.password)
            }.toString()
            val resp = httpsClient.nativePost(handle, "/api/v1/login", body, AUTH_TIMEOUT_S)
            if (resp.code == 200) {
                val token = JSONObject(resp.body).optString("access_token").takeIf { it.isNotBlank() }
                if (token != null) {
                    Log.d(TAG, "authenticate: success")
                } else {
                    PersistentLoggers.error(TAG, "authenticate: 200 but no access_token in response")
                }
                token
            } else {
                PersistentLoggers.error(TAG, "authenticate: HTTP ${resp.code} error=${resp.error}")
                null
            }
        } catch (e: Exception) {
            PersistentLoggers.error(TAG, "authenticate: exception ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            httpsClient.nativeDestroy(handle)
        }
    }

    companion object {
        private const val TAG = "FptnEngine"
        private const val AUTH_TIMEOUT_S = 15
        private const val READY_TIMEOUT_MS = 30_000L
        private const val READY_POLL_MS = 300L
    }
}
