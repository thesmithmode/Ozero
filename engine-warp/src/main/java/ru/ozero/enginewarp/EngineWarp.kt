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
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.VpnSocketProtector
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginescore.probe.Socks5HandshakeProbe
import ru.ozero.enginescore.settings.SettingsModel

@Suppress("TooManyFunctions")
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
    private val endpointProber: WarpEndpointProber = WarpEndpointProber(),
    pluginScope: CoroutineScope? = null,
) : EnginePlugin, TunFdAcceptor {

    private val ownedScope: CoroutineScope =
        pluginScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statsJobRef = AtomicReference<Job?>(null)
    private val connectedSinceRef = AtomicReference<Long>(0L)

    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile private var savedTunFd: Int = -1

    @Volatile private var consecutiveRecoverFails: Int = 0

    override val id = EngineId.WARP

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
        providesLocalSocks = true,
        providesLocalSocksWithoutUpstream = true,
    )

    private val _stats = MutableStateFlow(EngineStats())

    @Volatile
    private var resolvedConfig: WarpConfig? = null

    @Volatile
    private var resolvedIni: String? = null

    @Volatile
    private var activeSocksPort: Int = WARP_NO_SOCKS_PORT

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig = EngineConfig.Warp

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig = EngineConfig.WarpProxy()

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        if (config is EngineConfig.WarpProxy) return startProxy(config, upstream)
        require(config is EngineConfig.Warp) { "EngineWarp requires EngineConfig.Warp" }
        require(upstream is Upstream.None) {
            "EngineWarp does not accept upstream - supportsUpstreamSocks=false"
        }
        val cached = resolvedConfig
        val cachedIni = if (activeSocksPort > 0) null else resolvedIni
        val resolved = if (cached != null && cachedIni != null) {
            ResolvedWarp(cached, cachedIni, "cached")
        } else {
            resolveActive() ?: return StartResult.Failure(
                reason = "WARP config resolve failed (auto-register unavailable)",
            )
        }
        resolvedConfig = resolved.config
        resolvedIni = resolved.ini
        Log.i(TAG, "resolved config: ${resolved.config} (iniSource=${resolved.iniSource})")
        activeSocksPort = WARP_NO_SOCKS_PORT
        return StartResult.Success(socksPort = WARP_NO_SOCKS_PORT)
    }

    private suspend fun startProxy(config: EngineConfig.WarpProxy, upstream: Upstream): StartResult {
        require(upstream is Upstream.None) {
            "EngineWarp proxy does not accept upstream because supportsUpstreamSocks=false"
        }
        val cached = resolvedConfig
        val cachedIni = if (activeSocksPort > 0) null else resolvedIni
        val resolved = if (cached != null && cachedIni != null) {
            ResolvedWarp(cached, cachedIni, "cached")
        } else {
            resolveActive() ?: return StartResult.Failure(
                reason = "WARP config resolve failed (auto-register unavailable)",
            )
        }
        val proxyIni = appendSocks5Inbound(resolved.ini, config.socksPort)
        return when (
            val r = sdkBridge.startProxy(
                TUNNEL_NAME,
                proxyIni,
                uapiPathProvider(),
                config.socksPort,
                socketProtector,
            )
        ) {
            WarpSdkBridge.ProxyResult.Success -> {
                resolvedConfig = resolved.config
                resolvedIni = proxyIni
                activeSocksPort = config.socksPort
                Log.i(TAG, "resolved proxy config: ${resolved.config} (iniSource=${resolved.iniSource})")
                StartResult.Success(socksPort = config.socksPort)
            }
            is WarpSdkBridge.ProxyResult.Failed -> StartResult.Failure(reason = r.reason)
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop - detaching tun")
        statsJobRef.getAndSet(null)?.cancel()
        connectedSinceRef.set(0L)
        _stats.value = EngineStats()
        savedTunFd = -1
        activeSocksPort = WARP_NO_SOCKS_PORT
        consecutiveRecoverFails = 0
        networkCallback?.let { cb ->
            networkCallback = null
            runCatching {
                (context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                    ?.unregisterNetworkCallback(cb)
            }.onFailure { PersistentLoggers.warn(TAG, "unregisterNetworkCallback failed: ${it.message}") }
        }
        sdkBridge.stopProxy()
        sdkBridge.detachTun()
        resolvedConfig = null
        resolvedIni = null
    }

    override suspend fun recover(): EnginePlugin.RecoverResult {
        val uapiPath = uapiPathProvider()
        val state = uapiStateReader(uapiPath, TUNNEL_NAME)
        if (state == null) {
            consecutiveRecoverFails++
            if (consecutiveRecoverFails < RECOVER_PASSIVE_ATTEMPTS) {
                PersistentLoggers.warn(
                    TAG,
                    "recover: UAPI unavailable - passive attempt " +
                        "$consecutiveRecoverFails/$RECOVER_PASSIVE_ATTEMPTS",
                )
                return EnginePlugin.RecoverResult.Failed("UAPI unavailable - handshake state unreadable")
            }
            return reattachAfterStale(uapiPath, "UAPI unavailable")
        }
        val ageS = state.handshakeAgeSeconds
        if (ageS != null && ageS < handshakeStaleThresholdSec) {
            consecutiveRecoverFails = 0
            PersistentLoggers.debug(TAG, "recover: handshake age=${ageS}s - OK, no action")
            return EnginePlugin.RecoverResult.Success
        }
        consecutiveRecoverFails++
        if (consecutiveRecoverFails < RECOVER_PASSIVE_ATTEMPTS) {
            PersistentLoggers.warn(
                TAG,
                "recover: handshake stale (age=$ageS) - passive attempt " +
                    "$consecutiveRecoverFails/$RECOVER_PASSIVE_ATTEMPTS",
            )
            return EnginePlugin.RecoverResult.Failed("handshake stale age=${ageS ?: "never"}s")
        }
        return reattachAfterStale(uapiPath, "handshake stale")
    }

    private suspend fun reattachAfterStale(uapiPath: String, reason: String): EnginePlugin.RecoverResult {
        val fd = savedTunFd
        val ini = resolvedIni
        if (fd < 0 || ini == null) {
            PersistentLoggers.warn(TAG, "recover: reattach impossible - fd=$fd ini=${ini != null}")
            return EnginePlugin.RecoverResult.Failed("$reason, reattach unavailable")
        }
        PersistentLoggers.warn(TAG, "recover: $reason - reattach attempt")
        statsJobRef.getAndSet(null)?.cancel()
        networkCallback?.let { cb ->
            networkCallback = null
            runCatching {
                (context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                    ?.unregisterNetworkCallback(cb)
            }
        }
        sdkBridge.detachTun()
        delay(RECOVER_REATTACH_DELAY_MS)
        val result = sdkBridge.attachTun(TUNNEL_NAME, fd, ini, uapiPath, socketProtector)
        if (result is WarpSdkBridge.AttachResult.Failed) {
            PersistentLoggers.error(TAG, "recover reattach failed: ${result.reason}")
            return EnginePlugin.RecoverResult.Failed("reattach failed: ${result.reason}")
        }
        startStatsPoll(uapiPath)
        registerNetworkCallback()
        val handshakeOk = withTimeoutOrNull(RECOVER_HANDSHAKE_WAIT_MS) {
            while (true) {
                val ready = runCatching {
                    handshakeChecker(uapiPath, TUNNEL_NAME)
                }.getOrDefault(false)
                if (ready) return@withTimeoutOrNull true
                delay(warpReadyPollMs)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        consecutiveRecoverFails = 0
        return if (handshakeOk) {
            PersistentLoggers.info(TAG, "recover: reattach success - handshake established")
            EnginePlugin.RecoverResult.Success
        } else {
            PersistentLoggers.warn(TAG, "recover: reattach done but handshake pending")
            EnginePlugin.RecoverResult.Success
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val socksPort = activeSocksPort
        if (socksPort > 0) {
            val reached = withTimeoutOrNull(warpReadyTimeoutMs) {
                while (true) {
                    val ready = runCatching {
                        Socks5HandshakeProbe.probe("127.0.0.1", socksPort, SOCKS_PROBE_TIMEOUT_MS)
                    }.isSuccess
                    if (ready) return@withTimeoutOrNull Unit
                    delay(warpReadyPollMs)
                }
            }
            return if (reached != null) {
                _stats.value = _stats.value.copy(
                    connectedSince = System.currentTimeMillis(),
                    activeConnections = 1,
                )
                EnginePlugin.ReadyResult.Ready
            } else {
                val reason = "WARP proxy SOCKS timeout ${warpReadyTimeoutMs}ms (127.0.0.1:$socksPort)"
                PersistentLoggers.warn(TAG, "awaitReady timeout - $reason")
                EnginePlugin.ReadyResult.Timeout(reason)
            }
        }
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
            _stats.value = _stats.value.copy(activeConnections = 1)
            EnginePlugin.ReadyResult.Ready
        } else {
            val state = runCatching { uapiStateReader(uapiPath, TUNNEL_NAME) }.getOrNull()
            val diag = if (state != null) {
                "rx=${state.rxBytes} tx=${state.txBytes} peers=${state.peersSeen} " +
                    "lastHsAge=${state.handshakeAgeSeconds ?: "never"}"
            } else {
                "uapi unreachable - tunnel handle invalid or socket missing; " +
                    "dirListing=${WarpSocketDiagnostics.listSocketCandidates(uapiPath)}"
            }
            val reason = "WARP: WireGuard handshake timeout ${warpReadyTimeoutMs}ms ($diag)"
            PersistentLoggers.warn(TAG, "awaitReady timeout - $reason - startup not ready")
            EnginePlugin.ReadyResult.Timeout(reason)
        }
    }

    override suspend fun probe(): ProbeResult {
        if (activeSocksPort > 0) return proxyProbe()
        return ProbeResult.Failure(reason = "WARP does not provide a SOCKS endpoint")
    }

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val port = activeSocksPort.takeIf { it > 0 } ?: socksPort.takeIf { it > 0 }
        if (port != null) return ExitNodeStrategy.ViaSocks("127.0.0.1", port)
        val connected = resolvedConfig?.peerEndpoint?.isNotBlank() == true
        return if (connected) {
            ExitNodeStrategy.ProviderLabel("Cloudflare WARP")
        } else {
            ExitNodeStrategy.Unavailable("WARP not connected")
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override fun preflight(): ru.ozero.enginescore.EnginePreflight =
        WarpPreflight(peerEndpointProvider = { resolvedConfig?.peerEndpoint })

    override fun peerWatchdogPolicy(): EnginePlugin.PeerWatchdogPolicy =
        EnginePlugin.PeerWatchdogPolicy(
            timeoutMs = WARP_PEER_WATCHDOG_TIMEOUT_MS,
            recoverBeforeFirstPeer = false,
        )

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
        val v6Addr = if (ipv6Allowed) {
            cfg.interfaceAddressV6.substringBefore('/').takeIf { it.isNotBlank() }
        } else {
            WARP_IPV6_BLACKHOLE_ADDRESS
        }
        val v6Prefix = cfg.interfaceAddressV6.substringAfter('/', missingDelimiterValue = "128")
            .toIntOrNull() ?: 128
        val allowedV4 = cfg.allowedIps.filter { it.isIpv4Cidr() }
        val allowedV6 = cfg.allowedIps.filter { it.isIpv6Cidr() && ipv6Allowed }
        val routeAllV4 = allowedV4.any { it.isFullTunnelV4() }
        val routeAllV6 = v6Addr != null && (!ipv6Allowed || allowedV6.any { it.isFullTunnelV6() })
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
            ipv6PrefixLength = if (ipv6Allowed) v6Prefix else WARP_IPV6_BLACKHOLE_PREFIX,
            excludeRfc1918 = false,
            routeAllV4 = routeAllV4,
            routeAllV6 = routeAllV6,
            routeCidrsV4 = if (routeAllV4) emptyList() else allowedV4,
            routeCidrsV6 = if (routeAllV6) emptyList() else allowedV6,
        )
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        val ini = resolvedIni ?: return TunAttachResult.Failure(
            reason = "attachTun before start - no ini config",
        )
        savedTunFd = tunFd
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
                            PersistentLoggers.trace(
                                TAG,
                                "warp stats handshakeRecent=$handshakeRecent activeConnections=${_stats.value.activeConnections}",
                            )
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
                                "warp UAPI null x$consecutiveNullReads - activeConnections=0 " +
                                    "(peer watchdog handles this after ${PEER_WATCHDOG_HINT_S}s)",
                            )
                        }
                        if (tick % STATS_LOG_EVERY == 0) {
                            PersistentLoggers.warn(
                                TAG,
                                "warp stats unavailable - UAPI socket read returned null " +
                                    "(uapi=$uapiPath/$TUNNEL_NAME) - handle invalid or socket path missing",
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
            val effectiveConfig = if (slot.endpointList.isNotEmpty()) {
                val probed = endpointProber.probe(slot.endpointList)
                val best = probed.firstOrNull { it.rttMs < Long.MAX_VALUE }?.endpoint
                    ?: slot.endpointList.first()
                slot.config.copy(peerEndpoint = best)
            } else {
                slot.config
            }
            buildResolved(effectiveConfig, slot.rawIniOverride, source = "slot")
        } else {
            PersistentLoggers.debug(TAG, "no active config - autoConfig.register")
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
                                Log.i(TAG, "auto-register duplicate - activated existing slot ${t.existingSlotId}")
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
        val iniConfig = if (!rawIni.isNullOrBlank()) {
            WarpConfParser.parse(baseIni).getOrNull()?.let { parsed ->
                parsed.copy(
                    publicKey = resolvedConfig.publicKey,
                    accountLicense = resolvedConfig.accountLicense,
                    doHProvider = resolvedConfig.doHProvider,
                )
            } ?: resolvedConfig
        } else {
            resolvedConfig
        }
        val ini = if (ipv6Allowed) baseIni else stripIpv6FromIni(baseIni)
        val iniSource = when {
            !rawIni.isNullOrBlank() -> "raw($source)"
            else -> "builder($source)"
        }
        return ResolvedWarp(config = iniConfig, ini = ini, iniSource = iniSource)
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
        val provider = cfg.doHProvider
        Log.i(TAG, "resolveEndpointHost host=$host provider=${provider.name}")
        for (attempt in 0..2) {
            val resolved = if (provider.isSystem) {
                withContext(Dispatchers.IO) {
                    runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()
                }
            } else {
                withContext(Dispatchers.IO) { resolveViaDoH(host, bootstrapSafeDohUrl(provider)) }
            }
            if (!resolved.isNullOrBlank()) {
                Log.i(TAG, "endpoint resolved $host -> $resolved via ${provider.name} (attempt ${attempt + 1})")
                return cfg.copy(peerEndpoint = "$resolved:$port")
            }
            if (attempt < 2) delay(200L shl attempt)
        }
        PersistentLoggers.warn(TAG, "endpoint resolve failed after 3 attempts for $host via ${provider.name}")
        return cfg
    }

    private fun bootstrapSafeDohUrl(provider: DoHProvider): String =
        provider.url.takeIf { provider.supportsJsonQueryApi() && isIpLiteralDohUrl(it) } ?: BOOTSTRAP_DOH_URL

    private fun DoHProvider.supportsJsonQueryApi(): Boolean = this !in setOf(
        DoHProvider.GOOGLE_8888,
        DoHProvider.GOOGLE_8844,
    )

    private fun isIpLiteralDohUrl(dohUrl: String): Boolean = runCatching {
        isLikelyIpAddress(java.net.URL(dohUrl).host)
    }.getOrDefault(false)

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

    private suspend fun proxyProbe(): ProbeResult =
        runCatching {
            Socks5HandshakeProbe.probe("127.0.0.1", activeSocksPort, SOCKS_PROBE_TIMEOUT_MS)
        }.fold(
            onSuccess = { ProbeResult.Success(it) },
            onFailure = { ProbeResult.Failure("WARP proxy probe failed: ${it.message}", it) },
        )

    private fun appendSocks5Inbound(ini: String, socksPort: Int): String {
        val trimmed = ini.trimEnd()
        return "$trimmed\n\n[Socks5]\nBindAddress = 127.0.0.1:$socksPort\n"
    }

    private fun String.isIpv4Cidr(): Boolean = '/' in this && ':' !in this

    private fun String.isIpv6Cidr(): Boolean = '/' in this && ':' in this

    private fun String.isFullTunnelV4(): Boolean = trim() == "0.0.0.0/0"

    private fun String.isFullTunnelV6(): Boolean = trim() == "::/0"

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
        const val BOOTSTRAP_DOH_URL = "https://1.1.1.1/dns-query"
        const val WARP_IPV6_BLACKHOLE_ADDRESS = "fd00::1"
        const val WARP_IPV6_BLACKHOLE_PREFIX = 128
        const val WARP_READY_TIMEOUT_MS = 30_000L
        const val WARP_PEER_WATCHDOG_TIMEOUT_MS = 30_000L
        const val WARP_READY_POLL_MS = 100L
        const val SOCKS_PROBE_TIMEOUT_MS = 300
        const val STATS_POLL_INTERVAL_MS = 5_000L
        const val HANDSHAKE_STALE_THRESHOLD_SEC = 180L
        const val STATS_LOG_EVERY = 5
        const val UAPI_NULL_DEGRADED_THRESHOLD = 3
        const val PEER_WATCHDOG_HINT_S = 30
        const val RECOVER_PASSIVE_ATTEMPTS = 2
        const val RECOVER_REATTACH_DELAY_MS = 500L
        const val RECOVER_HANDSHAKE_WAIT_MS = 5_000L
    }
}
