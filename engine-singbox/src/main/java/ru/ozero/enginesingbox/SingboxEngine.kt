package ru.ozero.enginesingbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.app.ActivityManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
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
import kotlinx.coroutines.withContext
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
import ru.ozero.singboxconfig.BeanSupportDecision
import ru.ozero.singboxconfig.BeanSupportError
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxconfig.PersistedProfileRecovery
import ru.ozero.singboxconfig.RecoveryResult
import ru.ozero.singboxconfig.RecoveryFailureCategory
import ru.ozero.singboxconfig.PersistedProtocol
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.protocolLabel
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal enum class BuildConfigFailureCategory {
    DESERIALIZATION,
    CANONICALIZATION,
    UNSUPPORTED_PROFILE,
    NO_SUPPORTED_AUTO_PROFILE,
    GENERATION,
    DECODE_FAILED,
    MIGRATION_AMBIGUOUS,
    PROTOCOL_MISMATCH,
    INVALID_REQUIRED_FIELDS,
    CONFIG_GENERATION_FAILED,
    LIBBOX_CONFIG_REJECTED,
}

internal sealed interface BuildConfigResult {
    data class Success(
        val json: String,
        val inputFailures: List<ProfileInputFailure> = emptyList(),
    ) : BuildConfigResult

    data class Failure(
        val category: BuildConfigFailureCategory,
        val reason: BeanSupportError? = null,
        val inputFailures: List<ProfileInputFailure> = emptyList(),
        val exceptionClass: String? = null,
    ) : BuildConfigResult {
        fun stableReason(): String = reason?.let { "profile rejected: $it" } ?: "config failed: $category"
    }
}

private fun RecoveryFailureCategory.toBuildConfigFailureCategory(): BuildConfigFailureCategory = when (this) {
    RecoveryFailureCategory.DECODE_FAILED -> BuildConfigFailureCategory.DECODE_FAILED
    RecoveryFailureCategory.MIGRATION_AMBIGUOUS -> BuildConfigFailureCategory.MIGRATION_AMBIGUOUS
    RecoveryFailureCategory.PROTOCOL_MISMATCH -> BuildConfigFailureCategory.PROTOCOL_MISMATCH
    RecoveryFailureCategory.INVALID_REQUIRED_FIELDS -> BuildConfigFailureCategory.INVALID_REQUIRED_FIELDS
    RecoveryFailureCategory.UNSUPPORTED_PROFILE -> BuildConfigFailureCategory.UNSUPPORTED_PROFILE
    RecoveryFailureCategory.CONFIG_GENERATION_FAILED -> BuildConfigFailureCategory.CONFIG_GENERATION_FAILED
    RecoveryFailureCategory.LIBBOX_CONFIG_REJECTED -> BuildConfigFailureCategory.LIBBOX_CONFIG_REJECTED
}

internal enum class ProfileInputStage {
    MISSING_PROFILE,
    SIZE,
    DESERIALIZATION,
    CANONICALIZATION,
    VALIDATION,
}

internal data class ProfileInputFailure(
    val index: Int,
    val stage: ProfileInputStage,
    val profileId: Long? = null,
    val reason: BeanSupportError? = null,
    val exceptionClass: String? = null,
)

private data class DecodedProfiles(
    val beans: List<AbstractBean>,
    val failures: List<ProfileInputFailure>,
)

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
    private var cachedAutoProfiles: List<ProxyProfile> = emptyList()

    @Volatile
    private var cachedProfilesById: Map<Long, ProxyProfile> = emptyMap()

    @Volatile
    private var cachedChainProfileIds: List<Long> = emptyList()

    @Volatile
    private var cachedDnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS

    @Volatile
    private var cachedIpv6Enabled: Boolean = false

    @Volatile
    private var preferencesCacheInitialized: Boolean = false

    init {
        engineScope.launch {
            dataStore.data.collect { prefs ->
                val savedBlob = prefs[BEAN_KEY]
                cachedBlob = savedBlob
                cachedSelectedProfileId = prefs[SELECTED_PROFILE_KEY]
                cachedDnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.toList()?.ifEmpty { null }
                    ?: EngineConfig.Singbox.DEFAULT_DNS_SERVERS
                preferencesCacheInitialized = true
            }
        }
        engineScope.launch {
            profileDao.getAutoCandidatesFlow(MAX_AUTO_PROFILE_SCAN).collect { profiles ->
                val migratedProfiles = buildList {
                    profiles.forEach { add(migrateProfileBlob(it)) }
                }
                cachedProfilesById = migratedProfiles.associateBy { it.id }
                cachedAutoProfiles = autoSelectProfileWindow(migratedProfiles)
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
    private var engineProcessId: Int = -1

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
        override fun protect(socket: ParcelFileDescriptor): Boolean = socket.use {
            val result = VpnSocketProtectorHolder.protect(it.fd)
            PersistentLoggers.debug(
                TAG,
                "protect request sourcePid=${Binder.getCallingPid()} targetPid=${Process.myPid()} " +
                    "targetReceivedFd=${it.fd} result=$result",
            )
            result
        }
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
        val json = when (val result = buildPendingConfig(config, probePort)) {
            is BuildConfigResult.Success -> result.json
            is BuildConfigResult.Failure -> {
                activeAutoSelect = false
                PersistentLoggers.warn(
                    TAG,
                    "singbox config rejected profileId=${cachedSelectedProfileId ?: "unknown"} " +
                        "category=${result.category} reason=${result.reason ?: "none"} " +
                        "exceptionClass=${result.exceptionClass ?: "none"} " +
                        "inputs=${result.inputFailures.failureCounts()}",
                )
                return StartResult.Failure(result.stableReason())
            }
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

    private fun buildPendingConfig(config: EngineConfig.Singbox, probeSocksPort: Int): BuildConfigResult {
        if (config.autoSelectBeanBlobs.isNotEmpty()) {
            val decoded = decodeProfiles(
                config.autoSelectBeanBlobs,
                config.autoSelectProfileIds,
                "auto-select",
                enforceSizeLimit = true,
            )
            if (decoded.beans.isEmpty()) {
                return BuildConfigResult.Failure(
                    BuildConfigFailureCategory.NO_SUPPORTED_AUTO_PROFILE,
                    inputFailures = decoded.failures,
                )
            }
            return runCatching {
                ConfigBuilder.buildSingboxAutoConfig(
                    decoded.beans.take(MAX_AUTO_SELECT_OUTBOUNDS),
                    probeSocksPort,
                    config.dnsServers,
                    config.ipv6Enabled,
                )
            }
                .fold(
                    onSuccess = { BuildConfigResult.Success(it, decoded.failures) },
                    onFailure = {
                        BuildConfigResult.Failure(
                            BuildConfigFailureCategory.GENERATION,
                            inputFailures = decoded.failures,
                            exceptionClass = it.safeExceptionClass(),
                        )
                    },
                )
        }
        val recovery = PersistedProfileRecovery.recover(config.beanBlob, config.protocolType)
        val bean = when (recovery) {
            is RecoveryResult.Success -> recovery.bean
            is RecoveryResult.Failure -> {
                return BuildConfigResult.Failure(
                    recovery.category.toBuildConfigFailureCategory(),
                    recovery.supportError,
                )
            }
        }
        val wrappers = decodeProfiles(
            config.chainBeanBlobs,
            config.chainProfileIds,
            "chain",
            enforceSizeLimit = false,
            missingProfileIds = config.missingChainProfileIds,
        )
        if (wrappers.failures.isNotEmpty()) {
            return BuildConfigResult.Failure(
                BuildConfigFailureCategory.UNSUPPORTED_PROFILE,
                inputFailures = wrappers.failures,
            )
        }
        val canonicalBean = runCatching { ConfigBuilder.canonicalBean(bean) }
            .getOrElse {
                return BuildConfigResult.Failure(
                    BuildConfigFailureCategory.CANONICALIZATION,
                    exceptionClass = it.safeExceptionClass(),
                )
            }
        val decision = ConfigBuilder.supportDecisionCanonical(canonicalBean)
        if (decision is BeanSupportDecision.Unsupported) {
            logRejectedProfile(null, canonicalBean.value, decision, "selected")
            return BuildConfigResult.Failure(BuildConfigFailureCategory.UNSUPPORTED_PROFILE, decision.error)
        }
        logCanonicalProfileSummary(cachedSelectedProfileId, canonicalBean.value)
        return runCatching {
            ConfigBuilder.buildSingboxConfigFromCanonical(
                canonicalBean,
                probeSocksPort,
                config.dnsServers,
                config.ipv6Enabled
            )
        }
            .mapCatching {
                if (wrappers.beans.isNotEmpty()) {
                    ConfigBuilder.buildProfileChainConfigFromCanonical(
                        canonicalBean,
                        wrappers.beans,
                        probeSocksPort,
                        config.dnsServers,
                        config.ipv6Enabled
                    )
                } else {
                    it
                }
            }
            .fold(
                onSuccess = { BuildConfigResult.Success(it) },
                onFailure = {
                    BuildConfigResult.Failure(
                        BuildConfigFailureCategory.GENERATION,
                        exceptionClass = it.safeExceptionClass(),
                    )
                },
            )
    }

    private suspend fun startChainMode(config: EngineConfig.Singbox, upstream: Upstream.Socks5): StartResult {
        val configUpstream = ConfigBuilder.Upstream(upstream.host, upstream.port)
        return startProxyMode(config, configUpstream)
    }

    @Suppress("ReturnCount", "LongMethod")
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
        val proxyAutoSelect = config.autoSelectBeanBlobs.isNotEmpty()
        val json = if (proxyAutoSelect) {
            val decoded = decodeProfiles(
                config.autoSelectBeanBlobs,
                config.autoSelectProfileIds,
                "chain auto-select",
                enforceSizeLimit = true,
            )
            if (decoded.beans.isEmpty()) return StartResult.Failure("chain auto-select rejected")
            runCatching {
                ConfigBuilder.buildAutoChainConfig(
                    decoded.beans.take(MAX_AUTO_SELECT_OUTBOUNDS),
                    port,
                    upstream,
                    config.dnsServers,
                    config.ipv6Enabled,
                )
            }
                .getOrElse {
                    logConfigException("chain auto generation", it)
                    return StartResult.Failure("chain auto generation failed")
                }
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
                .getOrElse {
                    logConfigException("chain wireguard generation", it)
                    return StartResult.Failure("chain wireguard generation failed")
                }
        } else {
            val recovery = PersistedProfileRecovery.recover(config.beanBlob, config.protocolType)
            val bean = when (recovery) {
                is RecoveryResult.Success -> recovery.bean
                is RecoveryResult.Failure -> {
                    return StartResult.Failure(
                        recovery.supportError?.let { "chain selected profile rejected: $it" }
                            ?: "chain recovery failed: ${recovery.category}",
                    )
                }
            }
            val canonicalBean = runCatching { ConfigBuilder.canonicalBean(bean) }
                .getOrElse {
                    logConfigException("chain canonicalization", it)
                    return StartResult.Failure("chain canonicalization failed")
                }
            val decision = ConfigBuilder.supportDecisionCanonical(canonicalBean)
            if (decision is BeanSupportDecision.Unsupported) {
                logRejectedProfile(null, canonicalBean.value, decision, "chain selected")
                return StartResult.Failure("chain selected profile rejected: ${decision.error}")
            } else {
                logCanonicalProfileSummary(cachedSelectedProfileId, canonicalBean.value)
                val wrappers = if (upstream == null) {
                    decodeProfiles(
                        config.chainBeanBlobs,
                        config.chainProfileIds,
                        "chain",
                        enforceSizeLimit = false,
                        missingProfileIds = config.missingChainProfileIds,
                    )
                } else {
                    DecodedProfiles(emptyList(), emptyList())
                }
                if (wrappers.failures.isNotEmpty()) return StartResult.Failure("chain wrapper rejected")
                runCatching {
                    if (wrappers.beans.isNotEmpty()) {
                        ConfigBuilder.buildProfileChainProxyConfigFromCanonical(
                            canonicalBean,
                            wrappers.beans,
                            port,
                            config.dnsServers,
                            config.ipv6Enabled,
                        )
                    } else {
                        ConfigBuilder.buildChainConfigFromCanonical(
                            canonicalBean,
                            port,
                            upstream,
                            config.dnsServers,
                            config.ipv6Enabled,
                        )
                    }
                }
                    .getOrElse {
                        logConfigException("chain generation", it)
                        return StartResult.Failure("chain generation failed")
                    }
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
            activeAutoSelect = proxyAutoSelect
            PersistentLoggers.info(TAG, "startProxyMode sent over AIDL port=$port")
            StartResult.Success(socksPort = port)
        }.getOrElse {
            activeSocksPort = 0
            if (runtimeStarted) stopRuntimeAfterFailedReadiness(p)
            if (it is CancellationException) throw it
            PersistentLoggers.error(
                TAG,
                "startProxyMode failed exceptionClass=${it::class.java.simpleName} stableCategory=aidl",
            )
            StartResult.Failure("AIDL failed")
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
            activeSocksPort = pendingSocksPort
            activeTunAutoSelect = pendingTunAutoSelect
            pendingTunAutoSelect = false
            pendingSocksPort = 0
            pendingConfig = null
            PersistentLoggers.debug(
                TAG,
                "startWithConfig sent over AIDL activePort=$activeSocksPort autoSelect=$activeTunAutoSelect",
            )
            TunAttachResult.Success
        }.getOrElse {
            if (runtimeStarted) stopRuntimeAfterFailedReadiness(p)
            clearPendingStart()
            if (it is CancellationException) throw it
            PersistentLoggers.error(
                TAG,
                "startWithConfig failed exceptionClass=${it::class.java.simpleName} stableCategory=aidl",
            )
            TunAttachResult.Failure("AIDL failed")
        }
    }

    private fun stopRuntimeAfterFailedReadiness(p: ISingboxEngineProcess) {
        runCatching {
            val stopped = p.stopAndWait(REMOTE_STOP_TIMEOUT_MS)
            if (!stopped) PersistentLoggers.warn(TAG, "stop after routed probe failure timed out")
        }.onFailure { logConfigException("stop after routed probe failure", it) }
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
            }.onFailure { logConfigException("proxy stop", it) }
        }
        close()
    }

    override fun stopTimeoutMs(): Long = ENGINE_STOP_TIMEOUT_MS

    override suspend fun probe(): ProbeResult {
        return probeInternal()
    }

    private suspend fun probeInternal(): ProbeResult {
        val p = proxy ?: return ProbeResult.Failure("sing-box process is not connected")
        val port = activeSocksPort.takeIf { it > 0 }
            ?: return ProbeResult.Failure("sing-box SOCKS probe port is not active")
        PersistentLoggers.debug(TAG, "probe start port=$port chainMode=$chainMode")
        val runtimeRunning = runCatching { p.runtimeRunning() }.getOrElse {
            clearRuntimeState()
            logConfigException("runtime health check", it)
            return ProbeResult.Failure("sing-box runtime health check failed")
        }
        if (!runtimeRunning) {
            clearRuntimeState()
            return ProbeResult.Failure("sing-box runtime is not running")
        }
        if (!localSocksHandshake(port)) {
            clearRuntimeState()
            return ProbeResult.Failure("sing-box SOCKS5 listener is unavailable")
        }
        return when (val result = routedProbe.probe(port)) {
            is RoutedProbeResult.Success -> ProbeResult.Success(latencyMs = result.latencyMs)
            is RoutedProbeResult.Failure -> {
                PersistentLoggers.warn(
                    TAG,
                    "probe failed: reason=${result.reason} port=$port " +
                        "chainMode=$chainMode runtimeRunning=$runtimeRunning",
                )
                ProbeResult.Failure(
                    result.reason.probeFailureMessage(),
                    code = ProbeResult.Failure.Code.ROUTED_PROBE_FAILED,
                )
            }
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val process = proxy ?: return EnginePlugin.ReadyResult.Timeout("sing-box process is not connected")
        val runtimeRunning = runCatching { process.runtimeRunning() }.getOrDefault(false)
        if (!runtimeRunning) {
            return EnginePlugin.ReadyResult.Timeout("sing-box runtime is not running")
        }
        val port = activeSocksPort
        if (!awaitLocalSocksReady(port)) {
            return EnginePlugin.ReadyResult.Timeout("sing-box SOCKS5 listener is not ready")
        }
        return EnginePlugin.ReadyResult.Ready
    }

    private suspend fun awaitLocalSocksReady(port: Int): Boolean {
        repeat(LOCAL_SOCKS_READY_ATTEMPTS) { attempt ->
            if (localSocksHandshake(port)) return true
            if (attempt != LOCAL_SOCKS_READY_ATTEMPTS - 1) delay(LOCAL_SOCKS_READY_RETRY_MS)
        }
        return false
    }

    private suspend fun localSocksHandshake(port: Int): Boolean {
        if (port <= 0) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.soTimeout = LOCAL_SOCKS_IO_TIMEOUT_MS
                    socket.connect(
                        InetSocketAddress(LOCAL_SOCKS_HOST, port),
                        LOCAL_SOCKS_CONNECT_TIMEOUT_MS,
                    )
                    socket.getOutputStream().apply {
                        write(byteArrayOf(0x05, 0x01, 0x00))
                        flush()
                    }
                    val input = socket.getInputStream()
                    val version = input.read()
                    val method = input.read()
                    version == SOCKS5_VERSION && method == SOCKS5_NO_AUTH
                }
            }.getOrDefault(false)
        }
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
        dnsServers = buildList {
            add(TUN_DNS_V4)
            if (cachedIpv6Enabled) add(TUN_DNS_V6)
        },
        allowFamilyV4 = true,
        allowFamilyV6 = cachedIpv6Enabled,
        ipv6Address = "fdfe:dcba:9876::1".takeIf { cachedIpv6Enabled },
        ipv6PrefixLength = 126,
        routeAllV4 = true,
        routeAllV6 = cachedIpv6Enabled,
    )

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
            val profiles = cachedAutoProfiles.ifEmpty {
                runBlocking(Dispatchers.IO) {
                    profileDao.getAutoCandidatesFlow(MAX_AUTO_PROFILE_SCAN).first()
                }
                    .let(::autoSelectProfileWindow)
            }
            if (profiles.isEmpty()) return null
            val migratedProfiles = profiles.map(::migrateProfileBlobBlocking)
            return EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = migratedProfiles.map { it.beanBlob },
                autoSelectProfileIds = migratedProfiles.map { it.id },
                dnsServers = cachedDnsServers,
                ipv6Enabled = ipv6Enabled,
            )
        }
        val selectedProfile = cachedSelectedProfileId
            ?.takeIf { it != SELECTED_AUTO }
            ?.let { cachedProfilesById[it] ?: resolveProfileByIdBlocking(it) }
            ?.let(::migrateProfileBlobBlocking)
        val selectedBlob = selectedProfile?.beanBlob
        val savedRecovery = cachedBlob
            ?.takeIf { selectedBlob == null }
            ?.let(::recoverPersistedProfileWithoutProtocol)
        val blob = selectedBlob ?: savedRecovery?.let { KryoSerializer.serialize(it.bean) } ?: return null
        val type = selectedProfile?.protocolType ?: savedRecovery?.let { protocolTypeOf(it.bean) }
            ?: run {
                PersistentLoggers.warn(
                    TAG,
                    "singbox manual config rejected profileId=${cachedSelectedProfileId ?: "unknown"} category=DESERIALIZATION",
                )
                return null
            }
        val chainProfileIds = chainProfileIdsBlocking().filter { it != cachedSelectedProfileId }
        val chainProfiles = chainProfileIds.map { id ->
            id to (cachedProfilesById[id] ?: resolveProfileByIdBlocking(id))?.let(::migrateProfileBlobBlocking)
        }
        return EngineConfig.Singbox(
            beanBlob = blob,
            protocolType = type,
            chainBeanBlobs = chainProfiles.map { (_, profile) -> profile?.beanBlob ?: ByteArray(0) },
            chainProfileIds = chainProfileIds,
            missingChainProfileIds = chainProfiles
                .filter { (_, profile) -> profile == null }
                .map { it.first }
                .toSet(),
            dnsServers = cachedDnsServers,
            ipv6Enabled = ipv6Enabled,
        )
    }

    override suspend fun buildManualConfigAwaitingStorage(settings: SettingsModel?): EngineConfig? {
        if (!preferencesCacheInitialized) ensurePreferencesCacheInitialized(dataStore.data.first())
        return buildManualConfig(settings)
    }

    private fun ensurePreferencesCacheInitialized(prefs: Preferences) {
        if (preferencesCacheInitialized) return
        cachedBlob = prefs[BEAN_KEY]
        cachedSelectedProfileId = prefs[SELECTED_PROFILE_KEY]
        cachedDnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.toList()?.ifEmpty { null }
            ?: EngineConfig.Singbox.DEFAULT_DNS_SERVERS
        preferencesCacheInitialized = true
    }

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? =
        buildManualConfig(settings)?.let { it as? EngineConfig.Singbox }?.copy(proxyMode = true)

    override suspend fun buildProxyConfigAwaitingStorage(settings: SettingsModel?): EngineConfig? =
        buildManualConfigAwaitingStorage(settings)?.let { it as? EngineConfig.Singbox }?.copy(proxyMode = true)

    private fun decodeProfiles(
        blobs: List<ByteArray>,
        profileIds: List<Long>,
        source: String,
        enforceSizeLimit: Boolean,
        missingProfileIds: Set<Long> = emptySet(),
    ): DecodedProfiles {
        val beans = mutableListOf<AbstractBean>()
        val failures = mutableListOf<ProfileInputFailure>()
        blobs.forEachIndexed { index, blob ->
            val profileId = profileIds.getOrNull(index)
            if (profileId in missingProfileIds) {
                failures += ProfileInputFailure(index, ProfileInputStage.MISSING_PROFILE, profileId)
                return@forEachIndexed
            }
            if (enforceSizeLimit && blob.size > MAX_AUTO_SELECT_BLOB_BYTES) {
                failures += ProfileInputFailure(index, ProfileInputStage.SIZE, profileId)
                return@forEachIndexed
            }
            val knownProfile = profileId?.let { cachedProfilesById[it] ?: resolveProfileByIdBlocking(it) }
            val bean = if (knownProfile != null) {
                when (val recovery = PersistedProfileRecovery.recover(blob, knownProfile.protocolType)) {
                    is RecoveryResult.Success -> recovery.bean
                    is RecoveryResult.Failure -> {
                        failures += ProfileInputFailure(
                            index,
                            ProfileInputStage.DESERIALIZATION,
                            profileId,
                            reason = recovery.supportError,
                        )
                        return@forEachIndexed
                    }
                }
            } else {
                runCatching { KryoSerializer.deserialize<AbstractBean>(blob) }
                    .getOrElse {
                        failures += ProfileInputFailure(
                            index,
                            ProfileInputStage.DESERIALIZATION,
                            profileId,
                            exceptionClass = it.safeExceptionClass(),
                        )
                        return@forEachIndexed
                    }
            }
            val canonical = runCatching { ConfigBuilder.canonicalBean(bean) }
                .getOrElse {
                    failures += ProfileInputFailure(
                        index,
                        ProfileInputStage.CANONICALIZATION,
                        profileId,
                        exceptionClass = it.safeExceptionClass(),
                    )
                    return@forEachIndexed
                }
            when (val decision = ConfigBuilder.supportDecisionCanonical(canonical)) {
                BeanSupportDecision.Supported -> {
                    logCanonicalProfileSummary(profileId, canonical.value)
                    beans += canonical.value
                }
                is BeanSupportDecision.Unsupported -> {
                    logRejectedProfile(profileId, canonical.value, decision, source)
                    failures += ProfileInputFailure(index, ProfileInputStage.VALIDATION, profileId, decision.error)
                }
            }
        }
        failures.forEach {
            PersistentLoggers.warn(
                TAG,
                "singbox input rejected source=$source index=${it.index} " +
                    "profileId=${it.profileId ?: "unknown"} stage=${it.stage} " +
                    "reason=${it.reason ?: "none"} exceptionClass=${it.exceptionClass ?: "none"}",
            )
        }
        return DecodedProfiles(beans, failures)
    }

    private fun logRejectedProfile(
        profileId: Long?,
        bean: AbstractBean,
        decision: BeanSupportDecision.Unsupported,
        source: String,
    ) {
        val standard = bean as? ru.ozero.singboxfmt.StandardV2RayBean
        PersistentLoggers.warn(
            TAG,
            "singbox profile rejected source=$source id=${profileId ?: "unknown"} " +
                "protocol=${bean.protocolLabel()} transport=${standard?.type.orEmpty()} " +
                "security=${standard?.security.orEmpty()} headerType=${standard?.headerType.orEmpty()} " +
                "hasSni=${standard?.sni?.isNotBlank() == true} hasHost=${standard?.host?.isNotBlank() == true} " +
                "reason=${decision.error}",
        )
    }

    private fun logConfigException(stage: String, throwable: Throwable) {
        PersistentLoggers.warn(
            TAG,
            "singbox config failure stage=$stage exceptionClass=${throwable.safeExceptionClass()}",
        )
    }

    private fun logCanonicalProfileSummary(profileId: Long?, bean: AbstractBean) {
        val standard = bean as? StandardV2RayBean ?: return
        val publicKeyLength = standard.realityPublicKey.length
        val shortIdLength = standard.realityShortId.length
        val publicKeyValid = runCatching {
            val padded = standard.realityPublicKey + "=".repeat((4 - publicKeyLength % 4) % 4)
            java.util.Base64.getUrlDecoder().decode(padded).size == 32
        }.getOrDefault(false)
        val shortIdValid = shortIdLength <= 16 &&
            shortIdLength % 2 == 0 &&
            standard.realityShortId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        val serverIsIp = standard.serverAddress.trim('[', ']').let { address ->
            ':' in address ||
                address.split('.').let { parts ->
                    parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
                }
        }
        val flow = (bean as? VLESSBean)?.flow.orEmpty()
        PersistentLoggers.info(
            TAG,
            "singbox profile summary profileId=${profileId ?: "unknown"} protocol=${bean.protocolLabel()} " +
                "transport=${standard.type} security=${standard.security} flow=$flow serverIsIp=$serverIsIp " +
                "hasSni=${standard.sni.isNotBlank()} hasHost=${standard.host.isNotBlank()} " +
                "publicKeyLength=$publicKeyLength publicKeyValid=$publicKeyValid " +
                "shortIdLength=$shortIdLength shortIdValid=$shortIdValid fingerprint=${standard.realityFingerprint} " +
                "packetEncoding=${standard.packetEncoding} allowInsecure=${standard.allowInsecure}",
        )
    }

    private fun autoSelectProfileWindow(profiles: List<ProxyProfile>): List<ProxyProfile> {
        val selected = ArrayList<ProxyProfile>(MAX_AUTO_SELECT_OUTBOUNDS)
        val rejected = mutableMapOf<ProfileInputStage, Int>()
        for (profile in prioritizeSingboxAutoProfiles(profiles, MAX_AUTO_PROFILE_SCAN)) {
            val rejectionStage = autoProfileRejectionStage(profile)
            if (rejectionStage == null) {
                selected += profile
            } else {
                rejected[rejectionStage] = rejected.getOrDefault(rejectionStage, 0) + 1
            }
            if (selected.size == MAX_AUTO_SELECT_OUTBOUNDS) break
        }
        if (rejected.isNotEmpty()) {
            val rejectionSummary = rejected.toSortedMap().entries.joinToString { entry ->
                "${entry.key}=${entry.value}"
            }
            PersistentLoggers.warn(
                TAG,
                "auto-select skipped profiles=$rejectionSummary",
            )
        }
        return selected
    }

    private fun autoProfileRejectionStage(profile: ProxyProfile): ProfileInputStage? {
        if (profile.beanBlob.size > MAX_AUTO_SELECT_BLOB_BYTES) return ProfileInputStage.SIZE
        val bean = (PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType) as? RecoveryResult.Success)
            ?.bean ?: return ProfileInputStage.DESERIALIZATION
        val canonical = runCatching { ConfigBuilder.canonicalBean(bean) }
            .getOrElse { return ProfileInputStage.CANONICALIZATION }
        return when (ConfigBuilder.supportDecisionCanonical(canonical)) {
            BeanSupportDecision.Supported -> null
            is BeanSupportDecision.Unsupported -> ProfileInputStage.VALIDATION
        }
    }

    private fun resolveProfileByIdBlocking(id: Long): ProxyProfile? =
        runBlocking(Dispatchers.IO) { profileDao.getById(id) }

    private suspend fun migrateProfileBlob(profile: ProxyProfile): ProxyProfile {
        val recovered = PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType)
            as? RecoveryResult.Success
            ?: return profile
        return profile.copy(beanBlob = KryoSerializer.serialize(recovered.bean))
    }

    private fun migrateProfileBlobBlocking(profile: ProxyProfile): ProxyProfile =
        runBlocking(Dispatchers.IO) { migrateProfileBlob(profile) }

    private fun chainProfileIdsBlocking(): List<Long> =
        cachedChainProfileIds.ifEmpty {
            runBlocking(Dispatchers.IO) {
                proxyChainDao.getAll().map { it.profileId }
            }
        }

    private fun protocolTypeOf(bean: AbstractBean): Int = when (bean) {
        is VLESSBean -> PROTOCOL_VLESS
        is VMessBean -> PROTOCOL_VMESS
        is TrojanBean -> PROTOCOL_TROJAN
        is ShadowsocksBean -> PROTOCOL_SHADOWSOCKS
        else -> error("Unsupported Sing-box bean type: ${bean::class.java.simpleName}")
    }

    private fun recoverPersistedProfileWithoutProtocol(blob: ByteArray): RecoveryResult.Success? =
        PersistedProtocol.entries
            .mapNotNull { PersistedProfileRecovery.recover(blob, it) as? RecoveryResult.Success }
            .singleOrNull()

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
                    val connectedProcessId = runCatching { proxy?.processId() ?: -1 }.getOrDefault(-1)
                    engineProcessId = connectedProcessId
                    val recipient = IBinder.DeathRecipient {
                        proxy = null
                        engineBinder = null
                        clearRuntimeState()
                        logProcessExitInfo(connectedProcessId)
                        engineProcessId = -1
                        val ref = serviceConn
                        serviceConn = null
                        if (ref != null) runCatching { context.unbindService(ref) }
                        PersistentLoggers.warn(TAG, "SingboxEngineService binder died — :engine_singbox crash")
                        runCatching { onProcessDied() }
                    }
                    deathRecipient = recipient
                    if (runCatching { binder.linkToDeath(recipient, 0) }.isFailure) {
                        recipient.binderDied()
                        latch.countDown()
                        return
                    }
                    latch.countDown()
                    PersistentLoggers.debug(TAG, "SingboxEngineService connected")
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    proxy = null
                    engineBinder = null
                    clearRuntimeState()
                    logProcessExitInfo(engineProcessId)
                    engineProcessId = -1
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
                    logProcessExitInfo(engineProcessId)
                    engineProcessId = -1
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

    private fun logProcessExitInfo(processId: Int) {
        if (processId <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        engineScope.launch {
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return@launch
            var exit: android.app.ApplicationExitInfo? = null
            for (attempt in 0 until EXIT_INFO_ATTEMPTS) {
                exit = runCatching {
                    activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 64)
                        .firstOrNull { it.pid == processId }
                }.getOrNull()
                if (exit != null) break
                if (attempt < EXIT_INFO_ATTEMPTS - 1) delay(EXIT_INFO_RETRY_MS)
            }
            val checkpoints = SingboxRuntimeCheckpointStore.read(java.io.File(context.filesDir, "singbox"), processId)
            val resolvedExit = exit
            if (resolvedExit == null) {
                PersistentLoggers.warn(
                    TAG,
                    "engine process exit pid=$processId reason=unavailable checkpoints=${checkpoints.joinToString(" || ")}",
                )
                return@launch
            }
            val trace = runCatching {
                resolvedExit.traceInputStream?.bufferedReader()?.use { it.readText().take(2_000) }.orEmpty()
            }.getOrDefault("")
            PersistentLoggers.warn(
                TAG,
                "engine process exit pid=$processId reason=${resolvedExit.reason} status=${resolvedExit.status} " +
                    "importance=${resolvedExit.importance} pss=${resolvedExit.pss} rss=${resolvedExit.rss} " +
                    "timestamp=${resolvedExit.timestamp} " +
                    "description=${sanitizeExitDetail(resolvedExit.description.orEmpty())} " +
                    "trace=${sanitizeExitDetail(trace)} checkpoints=${checkpoints.joinToString(" || ")}",
            )
        }
    }

    private fun sanitizeExitDetail(detail: String): String = detail
        .replace(Regex("https?://[^\\s]+"), "<url>")
        .replace(Regex("(?i)(token|key|password)=?[^\\s,;]+"), "${'$'}1=<redacted>")
        .replace(Regex("\\s+"), " ")
        .take(2_000)

    private fun allocateChainPort(): Int {
        val offset = chainPortCounter.getAndIncrement() % CHAIN_PORT_RANGE
        return CHAIN_PORT_BASE + offset
    }

    companion object {
        private const val TUN_DNS_V4 = "172.19.0.2"
        private const val TUN_DNS_V6 = "fdfe:dcba:9876::2"
        private const val TAG = "SingboxEngine"
        private const val CONNECT_TIMEOUT_S = 5L
        private const val STATS_POLL_MS = 1_000L
        private const val REMOTE_STOP_TIMEOUT_MS = 3_000L
        private const val ENGINE_STOP_TIMEOUT_MS = 4_000L
        private const val EXIT_INFO_ATTEMPTS = 8
        private const val EXIT_INFO_RETRY_MS = 250L
        private const val LOCAL_SOCKS_HOST = "127.0.0.1"
        private const val LOCAL_SOCKS_CONNECT_TIMEOUT_MS = 400
        private const val LOCAL_SOCKS_IO_TIMEOUT_MS = 400
        private const val LOCAL_SOCKS_READY_ATTEMPTS = 10
        private const val LOCAL_SOCKS_READY_RETRY_MS = 100L
        private const val SOCKS5_VERSION = 0x05
        private const val SOCKS5_NO_AUTH = 0x00
        private const val MAX_AUTO_SELECT_OUTBOUNDS = 50
        private const val MAX_AUTO_SELECT_BLOB_BYTES = 64 * 1024
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

private fun Throwable.safeExceptionClass(): String = this::class.simpleName ?: "Throwable"

private fun List<ProfileInputFailure>.failureCounts(): String =
    groupingBy { failure -> failure.reason?.name ?: failure.stage.name }
        .eachCount()
        .toSortedMap()
        .entries
        .joinToString(",") { (reason, count) -> "$reason:$count" }
        .ifEmpty { "none" }

private fun RoutedProbeResult.Reason.probeFailureMessage(): String = when (this) {
    RoutedProbeResult.Reason.DNS -> "sing-box outbound DNS failed"
    RoutedProbeResult.Reason.CONNECT -> "sing-box outbound connect failed"
    RoutedProbeResult.Reason.TLS_CERTIFICATE -> "sing-box TLS certificate verification failed"
    RoutedProbeResult.Reason.TLS_HANDSHAKE -> "sing-box TLS handshake failed"
    RoutedProbeResult.Reason.TLS -> "sing-box outbound TLS failed"
    RoutedProbeResult.Reason.REMOTE_CLOSED -> "sing-box outbound closed by remote"
    RoutedProbeResult.Reason.TIMEOUT -> "sing-box outbound timed out"
    RoutedProbeResult.Reason.SOCKS_REPLY -> "sing-box SOCKS proxy rejected outbound"
    RoutedProbeResult.Reason.UNEXPECTED_RESPONSE ->
        "sing-box connectivity endpoint returned unexpected response"
    RoutedProbeResult.Reason.IO -> "sing-box outbound I/O failed"
    RoutedProbeResult.Reason.SOCKS_NOT_READY -> "sing-box SOCKS5 listener is not ready"
}
