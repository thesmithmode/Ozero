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
import dagger.hilt.android.qualifiers.ApplicationContext
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
import ru.ozero.enginescore.IpProbeRoute
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
import ru.ozero.singboxroom.dao.ProxyProfileDao
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SingboxEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val profileDao: ProxyProfileDao,
    private val onProcessDied: () -> Unit = {},
) : EnginePlugin, TunFdAcceptor {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedBlob: ByteArray? = null

    @Volatile
    private var cachedSelectedProfileId: Long? = null

    @Volatile
    private var cachedAutoBlobs: List<ByteArray> = emptyList()

    init {
        engineScope.launch {
            dataStore.data.collect { prefs ->
                cachedBlob = prefs[BEAN_KEY]
                cachedSelectedProfileId = prefs[SELECTED_PROFILE_KEY]
            }
        }
        engineScope.launch {
            profileDao.getAllFlow().collect { profiles ->
                cachedAutoBlobs = profiles.map { it.beanBlob }
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
    private var chainMode: Boolean = false

    @Volatile
    private var activeSocksPort: Int = 0

    private val bindLock = Any()

    private val localProtector = object : ISingboxProtector.Stub() {
        override fun protect(fd: Int): Boolean = VpnSocketProtectorHolder.protect(fd)
    }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Singbox) { "SingboxEngine requires EngineConfig.Singbox" }

        val isChain = upstream !is Upstream.None
        chainMode = isChain

        if (isChain) {
            val socks = upstream as? Upstream.Socks5
                ?: return StartResult.Failure("SingboxEngine chain mode requires Socks5 upstream, got $upstream")
            return startChainMode(config, socks)
        }

        if (config.autoSelectBeanBlobs.isNotEmpty()) {
            val beans = config.autoSelectBeanBlobs
                .take(MAX_AUTO_SELECT_OUTBOUNDS)
                .mapNotNull {
                    runCatching { KryoSerializer.deserialize<AbstractBean>(it) }.getOrNull()
                }
            if (beans.isEmpty()) return StartResult.Failure("auto-select: no valid beans")
            val json = runCatching { ConfigBuilder.buildSingboxAutoConfig(beans) }
                .getOrElse { return StartResult.Failure("Failed to build auto-select config: ${it.message}") }
            bindOrFail()?.let { return it }
            pendingConfig = json
            return StartResult.Success(socksPort = 0)
        }

        val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(config.beanBlob) }
            .getOrElse { return StartResult.Failure("Failed to deserialize config: ${it.message}") }

        val json = runCatching { ConfigBuilder.buildSingboxConfig(bean) }
            .getOrElse { return StartResult.Failure("Failed to build sing-box config: ${it.message}") }

        bindOrFail()?.let { return it }

        pendingConfig = json
        return StartResult.Success(socksPort = 0)
    }

    private suspend fun startChainMode(config: EngineConfig.Singbox, upstream: Upstream.Socks5): StartResult {
        val port = allocateChainPort()
        activeSocksPort = port
        val configUpstream = ConfigBuilder.Upstream(upstream.host, upstream.port)

        val json = if (config.autoSelectBeanBlobs.isNotEmpty()) {
            val beans = config.autoSelectBeanBlobs
                .take(MAX_AUTO_SELECT_OUTBOUNDS)
                .mapNotNull { runCatching { KryoSerializer.deserialize<AbstractBean>(it) }.getOrNull() }
            if (beans.isEmpty()) return StartResult.Failure("chain auto-select: no valid beans")
            runCatching { ConfigBuilder.buildAutoChainConfig(beans, port, configUpstream) }
                .getOrElse { return StartResult.Failure("chain auto config: ${it.message}") }
        } else if (config.wireGuardConfig != null) {
            runCatching { ConfigBuilder.buildWireGuardChainConfig(config.wireGuardConfig, port, configUpstream) }
                .getOrElse { return StartResult.Failure("chain WG config: ${it.message}") }
        } else {
            val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(config.beanBlob) }
                .getOrElse { return StartResult.Failure("chain deserialize: ${it.message}") }
            runCatching { ConfigBuilder.buildChainConfig(bean, port, configUpstream) }
                .getOrElse { return StartResult.Failure("chain config: ${it.message}") }
        }

        bindOrFail()?.let { return it }

        val p = proxy ?: return StartResult.Failure("SingboxEngineService not connected for chain mode")
        return runCatching {
            p.startProxyMode(json, localProtector)
            PersistentLoggers.info(TAG, "startProxyMode sent over AIDL port=$port")
            StartResult.Success(socksPort = port)
        }.getOrElse {
            PersistentLoggers.error(TAG, "startProxyMode AIDL call failed: ${it.message}", it)
            StartResult.Failure("startProxyMode failed: ${it.message}")
        }
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        if (chainMode) return TunAttachResult.Failure("chain mode — TUN not used")
        val json = pendingConfig ?: return TunAttachResult.Failure("attachTun called before start()")
        val p = proxy ?: return TunAttachResult.Failure("SingboxEngineService not connected")
        return runCatching {
            val pfd = ParcelFileDescriptor.fromFd(tunFd)
            try {
                p.startWithConfig(pfd, json, localProtector)
            } finally {
                runCatching { pfd.close() }
            }
            PersistentLoggers.debug(TAG, "startWithConfig sent over AIDL")
            TunAttachResult.Success
        }.getOrElse {
            PersistentLoggers.error(TAG, "startWithConfig AIDL call failed: ${it.message}", it)
            TunAttachResult.Failure("startWithConfig AIDL call failed: ${it.message}")
        }
    }

    override suspend fun stop() {
        pendingConfig = null
        chainMode = false
        activeSocksPort = 0
        runCatching { proxy?.stop() }
            .onFailure { PersistentLoggers.warn(TAG, "proxy.stop() failed: ${it.message}") }
        close()
    }

    override suspend fun probe(): ProbeResult = ProbeResult.Success(latencyMs = 0L)

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
        dnsServers = listOf("1.1.1.1"),
        allowFamilyV4 = true,
        allowFamilyV6 = true,
        ipv6Address = "fdfe:dcba:9876::1",
        ipv6PrefixLength = 126,
        routeAllV4 = true,
        routeAllV6 = true,
    )

    override suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute = IpProbeRoute.Default

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? {
        if (cachedSelectedProfileId == SELECTED_AUTO) {
            val blobs = cachedAutoBlobs.ifEmpty {
                // cold-start race: cache not yet populated; do synchronous fallback read
                runBlocking(Dispatchers.IO) {
                    profileDao.getAllFlow().first()
                }.map { it.beanBlob }
            }
            if (blobs.isEmpty()) return null
            return EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = blobs,
            )
        }
        val blob = cachedBlob ?: return null
        val type = runCatching {
            protocolTypeOf(KryoSerializer.deserialize<AbstractBean>(blob))
        }.getOrDefault(PROTOCOL_VLESS)
        return EngineConfig.Singbox(beanBlob = blob, protocolType = type)
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
                    unlinkDeath()
                    val ref = serviceConn
                    serviceConn = null
                    if (ref != null) runCatching { context.unbindService(ref) }
                    PersistentLoggers.warn(TAG, "SingboxEngineService binding died")
                    runCatching { onProcessDied() }
                }
            }
            val component = ComponentName(context, "ru.ozero.singboxprocess.SingboxEngineService")
            val intent = Intent().setComponent(component)
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
        if (b != null && r != null) runCatching { b.unlinkToDeath(r, 0) }
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
        private const val MAX_AUTO_SELECT_OUTBOUNDS = 50
        private const val CHAIN_PORT_BASE = 49408
        private const val CHAIN_PORT_RANGE = 256
        private val chainPortCounter = java.util.concurrent.atomic.AtomicInteger(0)
        private val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        private val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        const val SELECTED_AUTO = -1L
        const val PROTOCOL_AUTO_SELECT = -1
        const val PROTOCOL_VLESS = 0
        const val PROTOCOL_VMESS = 1
        const val PROTOCOL_TROJAN = 2
        const val PROTOCOL_SHADOWSOCKS = 3
    }
}
