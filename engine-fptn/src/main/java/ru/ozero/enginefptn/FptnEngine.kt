package ru.ozero.enginefptn

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

        FptnNativeWebSocket.loadOnce()
        if (!FptnNativeWebSocket.libraryLoaded) {
            return StartResult.Failure(
                "fptn_native_lib not loaded: ${FptnNativeWebSocket.loadError}"
            )
        }

        val accessToken = withContext(Dispatchers.IO) {
            authenticate(server, tokenData, fptn.bypassMethod)
        } ?: return StartResult.Failure("FPTN authentication failed")

        _currentServer = server
        _accessToken = accessToken
        _bypassMethod = fptn.bypassMethod

        return StartResult.Success(socksPort = 0)
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        val server = _currentServer ?: return TunAttachResult.Failure("Engine not started")
        val token = _accessToken ?: return TunAttachResult.Failure("No access token")

        val pfd = ParcelFileDescriptor.adoptFd(tunFd)
        _pfd = pfd

        val fos = FileOutputStream(pfd.fileDescriptor)

        wsClient.onOpen = { Log.i(TAG, "FPTN WebSocket connected") }
        wsClient.onMessage = { bytes ->
            try { fos.write(bytes) } catch (_: Exception) {}
        }
        wsClient.onFailure = {
            PersistentLoggers.error(TAG, "FPTN WebSocket failure")
        }

        val handle = wsClient.nativeCreate(
            serverIp = server.host,
            serverPort = server.port,
            tunIpv4 = "10.10.0.1",
            tunIpv6 = "fd00::1",
            sni = server.host,
            accessToken = token,
            md5Fingerprint = server.md5Fingerprint,
            censorshipStrategy = _bypassMethod,
        )
        _nativeHandle = handle
        wsClient.nativeRun(handle)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        tunScope = scope

        scope.launch {
            val fis = FileInputStream(pfd.fileDescriptor)
            val buf = ByteArray(65535)
            try {
                while (isActive) {
                    val n = fis.read(buf)
                    if (n > 0) {
                        wsClient.nativeSend(handle, buf.copyOf(n), n.toLong())
                    }
                }
            } catch (_: Exception) {}
        }

        return TunAttachResult.Success
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val handle = _nativeHandle
        if (handle == 0L) return EnginePlugin.ReadyResult.Timeout("No WS handle")

        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (wsClient.nativeIsStarted(handle)) return EnginePlugin.ReadyResult.Ready
            delay(READY_POLL_MS)
        }
        return EnginePlugin.ReadyResult.Timeout("FPTN WebSocket not started within ${READY_TIMEOUT_MS}ms")
    }

    override suspend fun stop() {
        tunScope?.cancel()
        tunScope = null

        val h = _nativeHandle
        if (h != 0L) {
            wsClient.nativeStop(h)
            wsClient.nativeDestroy(h)
            _nativeHandle = 0L
        }

        _pfd?.close()
        _pfd = null
        _currentServer = null
        _accessToken = null
    }

    override suspend fun probe(): ProbeResult {
        val token = configStore.config().first().token
        if (token.isBlank()) return ProbeResult.Failure("No token configured")
        val parsed = FptnToken.parse(token) ?: return ProbeResult.Failure("Invalid token")
        return if (parsed.servers.isNotEmpty()) ProbeResult.Success
        else ProbeResult.Failure("No servers in token")
    }

    private fun selectServer(config: EngineConfig.Fptn, data: FptnTokenData): FptnServer? {
        if (config.autoSelect || config.selectedServerName == null) return data.servers.firstOrNull()
        return data.servers.firstOrNull { it.name == config.selectedServerName }
            ?: data.servers.firstOrNull()
    }

    private fun authenticate(server: FptnServer, data: FptnTokenData, bypassMethod: String): String? {
        val handle = httpsClient.nativeCreate(
            host = server.host,
            port = server.port,
            sni = server.host,
            md5Fingerprint = server.md5Fingerprint,
            censorshipStrategy = bypassMethod,
        )
        return try {
            val body = JSONObject().apply {
                put("username", data.username)
                put("password", data.password)
            }.toString()
            val resp = httpsClient.nativePost(handle, "/api/v1/login", body, AUTH_TIMEOUT_S)
            if (resp.code == 200) {
                JSONObject(resp.body).optString("access_token").takeIf { it.isNotBlank() }
            } else {
                PersistentLoggers.error(TAG, "FPTN auth failed: ${resp.code} ${resp.error}")
                null
            }
        } catch (e: Exception) {
            PersistentLoggers.error(TAG, "FPTN auth exception: ${e.message}")
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
