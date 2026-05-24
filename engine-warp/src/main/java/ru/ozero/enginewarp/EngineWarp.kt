package ru.ozero.enginewarp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.IpProbeRoute
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.VpnSocketProtector
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginescore.settings.SettingsModel

class EngineWarp(
    private val autoConfig: WarpAutoConfig,
    private val configStore: WarpConfigSlotStore,
    private val sdkBridge: WarpSdkBridge,
    private val uapiPathProvider: () -> String,
    private val context: Context? = null,
    private val socketProtector: VpnSocketProtector = VpnSocketProtectorHolder,
    private val ipv6EnabledProvider: () -> Boolean = { false },
    private val handshakeChecker: (uapiPath: String, tunnelName: String) -> Boolean = WarpHandshakeUapi::check,
    private val uapiStateReader: (uapiPath: String, tunnelName: String) -> WarpUapiState? = WarpUapi::readState,
    private val warpReadyTimeoutMs: Long = WARP_READY_TIMEOUT_MS,
    private val warpReadyPollMs: Long = WARP_READY_POLL_MS,
    private val statsPollIntervalMs: Long = STATS_POLL_INTERVAL_MS,
    private val handshakeStaleThresholdSec: Long = HANDSHAKE_STALE_THRESHOLD_SEC,
    pluginScope: CoroutineScope? = null,
) : EnginePlugin, TunFdAcceptor {

    private val ownedScope: CoroutineScope =
        pluginScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statsJobRef = AtomicReference<Job?>(null)
    private val connectedSinceRef = AtomicReference<Long>(0L)

    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override val id = EngineId.WARP

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    private val _stats = MutableStateFlow(EngineStats())

    @Volatile
    private var resolvedConfig: WarpConfig? = null

    @Volatile
    private var resolvedIni: String? = null

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig = EngineConfig.Warp

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Warp) { "EngineWarp требует EngineConfig.Warp" }
        require(upstream is Upstream.None) {
            "EngineWarp не принимает upstream — supportsUpstreamSocks=false"
        }
        val cached = resolvedConfig
        val cachedIni = resolvedIni
        val resolved = if (cached != null && cachedIni != null) {
            ResolvedWarp(cached, cachedIni, "cached")
        } else {
            resolveActive() ?: return StartResult.Failure(
                reason = "WARP config resolve failed (auto-register не сработал)",
            )
        }
        resolvedConfig = resolved.config
        resolvedIni = resolved.ini
        Log.i(TAG, "resolved config: ${resolved.config} (iniSource=${resolved.iniSource})")
        return StartResult.Success(socksPort = WARP_NO_SOCKS_PORT)
    }

    override suspend fun stop() {
        Log.i(TAG, "stop — detaching tun")
        statsJobRef.getAndSet(null)?.cancel()
        connectedSinceRef.set(0L)
        _stats.value = EngineStats()
        networkCallback?.let { cb ->
            networkCallback = null
            runCatching {
                (context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                    ?.unregisterNetworkCallback(cb)
            }.onFailure { PersistentLoggers.warn(TAG, "unregisterNetworkCallback failed: ${it.message}") }
        }
        sdkBridge.detachTun()
        resolvedConfig = null
        resolvedIni = null
    }

    override suspend fun recover(): EnginePlugin.RecoverResult {
        val uapiPath = uapiPathProvider()
        val state = uapiStateReader(uapiPath, TUNNEL_NAME)
            ?: return EnginePlugin.RecoverResult.Failed("UAPI недоступен — handshake state не читается")
        val ageS = state.handshakeAgeSeconds
        return if (ageS != null && ageS < handshakeStaleThresholdSec) {
            PersistentLoggers.info(TAG, "recover: handshake age=${ageS}s — OK, без действий")
            EnginePlugin.RecoverResult.Success
        } else {
            PersistentLoggers.warn(
                TAG,
                "recover: handshake stale (age=$ageS, threshold=${handshakeStaleThresholdSec}s) — " +
                    "Failed — amneziawg-go ретраит handshake в фоне, watchdog продолжит ждать",
            )
            EnginePlugin.RecoverResult.Failed("handshake stale age=${ageS ?: "never"}s")
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val uapiPath = uapiPathProvider()
        val reached = withTimeoutOrNull(warpReadyTimeoutMs) {
            while (true) {
                val ready = runCatching { handshakeChecker(uapiPath, TUNNEL_NAME) }.getOrDefault(false)
                if (ready) {
                    Log.i(TAG, "awaitReady: WireGuard handshake complete")
                    return@withTimeoutOrNull Unit
                }
                delay(warpReadyPollMs)
            }
        }
        return if (reached != null) {
            EnginePlugin.ReadyResult.Ready
        } else {
            val state = runCatching { uapiStateReader(uapiPath, TUNNEL_NAME) }.getOrNull()
            val diag = if (state != null) {
                "rx=${state.rxBytes} tx=${state.txBytes} peers=${state.peersSeen} " +
                    "lastHsAge=${state.handshakeAgeSeconds ?: "never"}"
            } else {
                "uapi unreachable — tunnel handle invalid or socket missing; " +
                    "dirListing=${WarpSocketDiagnostics.listSocketCandidates(uapiPath)}"
            }
            val reason = "WARP: WireGuard handshake timeout ${warpReadyTimeoutMs}ms ($diag)"
            PersistentLoggers.warn(TAG, "awaitReady timeout — $reason — proceeding")
            EnginePlugin.ReadyResult.Timeout(reason)
        }
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure(reason = "WARP не предоставляет SOCKS-интерфейс")

    override suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute {
        val connected = resolvedConfig?.peerEndpoint?.isNotBlank() == true
        return if (connected) {
            IpProbeRoute.StaticLocation(country = "Cloudflare WARP", countryCode = null)
        } else {
            IpProbeRoute.Unavailable("WARP не подключён")
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override fun preflight(): ru.ozero.enginescore.EnginePreflight =
        WarpPreflight(peerEndpointProvider = { resolvedConfig?.peerEndpoint })

    override suspend fun tunSpec(): TunSpec? {
        val cfg = resolvedConfig
            ?: resolveActive()?.also {
                resolvedConfig = it.config
                resolvedIni = it.ini
            }?.config
            ?: return null
        val v4Addr = cfg.interfaceAddressV4.substringBefore('/').takeIf { it.isNotBlank() }
            ?: return null
        val v4Prefix = cfg.interfaceAddressV4.substringAfter('/', missingDelimiterValue = "32")
            .toIntOrNull() ?: 32
        val ipv6Allowed = cfg.interfaceAddressV6.isNotBlank() && ipv6EnabledProvider()
        val v6Addr = cfg.interfaceAddressV6.substringBefore('/').takeIf { it.isNotBlank() && ipv6Allowed }
        val v6Prefix = cfg.interfaceAddressV6.substringAfter('/', missingDelimiterValue = "128")
            .toIntOrNull() ?: 128
        return TunSpec(
            sessionName = "WARP",
            mtu = cfg.mtu,
            blocking = true,
            ipv4Address = v4Addr,
            ipv4PrefixLength = v4Prefix,
            dnsServers = cfg.dnsServers.filter { ipv6Allowed || !it.contains(':') },
            allowFamilyV4 = true,
            allowFamilyV6 = v6Addr != null,
            ipv6Address = v6Addr,
            ipv6PrefixLength = v6Prefix,
            excludeRfc1918 = false,
            routeAllV4 = true,
            routeAllV6 = v6Addr != null,
        )
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        val ini = resolvedIni ?: return TunAttachResult.Failure(
            reason = "attachTun до start — нет ini config",
        )
        val uapiPath = uapiPathProvider()
        Log.i(TAG, "attachTun fd=$tunFd uapi=$uapiPath/$TUNNEL_NAME.sock")
        return when (val r = sdkBridge.attachTun(TUNNEL_NAME, tunFd, ini, uapiPath, socketProtector)) {
            WarpSdkBridge.AttachResult.Success -> {
                startStatsPoll(uapiPath)
                registerNetworkCallback()
                TunAttachResult.Success
            }
            is WarpSdkBridge.AttachResult.Failed -> {
                val maskedIni = ini.replace(Regex("(?m)^(PrivateKey\\s*=\\s*)(.+)$"), "$1<masked>")
                PersistentLoggers.error(TAG, "attachTun failed: ${r.reason}\nini:\n$maskedIni")
                TunAttachResult.Failure(r.reason)
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                sdkBridge.reprotectSockets()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onSuccess { networkCallback = cb }
            .onFailure { PersistentLoggers.warn(TAG, "registerDefaultNetworkCallback failed: ${it.message}") }
    }

    private fun startStatsPoll(uapiPath: String) {
        statsJobRef.getAndSet(null)?.cancel()
        val job = ownedScope.launch {
            var prevRx = 0L
            var prevTx = 0L
            var tick = 0
            var consecutiveNullReads = 0
            try {
                while (isActive) {
                    val state = uapiStateReader(uapiPath, TUNNEL_NAME)
                    if (state != null) {
                        consecutiveNullReads = 0
                        val ageS = state.handshakeAgeSeconds
                        val handshakeRecent = ageS != null && ageS < handshakeStaleThresholdSec
                        if (handshakeRecent && connectedSinceRef.get() == 0L) {
                            connectedSinceRef.set(System.currentTimeMillis())
                        } else if (!handshakeRecent) {
                            connectedSinceRef.set(0L)
                        }
                        _stats.value = EngineStats(
                            bytesIn = state.rxBytes,
                            bytesOut = state.txBytes,
                            connectedSince = connectedSinceRef.get(),
                            activeConnections = if (handshakeRecent) 1 else 0,
                        )
                        tick += 1
                        if (tick % STATS_LOG_EVERY == 0) {
                            val dRx = state.rxBytes - prevRx
                            val dTx = state.txBytes - prevTx
                            PersistentLoggers.info(
                                TAG,
                                "warp stats tx=${state.txBytes}B rx=${state.rxBytes}B " +
                                    "Δtx=${dTx}B Δrx=${dRx}B hsAge=${ageS ?: "never"}s",
                            )
                            prevRx = state.rxBytes
                            prevTx = state.txBytes
                        }
                    } else {
                        consecutiveNullReads += 1
                        connectedSinceRef.set(0L)
                        _stats.value = _stats.value.copy(
                            connectedSince = 0L,
                            activeConnections = 0,
                        )
                        tick += 1
                        if (consecutiveNullReads == UAPI_NULL_DEGRADED_THRESHOLD) {
                            PersistentLoggers.warn(
                                TAG,
                                "warp UAPI null x$consecutiveNullReads — пометили activeConnections=0 " +
                                    "(peer watchdog подберёт через ${PEER_WATCHDOG_HINT_S}s)",
                            )
                        }
                        if (tick % STATS_LOG_EVERY == 0) {
                            PersistentLoggers.warn(
                                TAG,
                                "warp stats unavailable — UAPI socket read returned null " +
                                    "(uapi=$uapiPath/$TUNNEL_NAME) — handle invalid или socket путь не найден",
                            )
                        }
                    }
                    delay(statsPollIntervalMs)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException("stats poll cancelled")
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "stats poll threw: ${t.message}")
            }
        }
        statsJobRef.set(job)
    }

    private data class ResolvedWarp(val config: WarpConfig, val ini: String, val iniSource: String)

    private suspend fun resolveActive(): ResolvedWarp? {
        val slot = configStore.activeSlot().first()
        return if (slot != null) {
            buildResolved(slot.config, slot.rawIniOverride, source = "slot")
        } else {
            PersistentLoggers.info(TAG, "no active config — autoConfig.register")
            val regResult = autoConfig.register()
            val fresh = regResult.getOrElse { t ->
                PersistentLoggers.error(TAG, "register failed: ${t.message}")
                return null
            }
            val host = fresh.config.peerEndpoint.substringBeforeLast(':').trim().ifBlank { "auto" }
            runCatching { configStore.addSlot("WARP $host", fresh.config, fresh.rawIni) }
                .onSuccess { Log.i(TAG, "auto-registered config saved as slot $it") }
                .onFailure { t ->
                    if (t is WarpConfigDuplicateException) {
                        runCatching { configStore.setActive(t.existingSlotId) }
                            .onSuccess {
                                Log.i(TAG, "auto-register duplicate — activated existing slot ${t.existingSlotId}")
                            }
                            .onFailure { e -> PersistentLoggers.warn(TAG, "setActive duplicate failed: ${e.message}") }
                    } else {
                        PersistentLoggers.warn(TAG, "addSlot failed: ${t.message}")
                    }
                }
            buildResolved(fresh.config, fresh.rawIni, source = "auto")
        }
    }

    private suspend fun buildResolved(config: WarpConfig, rawIni: String?, source: String): ResolvedWarp {
        val resolvedConfig = resolveEndpointHost(config)
        val ipv6Allowed = config.interfaceAddressV6.isNotBlank() && ipv6EnabledProvider()
        val baseIni = if (!rawIni.isNullOrBlank()) {
            applyEndpointToRawIni(rawIni, resolvedConfig.peerEndpoint)
        } else {
            WarpIniBuilder.build(resolvedConfig)
        }
        val ini = if (ipv6Allowed) baseIni else stripIpv6FromIni(baseIni)
        val iniSource = when {
            !rawIni.isNullOrBlank() -> "raw($source)"
            else -> "builder($source)"
        }
        return ResolvedWarp(config = resolvedConfig, ini = ini, iniSource = iniSource)
    }

    private fun stripIpv6FromIni(ini: String): String {
        val out = StringBuilder()
        ini.lineSequence().forEachIndexed { idx, line ->
            if (idx > 0) out.append('\n')
            val eq = line.indexOf('=')
            if (eq <= 0) {
                out.append(line)
                return@forEachIndexed
            }
            val key = line.substring(0, eq).trim().lowercase()
            if (key != "address" && key != "allowedips" && key != "dns") {
                out.append(line)
                return@forEachIndexed
            }
            val value = line.substring(eq + 1)
            val ipv4Items = value.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.contains(':') }
            if (ipv4Items.isEmpty()) {
                return@forEachIndexed
            }
            val indent = line.takeWhile { it.isWhitespace() }
            val keyText = line.substring(indent.length, eq).trimEnd()
            out.append(indent).append(keyText).append(" = ").append(ipv4Items.joinToString(", "))
        }
        return out.toString()
    }

    private suspend fun resolveEndpointHost(cfg: WarpConfig): WarpConfig {
        val ep = cfg.peerEndpoint
        val sep = ep.lastIndexOf(':')
        if (sep < 0) return cfg
        val host = ep.substring(0, sep)
        val port = ep.substring(sep + 1)
        if (host.isBlank() || isLikelyIpAddress(host)) return cfg
        val provider = resolvedConfig?.doHProvider ?: DoHProvider.SYSTEM
        Log.i(TAG, "resolveEndpointHost host=$host provider=${provider.name}")
        for (attempt in 0..2) {
            val resolved = if (provider.isSystem) {
                withContext(Dispatchers.IO) {
                    runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()
                }
            } else {
                withContext(Dispatchers.IO) { resolveViaDoH(host, provider.url) }
            }
            if (!resolved.isNullOrBlank()) {
                Log.i(TAG, "endpoint resolved $host → $resolved via ${provider.name} (attempt ${attempt + 1})")
                return cfg.copy(peerEndpoint = "$resolved:$port")
            }
            if (attempt < 2) delay(200L shl attempt)
        }
        PersistentLoggers.warn(TAG, "endpoint resolve failed after 3 attempts for $host via ${provider.name}")
        return cfg
    }

    private fun resolveViaDoH(host: String, dohUrl: String): String? = runCatching {
        val url = java.net.URL("$dohUrl?name=$host&type=A")
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.connectTimeout = DOH_CONNECT_TIMEOUT_MS
            conn.readTimeout = DOH_READ_TIMEOUT_MS
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().readText()
            Regex("\"data\"\\s*:\\s*\"([0-9]{1,3}(?:\\.[0-9]{1,3}){3})\"").find(body)?.groupValues?.get(1)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun applyEndpointToRawIni(rawIni: String, resolvedEndpoint: String): String =
        rawIni.lineSequence().joinToString("\n") { line ->
            val eqIdx = line.indexOf('=')
            if (eqIdx > 0 && line.substring(0, eqIdx).trim().equals("Endpoint", ignoreCase = true)) {
                val indent = line.takeWhile { it.isWhitespace() }
                "${indent}Endpoint = $resolvedEndpoint"
            } else {
                line
            }
        }

    internal companion object {
        fun isLikelyIpAddress(host: String): Boolean {
            if (host.isEmpty()) return false
            if (host.startsWith('[') || host.contains(':')) return true
            return host.all { it.isDigit() || it == '.' }
        }

        const val TAG = "EngineWarp"
        const val WARP_NO_SOCKS_PORT = 0
        const val TUNNEL_NAME = "ozero-warp"
        const val DOH_CONNECT_TIMEOUT_MS = 3_000
        const val DOH_READ_TIMEOUT_MS = 3_000
        const val WARP_READY_TIMEOUT_MS = 10_000L
        const val WARP_READY_POLL_MS = 100L
        const val STATS_POLL_INTERVAL_MS = 5_000L
        const val HANDSHAKE_STALE_THRESHOLD_SEC = 180L
        const val STATS_LOG_EVERY = 5
        const val UAPI_NULL_DEGRADED_THRESHOLD = 3
        const val PEER_WATCHDOG_HINT_S = 30
    }
}
