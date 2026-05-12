package ru.ozero.enginewarp

import android.util.Log
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
    private val ipv6EnabledProvider: () -> Boolean = { false },
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
        val resolved = resolveActive() ?: return StartResult.Failure(
            reason = "WARP config resolve failed (auto-register не сработал)",
        )
        resolvedConfig = resolved.config
        resolvedIni = resolved.ini
        Log.i(TAG, "resolved config: ${resolved.config} (iniSource=${resolved.iniSource})")
        return StartResult.Success(socksPort = WARP_NO_SOCKS_PORT)
    }

    override suspend fun stop() {
        Log.i(TAG, "stop — detaching tun")
        sdkBridge.detachTun()
        resolvedConfig = null
        resolvedIni = null
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure(reason = "WARP не предоставляет SOCKS-интерфейс")

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override fun preflight(): ru.ozero.enginescore.EnginePreflight = WarpPreflight()

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
        val ipv6Allowed = ipv6EnabledProvider()
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
            WarpSdkBridge.AttachResult.Success -> TunAttachResult.Success
            is WarpSdkBridge.AttachResult.Failed -> {
                val maskedIni = ini.replace(Regex("(?m)^(PrivateKey\\s*=\\s*)(.+)$"), "$1<masked>")
                PersistentLoggers.error(TAG, "attachTun failed: ${r.reason}\nini:\n$maskedIni")
                TunAttachResult.Failure(r.reason)
            }
        }
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
            runCatching { configStore.addSlot("WARP Auto", fresh.config, fresh.rawIni) }
                .onSuccess { Log.i(TAG, "auto-registered config saved as slot $it") }
                .onFailure { PersistentLoggers.warn(TAG, "addSlot failed: ${it.message}") }
            buildResolved(fresh.config, fresh.rawIni, source = "auto")
        }
    }

    private fun buildResolved(config: WarpConfig, rawIni: String?, source: String): ResolvedWarp {
        val resolvedConfig = resolveEndpointHost(config)
        val ipv6Allowed = ipv6EnabledProvider()
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

    private fun resolveEndpointHost(cfg: WarpConfig): WarpConfig {
        val ep = cfg.peerEndpoint
        val sep = ep.lastIndexOf(':')
        if (sep < 0) return cfg
        val host = ep.substring(0, sep)
        val port = ep.substring(sep + 1)
        if (host.isBlank() || isLikelyIpAddress(host)) return cfg
        return runCatching {
            val resolved = java.net.InetAddress.getByName(host).hostAddress
            if (resolved.isNullOrBlank()) {
                cfg
            } else {
                Log.i(TAG, "endpoint resolved $host → $resolved")
                cfg.copy(peerEndpoint = "$resolved:$port")
            }
        }.getOrElse { t ->
            PersistentLoggers.warn(TAG, "endpoint resolve failed for $host: ${t.message}")
            cfg
        }
    }

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

    private fun isLikelyIpAddress(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host.startsWith('[') || host.contains(':')) return true
        return host.all { it.isDigit() || it == '.' }
    }

    private companion object {
        const val TAG = "EngineWarp"
        const val WARP_NO_SOCKS_PORT = 0
        const val TUNNEL_NAME = "ozero-warp"
    }
}
