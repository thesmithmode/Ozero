package ru.ozero.enginesingbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Suppress("TooManyFunctions", "LargeClass")
class SingboxEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val profileDao: ProxyProfileDao,
    private val proxyChainDao: ProxyChainDao,
    private val onProcessDied: () -> Unit = {},
) : EnginePlugin, TunFdAcceptor {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal var routedProbe: SingboxRoutedProbe = SingboxHttp204RoutedProbe()

    @Volatile
    private var cachedBlob: ByteArray? = null

    @Volatile
    private var cachedSelectedProfileId: Long? = null

    @Volatile
    private var cachedAutoBlobs: List<ByteArray> = emptyList()

    @Volatile
    private var cachedProfilesById: Map<Long, ProxyProfile> = emptyMap()

    @Volatile
    private var cachedChainProfileIds: List<Long> = emptyList()

    @Volatile
    private var cachedDnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS

    @Volatile
    private var cachedIpv6Enabled: Boolean = false

    init {
        engineScope.launch {
            dataStore.data.collect { prefs ->
                cachedBlob = prefs[BEAN_KEY]
                cachedSelectedProfileId = prefs[SELECTED_PROFILE_KEY]
                cachedDnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.toList()?.ifEmpty { null }
                    ?: EngineConfig.Singbox.DEFAULT_DNS_SERVERS
            }
        }
        engineScope.launch {
            profileDao.getAutoCandidatesFlow(MAX_AUTO_PROFILE_SCAN).collect { profiles ->
                cachedProfilesById = profiles.associateBy { it.id }
                cachedAutoBlobs = autoSelectBlobWindow(profiles)
            }
        }
        engineScope.launch {
            proxyChainDao.getAllFlow().collect { steps ->
                cachedChainProfileIds = steps.map { it.profileId }
            }
        }
    }

    override val id = EngineId.SINGBOX

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = true,
        providesLocalSocks = true,
        providesLocalSocksWithoutUpstream = true,
    )

    @Volatile
    private var proxy: ISingboxEngineProcess? = null

    @Volatile
    private var serviceConn: ServiceConnection? = null

    @Volatile
    private var engineBinder: IBinder? = null

    @Volatile
    private var deathRecipient: IBinder.DeathRecipient? = null

    @Volatile
    private var pendingConfig: String? = null

    @Volatile
    private var pendingSocksPort: Int = 0

    @Volatile
    private var chainMode: Boolean = false

    @Volatile
    private var pendingTunAutoSelect: Boolean = false

    @Volatile
    private var activeTunAutoSelect: Boolean = false

    @Volatile
    private var activeSocksPort: Int = 0

    @Volatile
    private var activeAutoSelect: Boolean = false

    private val bindLock = Any()

    private val localProtector = object : ISingboxProtector.Stub() {
        override fun protect(fd: Int): Boolean = VpnSocketProtectorHolder.protect(fd)
    }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Singbox) { "SingboxEngine requires EngineConfig.Singbox" }

        chainMode = upstream !is Upstream.None || config.proxyMode
        PersistentLoggers.debug(
            TAG,
            "start: proxyMode=${config.proxyMode} upstream=${upstream::class.simpleName} " +
                "protocolType=${config.protocolType} autoCount=${config.autoSelectBeanBlobs.size} " +
                "chainCount=${config.chainBeanBlobs.size} " +
                "hasWireGuard=${config.wireGuardConfig != null} beanBytes=${config.beanBlob.size}",
        )
        if (config.proxyMode && upstream is Upstream.None) {
            return startProxyMode(config, upstream = null)
        }
        if (chainMode) {
            return when (upstream) {
                is Upstream.Socks5 -> startChainMode(config, upstream)
                else -> StartResult.Failure("SingboxEngine chain requires Socks5, got $upstream")
            }
        }

        activeSocksPort = 0
        activeAutoSelect = config.autoSelectBeanBlobs.isNotEmpty()
        pendingSocksPort = 0
        pendingConfig = null
        val probePort = allocateChainPort()
        val json = buildPendingConfig(config, probePort) ?: run {
            activeAutoSelect = false
            return StartResult.Failure("failed to build sing-box config")
        }
        PersistentLoggers.debug(
            TAG,
            "start TUN config built probePort=$probePort fingerprint=${json.singboxConfigFingerprint()} len=${json.length}",
        )

        bindOrFail()?.let {
            clearPendingStart()
            return it
        }

        pendingConfig = json
        pendingSocksPort = probePort
        pendingTunAutoSelect = config.autoSelectBeanBlobs.isNotEmpty()
        return StartResult.Success(socksPort = 0)
    }

    private fun buildPendingConfig(config: EngineConfig.Singbox, probeSocksPort: Int): String? {
        if (config.autoSelectBeanBlobs.isNotEmpty()) {
            val beans = supportedBeans(config.autoSelectBeanBlobs.take(MAX_AUTO_SELECT_OUTBOUNDS))
            if (beans.isEmpty()) {
                PersistentLoggers.warn(TAG, "auto-select: all ${config.autoSelectBeanBlobs.size} beans failed")
                return null
            }
            return runCatching {
                ConfigBuilder.buildSingboxAutoConfig(beans, probeSocksPort, config.dnsServers, config.ipv6Enabled)
            }
                .onFailure { PersistentLoggers.warn(TAG, "buildSingboxAutoConfig: ${it.message}") }
                .getOrNull()
        }
        val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(config.beanBlob) }
            .onFailure { PersistentLoggers.warn(TAG, "beanBlob deserialize: ${it.message}") }
            .getOrNull() ?: return null
        val wrappers = chainWrapperBeans(config)
        if (!ConfigBuilder.isSupportedBean(bean) || !bean.hasRoutableServerAddress()) {
            val fallbackBeans = supportedBeans(cachedAutoBlobs).take(MAX_AUTO_SELECT_OUTBOUNDS)
            if (fallbackBeans.isEmpty()) {
                PersistentLoggers.warn(TAG, "selected bean unsupported and no supported fallback profiles")
                return null
            }
            return runCatching {
                ConfigBuilder.buildSingboxAutoConfig(
                    fallbackBeans,
                    probeSocksPort,
                    config.dnsServers,
                    config.ipv6Enabled,
                )
            }
                .onFailure { PersistentLoggers.warn(TAG, "build fallback auto config: ${it.message}") }
                .getOrNull()
        }
        return runCatching {
            ConfigBuilder.buildSingboxConfig(
                bean,
                probeSocksPort,
                config.dnsServers,
                config.ipv6Enabled
            )
        }
            .mapCatching {
                if (wrappers.isNotEmpty()) {
                    ConfigBuilder.buildProfileChainConfig(
                        bean,
                        wrappers,
                        probeSocksPort,
                        config.dnsServers,
                        config.ipv6Enabled
                    )
                } else {
                    it
                }
            }
            .onFailure { PersistentLoggers.warn(TAG, "buildSingboxConfig: ${it.message}") }
            .getOrNull()
    }

    private suspend fun startChainMode(config: EngineConfig.Singbox, upstream: Upstream.Socks5): StartResult {
        val configUpstream = ConfigBuilder.Upstream(upstream.host, upstream.port)
        return startProxyMode(config, configUpstream)
    }

    @Suppress("ReturnCount")
    private suspend fun startProxyMode(
        config: EngineConfig.Singbox,
        upstream: ConfigBuilder.Upstream?,
    ): StartResult {
        activeSocksPort = 0
        activeAutoSelect = false
        activeTunAutoSelect = false
        pendingSocksPort = 0
        pendingConfig = null
        val port = allocateChainPort()
        val json = if (config.autoSelectBeanBlobs.isNotEmpty()) {
            val beans = supportedBeans(config.autoSelectBeanBlobs.take(MAX_AUTO_SELECT_OUTBOUNDS))
            if (beans.isEmpty()) return StartResult.Failure("chain auto-select: no valid beans")
            runCatching {
                ConfigBuilder.buildAutoChainConfig(
                    beans,
                    port,
                    upstream,
                    config.dnsServers,
                    config.ipv6Enabled,
                )
            }
                .getOrElse { return StartResult.Failure("chain auto config: ${it.message}") }
        } else if (config.wireGuardConfig != null) {
            val wgConfig = requireNotNull(config.wireGuardConfig)
            runCatching {
                ConfigBuilder.buildWireGuardChainConfig(
                    wgConfig,
                    port,
                    upstream,
                    config.dnsServers,
                    config.ipv6Enabled,
                )
            }
                .getOrElse { return StartResult.Failure("chain WG config: ${it.message}") }
        } else {
            val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(config.beanBlob) }
                .getOrElse { return StartResult.Failure("chain deserialize: ${it.message}") }
            if (!ConfigBuilder.isSupportedBean(bean) || !bean.hasRoutableServerAddress()) {
                val fallbackBeans = supportedBeans(cachedAutoBlobs).take(MAX_AUTO_SELECT_OUTBOUNDS)
                if (fallbackBeans.isEmpty()) return StartResult.Failure("chain selected transport unsupported")
                runCatching {
                    ConfigBuilder.buildAutoChainConfig(
                        fallbackBeans,
                        port,
                        upstream,
                        config.dnsServers,
                        config.ipv6Enabled,
                    )
                }
                    .getOrElse { return StartResult.Failure("chain fallback auto config: ${it.message}") }
            } else {
                val wrappers = if (upstream == null) chainWrapperBeans(config) else emptyList()
                runCatching {
                    if (wrappers.isNotEmpty()) {
                        ConfigBuilder.buildProfileChainProxyConfig(
                            bean,
                            wrappers,
                            port,
                            config.dnsServers,
                            config.ipv6Enabled,
                        )
                    } else {
                        ConfigBuilder.buildChainConfig(
                            bean,
                            port,
                            upstream,
                            config.dnsServers,
                            config.ipv6Enabled,
                        )
                    }
                }
                    .getOrElse { return StartResult.Failure("chain config: ${it.message}") }
            }
        }
        PersistentLoggers.debug(
            TAG,
            "startProxyMode config built port=$port upstream=${upstream != null} " +
                "fingerprint=${json.singboxConfigFingerprint()} len=${json.length}",
        )

        bindOrFail()?.let {
            activeSocksPort = 0
            return it
        }

        val p = proxy ?: return StartResult.Failure("SingboxEngineService not connected for chain mode")
        var runtimeStarted = false
        return runCatching {
            p.startProxyMode(json, localProtector)
            runtimeStarted = true
            val runtimeRunning = runCatching { p.runtimeRunning() }.getOrDefault(false)
            PersistentLoggers.debug(
                TAG,
                "startProxyMode AIDL returned port=$port runtimeRunning=$runtimeRunning",
            )
            if (!runtimeRunning) {
                activeSocksPort = 0
                stopRuntimeAfterFailedReadiness(p)
                return StartResult.Failure("sing-box proxy runtime failed to start")
            }
            activeSocksPort = port
            PersistentLoggers.info(TAG, "startProxyMode sent over AIDL port=$port")
            StartResult.Success(socksPort = port)
        }.getOrElse {
            activeSocksPort = 0
            if (runtimeStarted) stopRuntimeAfterFailedReadiness(p)
            if (it is CancellationException) throw it
            PersistentLoggers.error(TAG, "startProxyMode AIDL call failed: ${it.message}", it)
            StartResult.Failure("startProxyMode failed: ${it.message}")
        }
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        if (chainMode) return TunAttachResult.Failure("chain mode - TUN not used")
        val json = pendingConfig ?: return TunAttachResult.Failure("attachTun called before start()")
        val p = proxy ?: run {
            clearPendingStart()
            return TunAttachResult.Failure("SingboxEngineService not connected")
        }
        var runtimeStarted = false
        return runCatching {
            val pfd = ParcelFileDescriptor.fromFd(tunFd)
            try {
                PersistentLoggers.debug(
                    TAG,
                    "attachTun start rawFd=$tunFd pendingPort=$pendingSocksPort " +
                        "fingerprint=${json.singboxConfigFingerprint()} len=${json.length}",
                )
                p.startWithConfig(pfd, json, localProtector)
                runtimeStarted = true
            } finally {
                runCatching { pfd.close() }
            }
            delay(150)
            val runtimeRunning = runCatching { p.runtimeRunning() }.getOrDefault(false)
            PersistentLoggers.debug(
                TAG,
                "attachTun AIDL returned rawFd=$tunFd pendingPort=$pendingSocksPort runtimeRunning=$runtimeRunning",
            )
            if (!runtimeRunning) {
                stopRuntimeAfterFailedReadiness(p)
                clearPendingStart()
                return TunAttachResult.Failure("sing-box runtime failed to start")
            }
            val autoSelect = pendingTunAutoSelect
            val routedReady = warmTrafficProbe(pendingSocksPort, autoSelect)
            if (!routedReady && !autoSelect) {
                stopRuntimeAfterFailedReadiness(p)
                clearPendingStart()
                return TunAttachResult.Failure("sing-box routed probe failed")
            }
            activeSocksPort = pendingSocksPort
            activeTunAutoSelect = autoSelect
            pendingTunAutoSelect = false
            pendingSocksPort = 0
            pendingConfig = null
            PersistentLoggers.debug(TAG, "startWithConfig sent over AIDL")
            TunAttachResult.Success
        }.getOrElse {
            if (runtimeStarted) stopRuntimeAfterFailedReadiness(p)
            clearPendingStart()
            if (it is CancellationException) throw it
            PersistentLoggers.error(TAG, "startWithConfig AIDL call failed: ${it.message}", it)
            TunAttachResult.Failure("startWithConfig AIDL call failed: ${it.message}")
        }
    }

    private suspend fun warmTrafficProbe(socksPort: Int, autoSelect: Boolean): Boolean {
        if (socksPort <= 0) return false
        repeat(READY_PROBE_ATTEMPTS) { attempt ->
            val latency = routedProbe.probeLatencyMs(socksPort)
            if (latency >= 0) return true
            PersistentLoggers.debug(
                TAG,
                "routed probe warmup failed attempt=${attempt + 1}/$READY_PROBE_ATTEMPTS autoSelect=$autoSelect",
            )
            if (attempt != READY_PROBE_ATTEMPTS - 1) delay(READY_PROBE_RETRY_MS)
        }
        PersistentLoggers.warn(TAG, "routed probe warmup failed port=$socksPort autoSelect=$autoSelect")
        return false
    }

    private fun stopRuntimeAfterFailedReadiness(p: ISingboxEngineProcess) {
        runCatching {
            val stopped = p.stopAndWait(REMOTE_STOP_TIMEOUT_MS)
            if (!stopped) PersistentLoggers.warn(TAG, "stop after routed probe failure timed out")
        }.onFailure { PersistentLoggers.warn(TAG, "stop after routed probe failure failed: ${it.message}") }
    }

    override suspend fun stop() {
        pendingConfig = null
        pendingTunAutoSelect = false
        pendingSocksPort = 0
        chainMode = false
        activeAutoSelect = false
        activeTunAutoSelect = false
        activeSocksPort = 0
        val p = proxy
        if (p != null) {
            runCatching {
                val stopped = p.stopAndWait(REMOTE_STOP_TIMEOUT_MS)
                if (!stopped) PersistentLoggers.warn(TAG, "proxy.stopAndWait() timed out")
            }.onFailure { PersistentLoggers.warn(TAG, "proxy.stopAndWait() failed: ${it.message}") }
        }
        close()
    }

    override fun stopTimeoutMs(): Long = ENGINE_STOP_TIMEOUT_MS

    override suspend fun probe(): ProbeResult {
        return probeInternal(clearOnRoutedFailure = true)
    }

    private suspend fun probeInternal(clearOnRoutedFailure: Boolean): ProbeResult {
        val p = proxy ?: return ProbeResult.Failure("sing-box process is not connected")
        val port = activeSocksPort.takeIf { it > 0 }
            ?: return ProbeResult.Failure("sing-box SOCKS probe port is not active")
        PersistentLoggers.debug(TAG, "probe start port=$port chainMode=$chainMode")
        val runtimeRunning = runCatching { p.runtimeRunning() }.getOrElse {
            clearRuntimeState()
            return ProbeResult.Failure("sing-box runtime health check failed: ${it.message}", it)
        }
        if (!runtimeRunning) {
            clearRuntimeState()
            return ProbeResult.Failure("sing-box runtime is not running")
        }
        val latency = routedProbe.probeLatencyMs(port)
        return if (latency >= 0) {
            ProbeResult.Success(latencyMs = latency)
        } else {
            PersistentLoggers.warn(
                TAG,
                "probe failed: routed probe returned $latency port=$port " +
                    "chainMode=$chainMode runtimeRunning=$runtimeRunning",
            )
            if (clearOnRoutedFailure) {
                activeSocksPort = 0
                activeTunAutoSelect = false
            }
            ProbeResult.Failure("sing-box routed probe failed")
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val autoSelectRuntime = activeAutoSelect || activeTunAutoSelect
        return awaitRoutedReady(clearAutoSelect = autoSelectRuntime)
    }

    private suspend fun awaitRoutedReady(clearAutoSelect: Boolean): EnginePlugin.ReadyResult {
        var lastFailure: ProbeResult.Failure? = null
        repeat(READY_PROBE_ATTEMPTS) { attempt ->
            when (val result = probeInternal(clearOnRoutedFailure = false)) {
                is ProbeResult.Success -> return EnginePlugin.ReadyResult.Ready
                is ProbeResult.Failure -> {
                    lastFailure = result
                    PersistentLoggers.debug(
                        TAG,
                        "awaitReady probe failed attempt=${attempt + 1}/$READY_PROBE_ATTEMPTS reason=${result.reason}",
                    )
                    if (attempt != READY_PROBE_ATTEMPTS - 1) delay(READY_PROBE_RETRY_MS)
                }
            }
        }
        activeSocksPort = 0
        activeTunAutoSelect = false
        if (clearAutoSelect) activeAutoSelect = false
        return EnginePlugin.ReadyResult.Timeout(lastFailure?.reason ?: "sing-box routed probe failed")
    }

    override fun stats(): Flow<EngineStats> = flow {
        while (true) {
            val p = proxy
            if (p != null) {
                val s = runCatching { p.stats }.getOrNull()
                if (s != null) {
                    emit(
                        EngineStats(
                            bytesIn = s.rxTotal,
                            bytesOut = s.txTotal,
                            activeConnections = s.activeConnections,
                        ),
                    )
                }
            }
            delay(STATS_POLL_MS)
        }
    }

    override suspend fun tunSpec(): TunSpec = TunSpec(
        sessionName = "Sing-box",
        mtu = 9000,
        blocking = false,
        ipv4Address = "172.19.0.1",
        ipv4PrefixLength = 30,
        dnsServers = effectiveDnsServers(),
        allowFamilyV4 = true,
        allowFamilyV6 = true,
        ipv6Address = "fdfe:dcba:9876::1",
        ipv6PrefixLength = 126,
        routeAllV4 = true,
        routeAllV6 = true,
    )

    private fun effectiveDnsServers(): List<String> =
        cachedDnsServers
            .ifEmpty { EngineConfig.Singbox.DEFAULT_DNS_SERVERS }
            .filter { cachedIpv6Enabled || !it.isPlainIpv6Address() }

    private fun String.isPlainIpv6Address(): Boolean =
        ':' in this && !startsWith("https://") && !startsWith("tls://")

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val port = activeSocksPort.takeIf { it > 0 }
        return if (port != null) {
            ExitNodeStrategy.ViaSocks("127.0.0.1", port)
        } else {
            ExitNodeStrategy.Unavailable("sing-box SOCKS probe unavailable")
        }
    }

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? {
        val ipv6Enabled = settings?.ipv6Enabled ?: false
        cachedIpv6Enabled = ipv6Enabled
        if (cachedSelectedProfileId == SELECTED_AUTO) {
            val blobs = cachedAutoBlobs.ifEmpty {
                runBlocking(Dispatchers.IO) {
                    profileDao.getAutoCandidatesFlow(MAX_AUTO_PROFILE_SCAN).first()
                }
                    .let { profiles -> autoSelectBlobWindow(profiles) }
            }
            if (blobs.isEmpty()) return null
            return EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = blobs,
                dnsServers = cachedDnsServers,
                ipv6Enabled = ipv6Enabled,
            )
        }
        val selectedProfile = cachedSelectedProfileId
            ?.takeIf { it != SELECTED_AUTO }
            ?.let { cachedProfilesById[it] ?: resolveProfileByIdBlocking(it) }
        val blob = selectedProfile?.beanBlob
            ?: cachedBlob
            ?: return null
        val type = runCatching {
            protocolTypeOf(KryoSerializer.deserialize<AbstractBean>(blob))
        }.getOrDefault(PROTOCOL_VLESS)
        return EngineConfig.Singbox(
            beanBlob = blob,
            protocolType = type,
            chainBeanBlobs = chainWrapperBlobs(cachedSelectedProfileId),
            dnsServers = cachedDnsServers,
            ipv6Enabled = ipv6Enabled,
        )
    }

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? =
        buildManualConfig(settings)?.let { it as? EngineConfig.Singbox }?.copy(proxyMode = true)

    private fun chainWrapperBlobs(selectedProfileId: Long?): List<ByteArray> {
        val selected = selectedProfileId ?: return emptyList()
        if (selected == SELECTED_AUTO) return emptyList()
        return chainProfileIdsBlocking()
            .filter { it != selected }
            .mapNotNull { id -> cachedProfilesById[id]?.beanBlob ?: resolveProfileByIdBlocking(id)?.beanBlob }
    }

    private fun chainWrapperBeans(config: EngineConfig.Singbox): List<AbstractBean> =
        config.chainBeanBlobs.mapNotNull { blob ->
            runCatching { KryoSerializer.deserialize<AbstractBean>(blob) }
                .onFailure { PersistentLoggers.warn(TAG, "chain bean deserialize: ${it.message}") }
                .getOrNull()
                ?.takeIf { ConfigBuilder.isSupportedBean(it) && it.hasRoutableServerAddress() }
        }

    private fun supportedBeans(blobs: List<ByteArray>): List<AbstractBean> =
        blobs.mapNotNull { blob ->
            runCatching { KryoSerializer.deserialize<AbstractBean>(blob) }
                .onFailure { e -> PersistentLoggers.warn(TAG, "bean deserialize: ${e.message}") }
                .getOrNull()
                ?.takeIf { ConfigBuilder.isSupportedBean(it) && it.hasRoutableServerAddress() }
        }

    private fun isSupportedRoutableBlob(blob: ByteArray): Boolean =
        runCatching { KryoSerializer.deserialize<AbstractBean>(blob) }
            .getOrNull()
            ?.let { ConfigBuilder.isSupportedBean(it) && it.hasRoutableServerAddress() }
            ?: false

    private fun autoSelectBlobWindow(profiles: List<ProxyProfile>): List<ByteArray> =
        profiles
            .filter { isSupportedRoutableBlob(it.beanBlob) }
            .let { supported -> prioritizeSingboxAutoProfiles(supported, MAX_AUTO_SELECT_OUTBOUNDS) }
            .map { it.beanBlob }

    private fun resolveProfileByIdBlocking(id: Long): ProxyProfile? =
        runBlocking(Dispatchers.IO) { profileDao.getById(id) }

    private fun chainProfileIdsBlocking(): List<Long> =
        cachedChainProfileIds.ifEmpty {
            runBlocking(Dispatchers.IO) {
                proxyChainDao.getAll().map { it.profileId }
            }
        }

    private fun AbstractBean.hasRoutableServerAddress(): Boolean {
        val host = serverAddress.trim().trim('[', ']').lowercase()
        return host.isNotEmpty() &&
            host != "localhost" &&
            host != "0.0.0.0" &&
            host != "::" &&
            host != "::0" &&
            host != "::1" &&
            !host.startsWith("127.")
    }

    private fun protocolTypeOf(bean: AbstractBean): Int = when (bean) {
        is VLESSBean -> PROTOCOL_VLESS
        is VMessBean -> PROTOCOL_VMESS
        is TrojanBean -> PROTOCOL_TROJAN
        is ShadowsocksBean -> PROTOCOL_SHADOWSOCKS
        else -> PROTOCOL_VLESS
    }

    private fun bindOrFail(): StartResult.Failure? {
        synchronized(bindLock) {
            if (proxy != null) return null
            serviceConn?.let { runCatching { context.unbindService(it) } }
            unlinkDeath()
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    proxy = ISingboxEngineProcess.Stub.asInterface(binder)
                    engineBinder = binder
                    val recipient = IBinder.DeathRecipient {
                        proxy = null
                        engineBinder = null
                        clearRuntimeState()
                        val ref = serviceConn
                        serviceConn = null
                        if (ref != null) runCatching { context.unbindService(ref) }
                        PersistentLoggers.warn(TAG, "SingboxEngineService binder died — :engine_singbox crash")
                        runCatching { onProcessDied() }
                    }
                    deathRecipient = recipient
                    runCatching { binder.linkToDeath(recipient, 0) }
                    latch.countDown()
                    PersistentLoggers.debug(TAG, "SingboxEngineService connected")
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    proxy = null
                    engineBinder = null
                    clearRuntimeState()
                    unlinkDeath()
                    val ref = serviceConn
                    serviceConn = null
                    if (ref != null) runCatching { context.unbindService(ref) }
                    PersistentLoggers.warn(TAG, "SingboxEngineService disconnected — system unbind")
                    runCatching { onProcessDied() }
                }

                override fun onBindingDied(name: ComponentName?) {
                    proxy = null
                    engineBinder = null
                    clearRuntimeState()
                    unlinkDeath()
                    val ref = serviceConn
                    serviceConn = null
                    if (ref != null) runCatching { context.unbindService(ref) }
                    PersistentLoggers.warn(TAG, "SingboxEngineService binding died")
                    runCatching { onProcessDied() }
                }
            }
            val component = ComponentName(context, "ru.ozero.singboxprocess.SingboxEngineService")
            val intent = Intent().apply { this.component = component }
            val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            if (!bound) {
                runCatching { context.unbindService(conn) }
                return StartResult.Failure("bindService failed for SingboxEngineService — registered in manifest?")
            }
            serviceConn = conn
            if (!latch.await(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)) {
                runCatching { context.unbindService(conn) }
                serviceConn = null
                return StartResult.Failure("SingboxEngineService bind timeout after ${CONNECT_TIMEOUT_S}s")
            }
            return if (proxy == null) {
                runCatching { context.unbindService(conn) }
                serviceConn = null
                StartResult.Failure("SingboxEngineService proxy null after bind")
            } else {
                null
            }
        }
    }

    private fun clearPendingStart() {
        clearRuntimeState()
    }

    private fun clearRuntimeState() {
        pendingConfig = null
        pendingTunAutoSelect = false
        pendingSocksPort = 0
        activeAutoSelect = false
        activeTunAutoSelect = false
        activeSocksPort = 0
    }

    private fun close() {
        synchronized(bindLock) {
            unlinkDeath()
            proxy = null
            serviceConn?.let { runCatching { context.unbindService(it) } }
            serviceConn = null
        }
    }

    private fun unlinkDeath() {
        val b = engineBinder
        val r = deathRecipient
        if (b != null && r != null) {
            runCatching { b.unlinkToDeath(r, 0) }
        }
        engineBinder = null
        deathRecipient = null
    }

    private fun allocateChainPort(): Int {
        val offset = chainPortCounter.getAndIncrement() % CHAIN_PORT_RANGE
        return CHAIN_PORT_BASE + offset
    }

    companion object {
        private const val TAG = "SingboxEngine"
        private const val CONNECT_TIMEOUT_S = 5L
        private const val STATS_POLL_MS = 1_000L
        private const val REMOTE_STOP_TIMEOUT_MS = 3_000L
        private const val ENGINE_STOP_TIMEOUT_MS = 4_000L
        private const val READY_PROBE_ATTEMPTS = 5
        private const val READY_PROBE_RETRY_MS = 500L
        private const val MAX_AUTO_SELECT_OUTBOUNDS = 50
        private const val MAX_AUTO_PROFILE_SCAN = 2_000
        private const val CHAIN_PORT_BASE = 49408
        private const val CHAIN_PORT_RANGE = 256
        private val chainPortCounter = java.util.concurrent.atomic.AtomicInteger(0)
        private val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        private val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        val SINGBOX_DNS_SERVERS_KEY = stringSetPreferencesKey("singbox_dns_servers")
        const val SELECTED_AUTO = -1L
        const val PROTOCOL_AUTO_SELECT = -1
        const val PROTOCOL_VLESS = 0
        const val PROTOCOL_VMESS = 1
        const val PROTOCOL_TROJAN = 2
        const val PROTOCOL_SHADOWSOCKS = 3
    }
}
