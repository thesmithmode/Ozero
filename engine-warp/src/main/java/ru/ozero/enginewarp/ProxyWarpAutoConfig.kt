package ru.ozero.enginewarp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import ru.ozero.enginescore.PersistentLoggers
import java.io.IOException
import java.util.Base64

class ProxyWarpAutoConfig(
    private val httpClient: HttpClient,
    private val mirrors: List<String> = DEFAULT_MIRRORS,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
    private val totalBudgetMs: Long = DEFAULT_TOTAL_BUDGET_MS,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val shuffler: (List<String>) -> List<String> = { it.shuffled() },
) : WarpAutoConfig {

    override suspend fun register(): Result<WarpConfig> {
        val ordered = shuffler(mirrors)
        if (ordered.isEmpty()) {
            return Result.failure(IOException("WARP register: список зеркал пуст"))
        }
        PersistentLoggers.info(
            TAG,
            "register: ${ordered.size} mirrors, concurrency=$concurrency, budget=${totalBudgetMs}ms",
        )
        val winner = withTimeoutOrNull(totalBudgetMs) { raceMirrors(ordered) }
        return when {
            winner == null -> {
                PersistentLoggers.error(TAG, "register: total budget exceeded (${totalBudgetMs}ms)")
                Result.failure(IOException("WARP register: бюджет ${totalBudgetMs}ms истёк"))
            }
            winner.isFailure -> {
                val cause = winner.exceptionOrNull()
                PersistentLoggers.error(TAG, "register: все зеркала отказали: ${cause?.message}")
                Result.failure(cause ?: IOException("WARP register: все зеркала отказали"))
            }
            else -> {
                PersistentLoggers.info(TAG, "register: success on mirror")
                winner
            }
        }
    }

    private suspend fun raceMirrors(ordered: List<String>): Result<WarpConfig> = coroutineScope {
        val iterator = ordered.iterator()
        val inFlight = ArrayDeque<Deferred<Result<WarpConfig>>>()
        var lastError: Throwable? = null
        while (iterator.hasNext() && inFlight.size < concurrency) {
            inFlight.add(spawnMirror(iterator.next()))
        }
        while (inFlight.isNotEmpty()) {
            val finished = select<Deferred<Result<WarpConfig>>> {
                inFlight.forEach { d ->
                    d.onAwait { d }
                }
            }
            inFlight.remove(finished)
            val r = finished.getCompleted()
            if (r.isSuccess) {
                inFlight.forEach { it.cancel() }
                return@coroutineScope r
            } else {
                lastError = r.exceptionOrNull() ?: lastError
                if (iterator.hasNext()) {
                    inFlight.add(spawnMirror(iterator.next()))
                }
            }
        }
        Result.failure(lastError ?: IOException("WARP register: все зеркала отказали"))
    }

    private fun CoroutineScope.spawnMirror(url: String): Deferred<Result<WarpConfig>> =
        async {
            try {
                val httpResult = withTimeoutOrNull(PER_MIRROR_TIMEOUT_MS) {
                    httpClient.postJson(url, REQUEST_BODY, userAgent)
                } ?: return@async Result.failure(IOException("mirror timeout: $url"))
                if (httpResult.isFailure) {
                    return@async Result.failure(
                        httpResult.exceptionOrNull()
                            ?: IOException("HTTP failure on $url"),
                    )
                }
                val body = httpResult.getOrThrow()
                val parsed = parseProxyResponse(body)
                if (parsed.isFailure) {
                    PersistentLoggers.warn(
                        TAG,
                        "mirror parse failed on $url: ${parsed.exceptionOrNull()?.message}",
                    )
                }
                parsed
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun parseProxyResponse(body: String): Result<WarpConfig> {
        val confText = extractWireguardConf(body)
            ?: return Result.failure(IOException("WARP response: [Interface] не найден"))
        return parseWireguardConf(confText)
    }

    private fun extractWireguardConf(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) {
            return findInterfaceBlock(body)
        }
        val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return findInterfaceBlock(body)
        return extractFromJson(json)
    }

    private fun extractFromJson(json: JSONObject): String? {
        if (!json.optBoolean("success", true)) {
            PersistentLoggers.warn(TAG, "mirror reported failure: ${json.optString("message", "")}")
            return null
        }
        json.optJSONObject("content")?.let { content ->
            val b64 = content.optString("configBase64", "")
            if (b64.isNotBlank()) {
                return runCatching {
                    String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
                }.getOrNull()?.let { findInterfaceBlock(it) ?: it }
            }
        }
        sequenceOf("data", "config", "wireguard", "conf").forEach { key ->
            findInterfaceBlock(json.optString(key))?.let { return it }
        }
        return findConfInNestedObject(json)
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val eq = line.indexOf('=')
        if (eq <= 0) {
            return null
        }
        return line.substring(0, eq).trim().lowercase() to line.substring(eq + 1).trim()
    }

    private fun applyKeyValue(
        line: String,
        section: String?,
        iface: MutableMap<String, String>,
        peer: MutableMap<String, String>,
    ) {
        val kv = parseKeyValue(line) ?: return
        when (section) {
            "iface" -> iface[kv.first] = kv.second
            "peer" -> peer[kv.first] = kv.second
            else -> Unit
        }
    }

    private fun findConfInNestedObject(json: JSONObject): String? {
        sequenceOf("data", "configs", "config").forEach { key ->
            val nested = json.optJSONObject(key) ?: return@forEach
            val names = nested.names() ?: return@forEach
            for (i in 0 until names.length()) {
                val childKey = names.optString(i)
                if (childKey.isNullOrEmpty()) {
                    continue
                }
                findInterfaceBlock(nested.optString(childKey))?.let { return it }
            }
        }
        return null
    }

    private fun findInterfaceBlock(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val idx = text.indexOf("[Interface]")
        if (idx < 0) return null
        return text.substring(idx)
    }

    private fun parseWireguardConf(conf: String): Result<WarpConfig> = try {
        val iface = mutableMapOf<String, String>()
        val peer = mutableMapOf<String, String>()
        var section: String? = null
        conf.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) {
                return@forEach
            }
            when {
                line.equals("[Interface]", ignoreCase = true) -> section = "iface"
                line.equals("[Peer]", ignoreCase = true) -> section = "peer"
                else -> applyKeyValue(line, section, iface, peer)
            }
        }
        val priv = iface["privatekey"]
            ?: throw IOException("WireGuard conf: PrivateKey отсутствует")
        val addresses = iface["address"]
            ?: throw IOException("WireGuard conf: Address отсутствует")
        val (v4, v6) = splitAddresses(addresses)
        val peerPub = peer["publickey"]
            ?: throw IOException("WireGuard conf: PublicKey peer отсутствует")
        val endpoint = peer["endpoint"]
            ?: throw IOException("WireGuard conf: Endpoint peer отсутствует")
        val mtu = iface["mtu"]?.toIntOrNull() ?: DEFAULT_MTU
        val dnsServers = parseDnsServers(iface["dns"])
        val keepalive = peer["persistentkeepalive"]?.toIntOrNull() ?: WarpConfig.DEFAULT_KEEPALIVE
        val awgParams = parseAwgParams(iface)
        Result.success(
            WarpConfig(
                privateKey = priv,
                publicKey = "",
                peerPublicKey = peerPub,
                peerEndpoint = endpoint,
                interfaceAddressV4 = v4,
                interfaceAddressV6 = v6,
                accountLicense = "",
                mtu = mtu,
                dnsServers = dnsServers,
                keepaliveSeconds = keepalive,
                awgParams = awgParams,
            ),
        )
    } catch (t: Throwable) {
        Result.failure(IOException("WireGuard conf parse: ${t.message}", t))
    }

    private fun splitAddresses(addresses: String): Pair<String, String> {
        val parts = addresses.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val v4 = parts.firstOrNull { ":" !in it } ?: ""
        val v6 = parts.firstOrNull { ":" in it } ?: ""
        return toCidr(v4, "/32") to toCidr(v6, "/128")
    }

    private fun parseAwgParams(iface: Map<String, String>): AwgParams = AwgParams(
        junkPacketCount = iface["jc"]?.toIntOrNull() ?: AwgParams.DEFAULT_JC,
        junkPacketMinSize = iface["jmin"]?.toIntOrNull() ?: AwgParams.DEFAULT_JMIN,
        junkPacketMaxSize = iface["jmax"]?.toIntOrNull() ?: AwgParams.DEFAULT_JMAX,
        initPacketJunkSize = iface["s1"]?.toIntOrNull() ?: AwgParams.DEFAULT_S1,
        responsePacketJunkSize = iface["s2"]?.toIntOrNull() ?: AwgParams.DEFAULT_S2,
        initPacketMagicHeader = iface["h1"]?.toLongOrNull() ?: AwgParams.DEFAULT_H1,
        responsePacketMagicHeader = iface["h2"]?.toLongOrNull() ?: AwgParams.DEFAULT_H2,
        cookieReplyMagicHeader = iface["h3"]?.toLongOrNull() ?: AwgParams.DEFAULT_H3,
        transportMagicHeader = iface["h4"]?.toLongOrNull() ?: AwgParams.DEFAULT_H4,
    )

    private fun toCidr(addr: String, suffix: String): String = when {
        addr.contains("/") -> addr
        addr.isNotBlank() -> "$addr$suffix"
        else -> addr
    }

    private fun parseDnsServers(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return WarpConfig.DEFAULT_DNS
        val servers = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return servers.ifEmpty { WarpConfig.DEFAULT_DNS }
    }

    companion object {
        const val TAG = "ProxyWarpAutoConfig"
        const val DEFAULT_USER_AGENT = "okhttp/3.12.1"
        const val DEFAULT_CONCURRENCY = 8
        const val DEFAULT_TOTAL_BUDGET_MS = 240_000L
        const val DEFAULT_MTU = 1280
        const val PER_MIRROR_TIMEOUT_MS = 45_000L
        const val REQUEST_BODY =
            "{\"selectedServices\":[],\"siteMode\":\"all\"," +
                "\"deviceType\":\"computer\",\"endpoint\":\"162.159.195.1:500\"}"

        val DEFAULT_MIRRORS: List<String> = listOf(
            "https://vless-portal.netlify.app/api/warp",
            "https://portal-warp.netlify.app/api/warp",
            "https://warp-vless.netlify.app/api/warp",
            "https://portal-vless.netlify.app/api/warp",
            "https://warp-porta.netlify.app/api/warp",
            "https://warpgen.netlify.app/api/warp",
            "https://portawg11.netlify.app/api/warp",
            "https://warp-vless1.netlify.app/api/warp",
            "https://strugov.netlify.app/api/warp",
            "https://portawg1.netlify.app/api/warp",
            "https://portalwg.netlify.app/api/warp",
            "https://warp-gen1.vercel.app/api/warp",
            "https://vip-str.vercel.app/api/warp",
            "https://vless-warp.vercel.app/api/warp",
            "https://wgportal.vercel.app/api/warp",
            "https://warp1-eta.vercel.app/api/warp",
            "https://warp-ge.vercel.app/api/warp",
            "https://str-vip.vercel.app/api/warp",
            "https://warp-vless.vercel.app/api/warp",
            "https://vipwarp.vercel.app/api/warp",
            "https://warp-g.vercel.app/api/warp",
            "https://port-vip.vercel.app/api/warp",
            "https://warp-vless22.vercel.app/api/warp",
            "https://warp-vless11.vercel.app/api/warp",
            "https://warp-vless55.vercel.app/api/warp",
            "https://warp-gen11.vercel.app/api/warp",
            "https://warp-vless77.vercel.app/api/warp",
            "https://warp-vless66.vercel.app/api/warp",
            "https://warp-vless44.vercel.app/api/warp",
            "https://warp-vless33.vercel.app/api/warp",
            "https://warp-vless12.vercel.app/api/warp",
            "https://portal-nexus-opal.vercel.app/api/warp",
            "https://portal-matrix.vercel.app/api/warp",
            "https://portal-flux-rose.vercel.app/api/warp",
            "https://shadowportal.vercel.app/api/warp",
            "https://portal-infinity.vercel.app/api/warp",
            "https://portal-reactor.vercel.app/api/warp",
            "https://darknet-portal-seven.vercel.app/api/warp",
            "https://portal-pulse-theta.vercel.app/api/warp",
            "https://cyberportal-x.vercel.app/api/warp",
            "https://cyberportal-core.vercel.app/api/warp",
            "https://cyberlink-two.vercel.app/api/warp",
            "https://wgconnect.vercel.app/api/warp",
            "https://kiberport.vercel.app/api/warp",
            "https://cyber-portal-ten.vercel.app/api/warp",
            "https://kiberportal.vercel.app/api/warp",
            "https://hyperportal-sable.vercel.app/api/warp",
            "https://neuroportal.vercel.app/api/warp",
            "https://portalcyber.vercel.app/api/warp",
            "https://cybportal.vercel.app/api/warp",
            "https://wg-gate.vercel.app/api/warp",
            "https://shadowportal.netlify.app/api/warp",
            "https://portal-flux.netlify.app/api/warp",
            "https://ghostportal.netlify.app/api/warp",
            "https://portal-void.netlify.app/api/warp",
            "https://portal-matrix.netlify.app/api/warp",
            "https://neonportal.netlify.app/api/warp",
            "https://bypass-portal.netlify.app/api/warp",
            "https://portal-nexus.netlify.app/api/warp",
            "https://cyberportal-core.netlify.app/api/warp",
            "https://dapper-brioche-3cb6bb.netlify.app/api/warp",
            "https://portal-infinity.netlify.app/api/warp",
            "https://portal-reactor.netlify.app/api/warp",
            "https://warp.cyberportal.workers.dev/api/warp",
            "https://kiber.cyberportal.workers.dev/api/warp",
            "https://vless.cyberportal.workers.dev/api/warp",
            "https://super.cyberportal.workers.dev/api/warp",
            "https://zet.cyberportal.workers.dev/api/warp",
            "https://vip-portal.cyberportal.workers.dev/api/warp",
            "https://xxx.cyberportal.workers.dev/api/warp",
            "https://test.cyberportal.workers.dev/api/warp",
            "https://zero.cyberportal.workers.dev/api/warp",
            "https://pro.cyberportal.workers.dev/api/warp",
            "https://vip-port.cyberportal.workers.dev/api/warp",
            "https://github.cyberportal.workers.dev/api/warp",
            "https://vip.cyberportal.workers.dev/api/warp",
            "https://portal.cyberportal.workers.dev/api/warp",
            "https://str.cyberportal.workers.dev/api/warp",
            "https://warp-vless.cyberportal.workers.dev/api/warp",
            "https://git.cyberportal.workers.dev/api/warp",
        )
    }
}
