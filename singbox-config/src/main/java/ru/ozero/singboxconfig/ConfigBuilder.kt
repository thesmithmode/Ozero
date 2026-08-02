@file:Suppress("TooManyFunctions")

package ru.ozero.singboxconfig

import java.net.URI
import java.util.Base64
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.WireGuardOutboundConfig
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.BeanCanonicalizer
import ru.ozero.singboxfmt.CanonicalizationResult
import ru.ozero.singboxfmt.canonicalBeanOrThrow
import ru.ozero.singboxfmt.hasRequiredOutboundCredentials

private const val VLESS_FLOW_XTLS_VISION = "xtls-rprx-vision"
private const val REALITY_PUBLIC_KEY_BYTES = 32
private const val DNS_LOCAL_TAG = "dns-local"
private const val AUTO_SELECT_PROBE_URL = "https://www.gstatic.com/generate_204"

enum class BeanSupportError {
    INVALID_PORT,
    INVALID_SERVER,
    MISSING_CREDENTIALS,
    UNSUPPORTED_BEAN_TYPE,
    UNSUPPORTED_SECURITY,
    UNSUPPORTED_TRANSPORT,
    CORE_UNSUPPORTED_XHTTP,
    UNSUPPORTED_TCP_HEADER,
    UNSUPPORTED_QUIC_SECURITY,
    INVALID_REALITY_PUBLIC_KEY,
    INVALID_REALITY_SHORT_ID,
    UNSUPPORTED_GRPC_MULTI_MODE,
    UNSUPPORTED_GRPC_COMPAT_MODE,
    UNSUPPORTED_ECH,
    UNSUPPORTED_MTLS,
    UNSUPPORTED_CERTIFICATE_PINNING,
    MISSING_REALITY_SERVER_NAME,
    UNSUPPORTED_VLESS_FLOW,
    UNSUPPORTED_VLESS_ENCRYPTION,
    UNSUPPORTED_PACKET_ENCODING,
    UNSUPPORTED_MUX,
    UNSUPPORTED_BROWSER_FORWARDER,
    UNSUPPORTED_REALITY_OPTIONS,
    UNSUPPORTED_SHADOWSOCKS_PLUGIN,
    UNSUPPORTED_CORE_FEATURE,
}

sealed interface BeanSupportDecision {
    data object Supported : BeanSupportDecision
    data class Unsupported(val error: BeanSupportError) : BeanSupportDecision
}

@Suppress("TooManyFunctions")
object ConfigBuilder {
    class CanonicalBean internal constructor(val value: AbstractBean)

    private val SUPPORTED_TRANSPORTS = setOf("tcp", "ws", "grpc", "http", "h2", "httpupgrade", "")
    private val SUPPORTED_SECURITY = setOf("", "none", "tls", "reality")
    private val SUPPORTED_VLESS_ENCRYPTION = setOf("", "none")
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535
    private const val MAX_AUTO_OUTBOUNDS = 50
    private const val MAX_PROBE_OUTBOUNDS = 10
    private const val MAX_AUTO_CONFIG_BYTES = 512 * 1024

    fun buildSingboxConfig(
        bean: AbstractBean,
        probeSocksPort: Int? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val canonical = bean.canonicalBeanOrThrow()
        require(isSupportedBean(canonical)) { "Unsupported transport: ${(canonical as? StandardV2RayBean)?.type}" }
        val outbound = beanOutbound(canonical, "proxy")
        return buildFullConfig(listOf(outbound), probeSocksPort, dnsServers, ipv6Enabled)
    }

    fun canonicalBean(bean: AbstractBean): CanonicalBean = CanonicalBean(bean.canonicalBeanOrThrow())

    fun buildSingboxConfigFromCanonical(
        bean: CanonicalBean,
        probeSocksPort: Int? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        require(supportDecisionCanonical(bean) is BeanSupportDecision.Supported)
        return buildFullConfig(listOf(beanOutbound(bean.value, "proxy")), probeSocksPort, dnsServers, ipv6Enabled)
    }

    fun buildSingboxAutoConfig(
        beans: List<AbstractBean>,
        probeSocksPort: Int? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val supported = beans.map(AbstractBean::canonicalBeanOrThrow).filter { isSupportedBean(it) }
        require(supported.size <= MAX_AUTO_OUTBOUNDS) { "auto-select supports at most $MAX_AUTO_OUTBOUNDS outbounds" }
        require(supported.isNotEmpty()) { "no beans with supported transport types" }
        val proxyOutbounds = supported.mapIndexed { index, bean -> beanOutbound(bean, "proxy-$index") }
        val tagList = proxyOutbounds.indices.joinToString(",") { jsonString("proxy-$it") }
        val urltest = buildString {
            append("""{"type":"urltest","tag":"proxy","outbounds":[$tagList],""")
            append(""""url":"$AUTO_SELECT_PROBE_URL",""")
            append(""""interval":"3m","tolerance":50,""")
            append(""""interrupt_exist_connections":true,"idle_timeout":"30m"}""")
        }
        return buildFullConfig(listOf(urltest) + proxyOutbounds, probeSocksPort, dnsServers, ipv6Enabled)
            .also { json ->
                require(json.toByteArray(Charsets.UTF_8).size <= MAX_AUTO_CONFIG_BYTES) {
                    "auto-select config is too large"
                }
            }
    }

    fun isSupportedBean(bean: AbstractBean): Boolean =
        supportDecision(bean) is BeanSupportDecision.Supported

    fun supportDecision(bean: AbstractBean): BeanSupportDecision =
        when (val canonical = BeanCanonicalizer.canonicalizeOrError(bean)) {
            is CanonicalizationResult.Canonical -> supportDecisionCanonicalValue(canonical.bean)
            is CanonicalizationResult.Rejected -> unsupported(BeanSupportError.UNSUPPORTED_CORE_FEATURE)
        }

    fun supportDecisionCanonical(bean: CanonicalBean): BeanSupportDecision = supportDecisionCanonicalValue(bean.value)

    private fun supportDecisionCanonicalValue(bean: AbstractBean): BeanSupportDecision {
        if (bean.serverPort !in MIN_PORT..MAX_PORT) return unsupported(BeanSupportError.INVALID_PORT)
        if (!bean.hasRoutableServerAddress()) return unsupported(BeanSupportError.INVALID_SERVER)
        if (!bean.hasRequiredOutboundCredentials()) return unsupported(BeanSupportError.MISSING_CREDENTIALS)
        return when (bean) {
            is VLESSBean -> supportDecision(bean)
            is VMessBean -> supportDecision(bean)
            is TrojanBean -> supportDecision(bean)
            is ShadowsocksBean -> supportDecision(bean)
            else -> unsupported(BeanSupportError.UNSUPPORTED_BEAN_TYPE)
        }
    }

    private fun supportDecision(bean: StandardV2RayBean): BeanSupportDecision {
        val error = listOfNotNull(
            BeanSupportError.UNSUPPORTED_SECURITY.takeIf {
                bean.security.trim().lowercase() !in SUPPORTED_SECURITY
            },
            BeanSupportError.UNSUPPORTED_VLESS_FLOW.takeIf {
                bean is VLESSBean && normalizeVlessFlow(bean.flow) == null
            },
            BeanSupportError.UNSUPPORTED_VLESS_ENCRYPTION.takeIf {
                bean is VLESSBean && bean.encryption.trim().lowercase() !in SUPPORTED_VLESS_ENCRYPTION
            },
            BeanSupportError.CORE_UNSUPPORTED_XHTTP.takeIf { bean.type == "splithttp" },
            BeanSupportError.UNSUPPORTED_TRANSPORT.takeIf {
                bean.type !in SUPPORTED_TRANSPORTS && bean.type != "splithttp"
            },
            BeanSupportError.UNSUPPORTED_TCP_HEADER.takeIf { bean.hasUnsupportedTcpHeader() },
            BeanSupportError.UNSUPPORTED_GRPC_MULTI_MODE.takeIf { bean.type == "grpc" && bean.grpcMultiMode },
            BeanSupportError.UNSUPPORTED_GRPC_COMPAT_MODE.takeIf {
                bean.type == "grpc" && bean.grpcServiceNameCompat
            },
            BeanSupportError.MISSING_REALITY_SERVER_NAME.takeIf { bean.hasMissingRealityServerName() },
            BeanSupportError.UNSUPPORTED_PACKET_ENCODING.takeIf { bean.hasUnsupportedPacketEncoding() },
            BeanSupportError.UNSUPPORTED_MUX.takeIf { bean.hasUnsupportedMux() },
            BeanSupportError.UNSUPPORTED_BROWSER_FORWARDER.takeIf { bean.hasBrowserForwarder() },
            BeanSupportError.UNSUPPORTED_REALITY_OPTIONS.takeIf { bean.hasUnsupportedRealityOptions() },
            BeanSupportError.UNSUPPORTED_QUIC_SECURITY.takeIf { bean.hasUnsupportedQuicSecurity() },
            BeanSupportError.UNSUPPORTED_ECH.takeIf { bean.echEnabled || bean.echConfig.isNotBlank() },
            BeanSupportError.UNSUPPORTED_MTLS.takeIf { bean.hasMtls() },
            BeanSupportError.UNSUPPORTED_CERTIFICATE_PINNING.takeIf { bean.hasCertificatePinning() },
            BeanSupportError.INVALID_REALITY_PUBLIC_KEY.takeIf { bean.hasInvalidRealityPublicKey() },
            BeanSupportError.INVALID_REALITY_SHORT_ID.takeIf { bean.hasInvalidRealityShortId() },
        ).firstOrNull()
        return error?.let(::unsupported) ?: BeanSupportDecision.Supported
    }

    private fun supportDecision(bean: ShadowsocksBean): BeanSupportDecision = when {
        bean.plugin.isNotBlank() || bean.pluginOpts.isNotBlank() ->
            unsupported(BeanSupportError.UNSUPPORTED_SHADOWSOCKS_PLUGIN)
        else -> BeanSupportDecision.Supported
    }

    private fun unsupported(error: BeanSupportError): BeanSupportDecision.Unsupported =
        BeanSupportDecision.Unsupported(error)

    fun buildChainConfig(
        bean: AbstractBean,
        socksPort: Int,
        upstream: Upstream? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val canonical = bean.canonicalBeanOrThrow()
        require(isSupportedBean(canonical)) { "Unsupported transport: ${(canonical as? StandardV2RayBean)?.type}" }
        val outbound = beanOutbound(canonical, "proxy", detour = upstream?.let { "upstream" })
        return buildChainFullConfig(socksPort, listOf(outbound), upstream, dnsServers, ipv6Enabled)
    }

    fun buildChainConfigFromCanonical(
        bean: CanonicalBean,
        socksPort: Int,
        upstream: Upstream? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        require(supportDecisionCanonical(bean) is BeanSupportDecision.Supported)
        val outbound = beanOutbound(bean.value, "proxy", detour = upstream?.let { "upstream" })
        return buildChainFullConfig(socksPort, listOf(outbound), upstream, dnsServers, ipv6Enabled)
    }

    fun buildAutoChainConfig(
        beans: List<AbstractBean>,
        socksPort: Int,
        upstream: Upstream? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        require(beans.isNotEmpty()) { "beans must not be empty for auto-select chain config" }
        val supported = beans.map(AbstractBean::canonicalBeanOrThrow).filter { isSupportedBean(it) }
        require(supported.size <= MAX_AUTO_OUTBOUNDS) { "auto-select supports at most $MAX_AUTO_OUTBOUNDS outbounds" }
        require(supported.isNotEmpty()) { "no beans with supported transport types" }
        val detourTag = upstream?.let { "upstream" }
        val proxyOutbounds = supported.mapIndexed { index, bean ->
            beanOutbound(bean, "proxy-$index", detour = detourTag)
        }
        val tagList = proxyOutbounds.indices.joinToString(",") { jsonString("proxy-$it") }
        val urltest = buildString {
            append("""{"type":"urltest","tag":"proxy","outbounds":[$tagList],""")
            append(""""url":"$AUTO_SELECT_PROBE_URL",""")
            append(""""interval":"3m","tolerance":50,""")
            append(""""interrupt_exist_connections":true,"idle_timeout":"30m"}""")
        }
        return buildChainFullConfig(socksPort, listOf(urltest) + proxyOutbounds, upstream, dnsServers, ipv6Enabled)
    }

    fun buildProbeConfig(
        targets: List<ProbeTarget>,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        require(targets.isNotEmpty()) { "probe targets must not be empty" }
        require(targets.size <= MAX_PROBE_OUTBOUNDS) {
            "probe config supports at most $MAX_PROBE_OUTBOUNDS outbounds"
        }
        require(targets.map { it.socksPort }.distinct().size == targets.size) {
            "probe socks ports must be unique"
        }
        val canonicalTargets = targets.map { it.copy(bean = it.bean.canonicalBeanOrThrow()) }
        canonicalTargets.forEach { target ->
            require(target.socksPort in MIN_PORT..MAX_PORT) { "invalid probe socks port" }
            require(isSupportedBean(target.bean)) { "unsupported probe target" }
        }

        val inbounds = canonicalTargets.mapIndexed { index, target ->
            socksInbound(target.socksPort, "probe-in-$index")
        }
        val outbounds = canonicalTargets.mapIndexed { index, target ->
            beanOutbound(target.bean, "probe-out-$index")
        }
        val routeRules = canonicalTargets.indices.map { index ->
            """{"inbound":["probe-in-$index"],"outbound":"probe-out-$index"}"""
        }
        return buildProbeFullConfig(inbounds, outbounds, routeRules, dnsServers, ipv6Enabled)
    }

    fun buildWireGuardChainConfig(
        wg: WireGuardOutboundConfig,
        socksPort: Int,
        upstream: Upstream? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val outbound = wireGuardOutbound(wg, "proxy", detour = upstream?.let { "upstream" })
        return buildChainFullConfig(socksPort, listOf(outbound), upstream, dnsServers, ipv6Enabled)
    }

    fun buildProfileChainConfig(
        selected: AbstractBean,
        wrappers: List<AbstractBean>,
        probeSocksPort: Int? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val outbounds = profileChainOutbounds(selected, wrappers)
        return buildFullConfig(outbounds, probeSocksPort, dnsServers, ipv6Enabled)
    }

    fun buildProfileChainProxyConfig(
        selected: AbstractBean,
        wrappers: List<AbstractBean>,
        socksPort: Int,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val outbounds = profileChainOutbounds(selected, wrappers)
        return buildChainFullConfig(
            socksPort = socksPort,
            proxyOutbounds = outbounds,
            upstream = null,
            dnsServers = dnsServers,
            ipv6Enabled = ipv6Enabled,
        )
    }

    fun buildProfileChainConfigFromCanonical(
        selected: CanonicalBean,
        wrappers: List<AbstractBean>,
        probeSocksPort: Int? = null,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String = buildFullConfig(
        profileChainOutboundsCanonical(selected.value, wrappers),
        probeSocksPort,
        dnsServers,
        ipv6Enabled,
    )

    fun buildProfileChainProxyConfigFromCanonical(
        selected: CanonicalBean,
        wrappers: List<AbstractBean>,
        socksPort: Int,
        dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
        ipv6Enabled: Boolean = true,
    ): String {
        val outbounds = profileChainOutboundsCanonical(selected.value, wrappers)
        return buildChainFullConfig(socksPort, outbounds, null, dnsServers, ipv6Enabled)
    }

    data class Upstream(val host: String, val port: Int)

    data class ProbeTarget(val bean: AbstractBean, val socksPort: Int)

    private fun profileChainOutbounds(
        selected: AbstractBean,
        wrappers: List<AbstractBean>,
    ): List<String> {
        val canonicalSelected = selected.canonicalBeanOrThrow()
        val supportedWrappers = wrappers.map(AbstractBean::canonicalBeanOrThrow)
        require(isSupportedBean(canonicalSelected)) { "Unsupported selected transport" }
        require(supportedWrappers.all(::isSupportedBean)) { "Unsupported wrapper transport" }
        val wrapperOutbounds = supportedWrappers.mapIndexed { index, bean ->
            val detour = if (index == 0) null else "chain-${index - 1}"
            beanOutbound(bean, "chain-$index", detour)
        }
        val selectedDetour = supportedWrappers.lastIndex.takeIf { it >= 0 }?.let { "chain-$it" }
        val selectedOutbound = beanOutbound(canonicalSelected, "proxy", selectedDetour)
        return wrapperOutbounds + selectedOutbound
    }

    private fun profileChainOutboundsCanonical(
        canonicalSelected: AbstractBean,
        wrappers: List<AbstractBean>,
    ): List<String> {
        val supportedWrappers = wrappers.map(AbstractBean::canonicalBeanOrThrow)
        require(supportDecisionCanonicalValue(canonicalSelected) is BeanSupportDecision.Supported)
        require(supportedWrappers.all(::isSupportedBean))
        val wrapperOutbounds = supportedWrappers.mapIndexed { index, bean ->
            beanOutbound(bean, "chain-$index", if (index == 0) null else "chain-${index - 1}")
        }
        val selectedDetour = supportedWrappers.lastIndex.takeIf { it >= 0 }?.let { "chain-$it" }
        return wrapperOutbounds + beanOutbound(canonicalSelected, "proxy", selectedDetour)
    }

    private fun beanOutbound(bean: AbstractBean, tag: String, detour: String? = null): String = when (bean) {
        is VLESSBean -> vlessOutbound(bean, tag, detour)
        is VMessBean -> vmessOutbound(bean, tag, detour)
        is TrojanBean -> trojanOutbound(bean, tag, detour)
        is ShadowsocksBean -> shadowsocksOutbound(bean, tag, detour)
        else -> error("Unsupported bean type: ${bean::class.simpleName}")
    }

    private fun buildFullConfig(
        proxyOutbounds: List<String>,
        probeSocksPort: Int? = null,
        dnsServers: List<String>,
        ipv6Enabled: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""log":{"level":"warn","timestamp":true},""")
        sb.append(""""inbounds":[""")
        sb.append(tunInbound(ipv6Enabled))
        if (probeSocksPort != null && probeSocksPort > 0) {
            sb.append(',')
            sb.append(socksInbound(probeSocksPort))
        }
        sb.append("""],""")
        sb.append(""""outbounds":[""")
        proxyOutbounds.forEachIndexed { i, outbound ->
            if (i > 0) sb.append(',')
            sb.append(outbound)
        }
        sb.append(""",{"type":"direct","tag":"direct"}""")
        sb.append(""",{"type":"block","tag":"block"}""")
        sb.append("""],""")
        sb.append(dnsConfig(dnsServers, detour = "proxy", ipv6Enabled = ipv6Enabled))
        sb.append("\"route\":{")
        sb.append(defaultDomainResolver(ipv6Enabled))
        sb.append(""""final":"proxy",""")
        sb.append(""""auto_detect_interface":true,""")
        sb.append(""""rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}]""")
        sb.append('}')
        sb.append('}')
        return sb.toString()
    }

    private fun buildChainFullConfig(
        socksPort: Int,
        proxyOutbounds: List<String>,
        upstream: Upstream?,
        dnsServers: List<String>,
        ipv6Enabled: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""log":{"level":"warn","timestamp":true},""")
        sb.append(""""inbounds":[""")
        sb.append(socksInbound(socksPort))
        sb.append("""],""")
        sb.append(""""outbounds":[""")
        proxyOutbounds.forEachIndexed { i, outbound ->
            if (i > 0) sb.append(',')
            sb.append(outbound)
        }
        if (upstream != null) {
            sb.append(',')
            sb.append(socksOutbound("upstream", upstream.host, upstream.port))
        }
        sb.append(""",{"type":"direct","tag":"direct"}""")
        sb.append(""",{"type":"block","tag":"block"}""")
        sb.append("""],""")
        sb.append(dnsConfig(dnsServers, detour = "proxy", ipv6Enabled = ipv6Enabled))
        sb.append("\"route\":{")
        sb.append(defaultDomainResolver(ipv6Enabled))
        sb.append(""""final":"proxy",""")
        sb.append(""""auto_detect_interface":true,""")
        sb.append(""""rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}]""")
        sb.append('}')
        sb.append('}')
        return sb.toString()
    }

    private fun buildProbeFullConfig(
        inbounds: List<String>,
        proxyOutbounds: List<String>,
        routeRules: List<String>,
        dnsServers: List<String>,
        ipv6Enabled: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""log":{"level":"debug","timestamp":true},""")
        sb.append(""""inbounds":[""")
        sb.append(inbounds.joinToString(","))
        sb.append("""],""")
        sb.append(""""outbounds":[""")
        sb.append(proxyOutbounds.joinToString(","))
        sb.append(""",{"type":"direct","tag":"direct"}""")
        sb.append(""",{"type":"block","tag":"block"}""")
        sb.append("""],""")
        sb.append(dnsConfig(dnsServers, detour = null, ipv6Enabled = ipv6Enabled))
        sb.append("\"route\":{")
        sb.append(defaultDomainResolver(ipv6Enabled))
        sb.append(""""final":"block","auto_detect_interface":true,"rules":[""")
        sb.append("""{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}""")
        routeRules.forEach { rule ->
            sb.append(',')
            sb.append(rule)
        }
        sb.append("]}}")
        return sb.toString()
    }

    private fun dnsConfig(dnsServers: List<String>, detour: String?, ipv6Enabled: Boolean): String {
        val endpoints = dnsServers.mapNotNull { DnsEndpoint.parse(it, ipv6Enabled) }
        val finalTag = endpoints.indices.firstOrNull()?.let { "dns-$it" } ?: DNS_LOCAL_TAG
        val servers = buildList {
            add("{\"type\":\"local\",\"tag\":\"$DNS_LOCAL_TAG\"}")
            endpoints.mapIndexedTo(this) { index, endpoint -> dnsServer(endpoint, "dns-$index", detour) }
        }.joinToString(",")
        val strategy = if (ipv6Enabled) "" else ",\"strategy\":\"ipv4_only\""
        return "\"dns\":{\"servers\":[$servers],\"final\":${jsonString(finalTag)}$strategy},"
    }

    private fun String.isValidIpv4Dns(): Boolean {
        val parts = split('.')
        return parts.size == 4 &&
            parts.all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
            }
    }

    private fun String.isValidPlainIpv6Dns(): Boolean {
        if (!hasIpv6LiteralShape() || hasMalformedIpv6Colons()) return false
        if ('.' in this && !substringAfterLast(':').isValidIpv4Dns()) return false
        val compressed = "::" in this
        val sides = split("::", limit = 2)
        val groups = sides.sumOf(::ipv6GroupCount)
        val validParts = sides.all { it.hasValidIpv6Parts() }
        return validParts && if (compressed) groups < 8 else groups == 8
    }

    private fun String.hasIpv6LiteralShape(): Boolean =
        isNotEmpty() && '%' !in this && count { it == ':' } >= 2 && all { it.isIpv6LiteralCharacter() }

    private fun String.hasMalformedIpv6Colons(): Boolean =
        ":::" in this || countSubstring("::") > 1 || hasUnpairedEdgeColon()

    private fun String.hasUnpairedEdgeColon(): Boolean =
        startsWith(':') && !startsWith("::") || endsWith(':') && !endsWith("::")

    private fun String.hasValidIpv6Parts(): Boolean {
        if (isEmpty()) return true
        return split(':').all { part ->
            if ('.' in part) part.isValidIpv4Dns() else part.length in 1..4 && part.all { it.isIpv6HexDigit() }
        }
    }

    private fun ipv6GroupCount(side: String): Int =
        if (side.isEmpty()) 0 else side.split(':').fold(0) { count, part -> count + if ('.' in part) 2 else 1 }

    private fun String.countSubstring(value: String): Int = windowed(value.length).count { it == value }

    private fun Char.isIpv6HexDigit(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun Char.isIpv6LiteralCharacter(): Boolean = isIpv6HexDigit() || this == ':' || this == '.'

    private fun dnsServer(endpoint: DnsEndpoint, tag: String, detour: String?): String = buildString {
        append('{')
        append("\"tag\":${jsonString(tag)},")
        append("\"type\":${jsonString(endpoint.type)},")
        append("\"server\":${jsonString(endpoint.server)}")
        endpoint.serverPort?.let { append(",\"server_port\":$it") }
        endpoint.path?.let { append(",\"path\":${jsonString(it)}") }
        if (endpoint.needsDomainResolver()) append(",\"domain_resolver\":${jsonString(DNS_LOCAL_TAG)}")
        detour?.let { append(",\"detour\":${jsonString(it)}") }
        append('}')
    }

    private data class DnsEndpoint(val type: String, val server: String, val serverPort: Int?, val path: String?) {
        fun needsDomainResolver(): Boolean =
            server.isDnsHostname()

        companion object {
            fun parse(value: String, ipv6Enabled: Boolean): DnsEndpoint? {
                val input = value.trim()
                if (input.isEmpty()) return null
                val endpoint = if ("://" in input) parseUri(input) else parseLiteral(input)
                return endpoint?.takeIf { ipv6Enabled || !it.server.isValidPlainIpv6Dns() }
            }

            private fun parseLiteral(input: String): DnsEndpoint? =
                input.takeIf { it.isValidIpv4Dns() || it.isValidPlainIpv6Dns() }
                    ?.let { DnsEndpoint("udp", it, null, null) }

            private fun parseUri(input: String): DnsEndpoint? = runCatching {
                val uri = URI(input)
                val type = uri.scheme?.lowercase()?.takeIf { it in setOf("udp", "tls", "https") }
                    ?: return@runCatching null
                if (uri.userInfo != null) return@runCatching null
                val host = uri.host?.removeSurrounding("[", "]")?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val port = uri.port
                if (port != -1 && port !in MIN_PORT..MAX_PORT) return@runCatching null
                val path = if (type == "https") {
                    uri.rawPath.orEmpty().ifEmpty { "/dns-query" }.takeIf { it.startsWith('/') }
                        ?: return@runCatching null
                } else {
                    null
                }
                DnsEndpoint(type, host, port.takeIf { it != -1 }, path)
            }.getOrNull()
        }
    }

    private fun String.isDnsHostname(): Boolean = !isValidIpv4Dns() && !isValidPlainIpv6Dns()

    private fun defaultDomainResolver(ipv6Enabled: Boolean): String =
        if (ipv6Enabled) {
            "\"default_domain_resolver\":\"$DNS_LOCAL_TAG\","
        } else {
            "\"default_domain_resolver\":{\"server\":\"$DNS_LOCAL_TAG\",\"strategy\":\"ipv4_only\"},"
        }

    private fun tunInbound(ipv6Enabled: Boolean): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""type":"tun",""")
        sb.append(""""tag":"tun-in",""")
        val addresses = if (ipv6Enabled) {
            "\"172.19.0.1/30\",\"fdfe:dcba:9876::1/126\""
        } else {
            "\"172.19.0.1/30\""
        }
        sb.append("\"address\":[$addresses],")
        sb.append(""""mtu":9000,""")
        sb.append(""""auto_route":false,""")
        sb.append(""""strict_route":false""")
        sb.append('}')
        return sb.toString()
    }

    private fun socksInbound(port: Int, tag: String = "socks-in"): String = buildString {
        append("""{"type":"socks","tag":${jsonString(tag)},""")
        append(""""listen":"127.0.0.1","listen_port":$port}""")
    }

    private fun socksOutbound(tag: String, host: String, port: Int): String = buildString {
        append("""{"type":"socks","tag":${jsonString(tag)},""")
        append(""""server":${jsonString(host)},"server_port":$port}""")
    }

    private fun wireGuardOutbound(wg: WireGuardOutboundConfig, tag: String, detour: String? = null): String =
        buildString {
            append("""{"type":"wireguard","tag":${jsonString(tag)},""")
            append(""""server":${jsonString(wg.serverHost)},"server_port":${wg.serverPort},""")
            val addrs = wg.localAddresses.joinToString(",") { jsonString(it) }
            append(""""local_address":[$addrs],""")
            append(""""private_key":${jsonString(wg.privateKey)},""")
            append(""""peer_public_key":${jsonString(wg.peerPublicKey)},""")
            append(""""mtu":${wg.mtu}""")
            if (wg.keepaliveSeconds > 0) append(""","persistent_keepalive_interval":${wg.keepaliveSeconds}""")
            if (detour != null) append(""","detour":${jsonString(detour)}""")
            append('}')
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

private fun StandardV2RayBean.hasUnsupportedTcpHeader(): Boolean =
    type == "tcp" && headerType !in setOf("", "none")

private fun StandardV2RayBean.hasUnsupportedQuicSecurity(): Boolean =
    type == "quic" && quicSecurity !in setOf("", "none")

private fun StandardV2RayBean.hasMissingRealityServerName(): Boolean =
    security == "reality" && serverAddress.trim().trim('[', ']').isIpAddressForSni() && sni.isBlank() && host.isBlank()

private fun StandardV2RayBean.hasUnsupportedPacketEncoding(): Boolean =
    packetEncoding.trim().lowercase() !in setOf("", "none", "xudp", "packetaddr")

private fun StandardV2RayBean.hasUnsupportedMux(): Boolean =
    mux || singMux

private fun StandardV2RayBean.hasBrowserForwarder(): Boolean =
    wsUseBrowserForwarder || shUseBrowserForwarder

private fun StandardV2RayBean.hasUnsupportedRealityOptions(): Boolean =
    security == "reality" && realityDisableX25519Mlkem768

private fun StandardV2RayBean.hasMtls(): Boolean =
    mtlsCertificate.isNotBlank() || mtlsCertificatePrivateKey.isNotBlank()

private fun StandardV2RayBean.hasInvalidRealityPublicKey(): Boolean =
    security == "reality" && !realityPublicKey.isValidRealityPublicKey()

private fun StandardV2RayBean.hasInvalidRealityShortId(): Boolean =
    security == "reality" && !realityShortId.isValidRealityShortId()

private fun StandardV2RayBean.hasCertificatePinning(): Boolean =
    pinnedPeerCertificateChainSha256.isNotBlank() ||
        pinnedPeerCertificatePublicKeySha256.isNotBlank() ||
        pinnedPeerCertificateSha256.isNotBlank()

private fun String.isValidRealityPublicKey(): Boolean {
    val key = trim()
    if (key.isEmpty()) return false
    return runCatching {
        Base64.getUrlDecoder().decode(key).size == REALITY_PUBLIC_KEY_BYTES
    }.getOrDefault(false)
}

private fun String.isValidRealityShortId(): Boolean {
    val shortId = trim()
    return shortId.length <= 16 &&
        shortId.length % 2 == 0 &&
        shortId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

private fun vlessOutbound(bean: VLESSBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"vless","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""uuid":${jsonString(bean.uuid)},""")
    val flow = requireNotNull(normalizeVlessFlow(bean.flow)) { "Unsupported VLESS flow" }
    if (flow.isNotEmpty()) {
        sb.append(""""flow":${jsonString(flow)},""")
    }

    val transport = buildTransport(bean)
    if (transport != null) {
        sb.append(""""transport":$transport,""")
    }

    val tls = buildTls(bean)
    if (tls != null) {
        sb.append(""""tls":$tls,""")
    }

    if (detour != null) sb.append(""""detour":${jsonString(detour)},""")
    appendPacketEncoding(sb, bean.packetEncoding)
    return sb.toString()
}

private fun normalizeVlessFlow(flow: String): String? = when (val normalized = flow.trim().lowercase()) {
    "", "none" -> ""
    VLESS_FLOW_XTLS_VISION -> normalized
    else -> null
}

private fun vmessOutbound(bean: VMessBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"vmess","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""uuid":${jsonString(bean.uuid)},""")
    sb.append(""""alter_id":${bean.alterId},""")
    sb.append(""""security":${jsonString(bean.encryption.ifEmpty { "auto" })},""")

    val transport = buildTransport(bean)
    if (transport != null) sb.append(""""transport":$transport,""")

    val tls = buildTls(bean)
    if (tls != null) sb.append(""""tls":$tls,""")

    if (detour != null) sb.append(""""detour":${jsonString(detour)},""")
    appendPacketEncoding(sb, bean.packetEncoding)
    return sb.toString()
}

private fun appendPacketEncoding(builder: StringBuilder, packetEncoding: String) {
    val normalized = packetEncoding.trim().takeUnless { it.isEmpty() || it == "none" }
    if (normalized != null) builder.append(""""packet_encoding":${jsonString(normalized)},""")
    if (builder[builder.length - 1] == ',') builder.deleteCharAt(builder.length - 1)
    builder.append('}')
}

private fun trojanOutbound(bean: TrojanBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"trojan","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""password":${jsonString(bean.password)},""")

    val transport = buildTransport(bean)
    if (transport != null) sb.append(""""transport":$transport,""")

    val tls = buildTls(bean)
    if (tls != null) sb.append(""""tls":$tls,""")

    if (detour != null) sb.append(""""detour":${jsonString(detour)},""")
    if (sb[sb.length - 1] == ',') sb.deleteCharAt(sb.length - 1)
    sb.append('}')
    return sb.toString()
}

private fun shadowsocksOutbound(bean: ShadowsocksBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"shadowsocks","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""method":${jsonString(bean.method)},""")
    sb.append(""""password":${jsonString(bean.password)}""")
    if (bean.plugin.isNotEmpty()) {
        sb.append(""","plugin":${jsonString(bean.plugin)}""")
        if (bean.pluginOpts.isNotEmpty()) {
            sb.append(""","plugin_opts":${jsonString(bean.pluginOpts)}""")
        }
    }
    if (detour != null) sb.append(""","detour":${jsonString(detour)}""")
    sb.append('}')
    return sb.toString()
}

private fun buildTransport(bean: StandardV2RayBean): String? = when (bean.type) {
    "ws" -> {
        val legacyEarlyData = bean.earlyDataHeaderName.toIntOrNull().takeIf { bean.maxEarlyData <= 0 }
        buildMap(
            "type" to "ws",
            "path" to (bean.path.ifEmpty { "/" }),
            "headers" to if (bean.host.isNotEmpty()) """{"Host":${jsonString(bean.host)}}""" else "{}",
            "max_early_data" to (bean.maxEarlyData.takeIf { it > 0 } ?: legacyEarlyData ?: 0).toString(),
            "early_data_header_name" to bean.earlyDataHeaderName.takeUnless { legacyEarlyData != null }.orEmpty(),
        )
    }
    "grpc" -> buildMap(
        "type" to "grpc",
        "service_name" to bean.grpcServiceName,
    )
    "http", "h2" -> buildMap(
        "type" to "http",
        "path" to (bean.path.ifEmpty { "/" }),
        "host" to httpHostArray(bean.host),
    )
    "httpupgrade" -> buildMap(
        "type" to "httpupgrade",
        "path" to (bean.path.ifEmpty { "/" }),
        "host" to bean.host,
    )
    "tcp" -> null
    else -> null
}

private fun httpHostArray(hosts: String): String =
    hosts.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = ",", prefix = "[", postfix = "]") { jsonString(it) }

private fun buildTls(bean: StandardV2RayBean): String? {
    val security = bean.security
    if (security == "none" || security.isEmpty()) return null

    val sb = StringBuilder()
    sb.append("""{"enabled":true,""")
    val serverName = tlsServerName(bean)
    if (serverName.isNotEmpty()) sb.append(""""server_name":${jsonString(serverName)},""")

    if (bean.alpn.isNotEmpty()) {
        val alpns = bean.alpn.split(",").joinToString(",") { jsonString(it.trim()) }
        sb.append(""""alpn":[$alpns],""")
    }

    if (security == "reality") {
        sb.append(""""reality":{"enabled":true,""")
        sb.append(""""public_key":${jsonString(bean.realityPublicKey)},""")
        sb.append(""""short_id":${jsonString(bean.realityShortId)}},""")
        val fp = bean.realityFingerprint.ifEmpty { "chrome" }
        sb.append(""""utls":{"enabled":true,"fingerprint":${jsonString(fp)}},""")
        if (bean.allowInsecure) {
            sb.append(""""insecure":true,""")
        }
    } else if (security == "tls") {
        if (bean.utlsFingerprint.isNotEmpty()) {
            sb.append(""""utls":{"enabled":true,"fingerprint":${jsonString(bean.utlsFingerprint)}},""")
        }
        if (bean.allowInsecure) {
            sb.append(""""insecure":true,""")
        }
        if (bean.certificates.isNotEmpty()) {
            sb.append(""""certificate":${jsonString(bean.certificates)},""")
        }
    }

    sb.append(""""disable_sni":false}""")
    return sb.toString()
}

private fun tlsServerName(bean: StandardV2RayBean): String {
    if (bean.sni.isNotEmpty()) return bean.sni.trim()
    val host = bean.host.trim()
    val server = bean.serverAddress.trim().trim('[', ']')
    if (bean.canUseHostAsTlsServerName(host, server)) return host
    return if (server.isDomainNameForSni()) server else ""
}

private fun StandardV2RayBean.canUseHostAsTlsServerName(host: String, server: String): Boolean =
    host.isDomainNameForSni() &&
        when (security) {
            "reality" -> true
            "tls" -> server.isIpAddressForSni()
            else -> false
        }

private fun String.isIpAddressForSni(): Boolean =
    isNotEmpty() && (all { it.isDigit() || it == '.' } || ":" in this)

private fun String.isDomainNameForSni(): Boolean {
    if (isEmpty() || length > 253 || ":" in this) return false
    if (all { it.isDigit() || it == '.' }) return false
    return split('.').all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first().isLetterOrDigit() &&
            label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private fun buildMap(vararg pairs: Pair<String, String>): String {
    val fields = pairs.filter { (_, v) -> v.isNotEmpty() && v != "0" && v != "[]" && v != "{}" }
        .joinToString(",") { (k, v) ->
            val isLiteral = k == "max_early_data" ||
                v.startsWith("{") ||
                v.startsWith("[") ||
                v == "true" ||
                v == "false"
            val value = if (isLiteral) {
                v
            } else {
                jsonString(v)
            }
            "${jsonString(k)}:$value"
        }
    return "{$fields}"
}

private fun jsonString(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (c.code < 0x20) {
                sb.append("\\u%04x".format(c.code))
            } else {
                sb.append(c)
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
