package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.VpnSocketProtector
import ru.ozero.enginescore.VpnSocketProtectorHolder

class EngineWarp(
    private val autoConfig: WarpAutoConfig,
    private val configStore: WarpConfigSlotStore,
    private val sdkBridge: WarpSdkBridge,
    private val uapiPathProvider: () -> String,
    private val socketProtector: VpnSocketProtector = VpnSocketProtectorHolder,
) : EnginePlugin, TunFdAcceptor {

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

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Warp) { "EngineWarp требует EngineConfig.Warp" }
        require(upstream is Upstream.None) {
            "EngineWarp не принимает upstream — supportsUpstreamSocks=false"
        }
        val effective = resolveConfig() ?: return StartResult.Failure(
            reason = "WARP config resolve failed (auto-register не сработал)",
        )
        resolvedConfig = effective
        resolvedIni = WarpIniBuilder.build(effective)
        PersistentLoggers.info(TAG, "resolved config: $effective")
        return StartResult.Success(socksPort = WARP_NO_SOCKS_PORT)
    }

    override suspend fun stop() {
        PersistentLoggers.info(TAG, "stop — detaching tun")
        sdkBridge.detachTun()
        resolvedConfig = null
        resolvedIni = null
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure(reason = "WARP не предоставляет SOCKS-интерфейс")

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override suspend fun tunSpec(): TunSpec? {
        val cfg = resolvedConfig
            ?: resolveConfig()?.also { resolvedConfig = it }
            ?: return null
        val v4Addr = cfg.interfaceAddressV4.substringBefore('/').takeIf { it.isNotBlank() }
            ?: return null
        val v4Prefix = cfg.interfaceAddressV4.substringAfter('/', missingDelimiterValue = "32")
            .toIntOrNull() ?: 32
        val v6Addr = cfg.interfaceAddressV6.substringBefore('/').takeIf { it.isNotBlank() }
        val v6Prefix = cfg.interfaceAddressV6.substringAfter('/', missingDelimiterValue = "128")
            .toIntOrNull() ?: 128
        return TunSpec(
            sessionName = "WARP",
            mtu = cfg.mtu,
            blocking = false,
            ipv4Address = v4Addr,
            ipv4PrefixLength = v4Prefix,
            dnsServers = cfg.dnsServers,
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
        PersistentLoggers.info(TAG, "attachTun fd=$tunFd uapi=$uapiPath/$TUNNEL_NAME.sock")
        return when (val r = sdkBridge.attachTun(TUNNEL_NAME, tunFd, ini, uapiPath, socketProtector)) {
            WarpSdkBridge.AttachResult.Success -> TunAttachResult.Success
            is WarpSdkBridge.AttachResult.Failed -> {
                val maskedIni = ini.replace(Regex("(?m)^(PrivateKey\\s*=\\s*)(.+)$"), "$1<masked>")
                PersistentLoggers.error(TAG, "attachTun failed: ${r.reason}\nini:\n$maskedIni")
                TunAttachResult.Failure(r.reason)
            }
        }
    }

    private suspend fun resolveConfig(): WarpConfig? {
        configStore.activeConfig().first()?.let { return it }
        PersistentLoggers.info(TAG, "no active config — autoConfig.register")
        val regResult = autoConfig.register()
        val fresh = regResult.getOrElse { t ->
            PersistentLoggers.error(TAG, "register failed: ${t.message}")
            return null
        }
        runCatching { configStore.addSlot("WARP Auto", fresh) }
            .onSuccess { PersistentLoggers.info(TAG, "auto-registered config saved as slot $it") }
            .onFailure { PersistentLoggers.warn(TAG, "addSlot failed: ${it.message}") }
        return fresh
    }

    private companion object {
        const val TAG = "EngineWarp"
        const val WARP_NO_SOCKS_PORT = 0
        const val TUNNEL_NAME = "ozero-warp"
    }
}
