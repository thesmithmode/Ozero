package ru.ozero.enginewarp

import android.util.Log
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
    private val ranker: MirrorRanker = NoopMirrorRanker,
    private val shuffler: ((List<String>) -> List<String>)? = null,
) : WarpAutoConfig {

    @Volatile private var lastSuccessMs: Long = 0L

    override fun remainingCooldownMs(): Long = maxOf(0L, COOLDOWN_MS - (System.currentTimeMillis() - lastSuccessMs))

    override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> {
        val cooldown = remainingCooldownMs()
        if (cooldown > 0) {
            Log.i(TAG, "register: кулдаун ${cooldown / 1000}с, пропуск")
            return Result.failure(IOException("WARP auto-register: кулдаун ${cooldown / 1000}с"))
        }
        val ordered = shuffler?.invoke(mirrors) ?: ranker.order(mirrors)
        if (ordered.isEmpty()) {
            return Result.failure(IOException("WARP register: список зеркал пуст"))
        }
        val total = ordered.size
        Log.i(
            TAG,
            "register: $total mirrors, concurrency=$concurrency, budget=${totalBudgetMs}ms",
        )
        val winner = withTimeoutOrNull(totalBudgetMs) { raceMirrors(ordered, total, onProgress) }
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
                lastSuccessMs = System.currentTimeMillis()
                Log.i(TAG, "register: success on mirror")
                winner
            }
        }
    }

    private suspend fun raceMirrors(
        ordered: List<String>,
        total: Int,
        onProgress: ((String) -> Unit)?,
    ): Result<RegisteredWarpConfig> = coroutineScope {
        val iterator = ordered.iterator()
        val inFlight = ArrayDeque<Deferred<Result<RegisteredWarpConfig>>>()
        var lastError: Throwable? = null
        var tried = 0
        while (iterator.hasNext() && inFlight.size < concurrency) {
            inFlight.add(spawnMirror(iterator.next()))
        }
        while (inFlight.isNotEmpty()) {
            val finished = select<Deferred<Result<RegisteredWarpConfig>>> {
                inFlight.forEach { d ->
                    d.onAwait { d }
                }
            }
            inFlight.remove(finished)
            tried++
            val r = finished.getCompleted()
            if (r.isSuccess) {
                onProgress?.invoke("$tried/$total")
                inFlight.forEach { it.cancel() }
                return@coroutineScope r
            } else {
                onProgress?.invoke("$tried/$total")
                lastError = r.exceptionOrNull() ?: lastError
                if (iterator.hasNext()) {
                    inFlight.add(spawnMirror(iterator.next()))
                }
            }
        }
        Result.failure(lastError ?: IOException("WARP register: все зеркала отказали"))
    }

    private fun CoroutineScope.spawnMirror(url: String): Deferred<Result<RegisteredWarpConfig>> =
        async {
            try {
                val tag = mirrorTag(url)
                val httpResult = withTimeoutOrNull(PER_MIRROR_TIMEOUT_MS) {
                    httpClient.postJson(url, requestBodyFor(url), userAgent)
                } ?: run {
                    ranker.recordFailure(url)
                    return@async Result.failure(IOException("mirror timeout [$tag]"))
                }
                if (httpResult.isFailure) {
                    ranker.recordFailure(url)
                    return@async Result.failure(
                        httpResult.exceptionOrNull()
                            ?: IOException("HTTP failure [$tag]"),
                    )
                }
                val body = httpResult.getOrThrow()
                val parsed = parseProxyResponse(body)
                if (parsed.isSuccess) {
                    ranker.recordSuccess(url)
                } else {
                    ranker.recordFailure(url)
                    PersistentLoggers.warn(
                        TAG,
                        "mirror parse failed [$tag]: ${parsed.exceptionOrNull()?.message}",
                    )
                }
                parsed
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                ranker.recordFailure(url)
                Result.failure(t)
            }
        }

    private data class ExtractedIni(val text: String, val source: String)

    private fun mirrorTag(url: String): String = "m%08x".format(url.hashCode())

    private fun parseProxyResponse(body: String): Result<RegisteredWarpConfig> {
        val extracted = extractIniFromBody(body)
            ?: return Result.failure(IOException("WARP response: [Interface] не найден"))
        Log.i(TAG, "selected ${extracted.source}")
        return WarpConfParser.parse(extracted.text).mapCatching { config ->
            validateCloudflarePeer(config)
            validateFullTunnelRoutes(config)
            RegisteredWarpConfig(config = config, rawIni = extracted.text)
        }
    }

    private fun validateCloudflarePeer(config: WarpConfig) {
        if (config.peerPublicKey != CLOUDFLARE_WARP_PEER_PUBKEY) {
            throw IOException(
                "mirror peerPublicKey mismatch: ${config.peerPublicKey.take(12)}… " +
                    "expected Cloudflare WARP key — supply chain risk",
            )
        }
        val host = config.peerEndpoint.substringBeforeLast(":").trim().lowercase()
        if (host != CLOUDFLARE_WARP_PEER_HOST && !isCloudflareWarpIp(host)) {
            throw IOException(
                "mirror peer host mismatch: '$host' " +
                    "expected '$CLOUDFLARE_WARP_PEER_HOST' or Cloudflare WARP IP — supply chain risk",
            )
        }
    }

    private fun validateFullTunnelRoutes(config: WarpConfig) {
        val hasFullTunnelV4 = config.allowedIps.any { it.trim() == "0.0.0.0/0" }
        val hasFullTunnelV6 = config.interfaceAddressV6.isBlank() ||
            config.allowedIps.any { it.trim() == "::/0" }
        if (!hasFullTunnelV4 || !hasFullTunnelV6) {
            throw IOException("mirror AllowedIPs must keep WARP full-tunnel routes")
        }
    }

    private fun isCloudflareWarpIp(host: String): Boolean {
        val parts = host.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return (parts[0] == 162 && parts[1] == 159 && parts[2] in 192..195) ||
            (parts[0] == 188 && parts[1] == 114 && parts[2] in 96..99)
    }

    private fun extractIniFromBody(body: String): ExtractedIni? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) {
            return findInterfaceBlock(body)?.let { ExtractedIni(it, "raw INI") }
        }
        val json = runCatching { JSONObject(trimmed) }.getOrNull()
            ?: return findInterfaceBlock(body)?.let { ExtractedIni(it, "raw INI") }
        return extractFromJson(json)
    }

    private fun extractFromJson(json: JSONObject): ExtractedIni? {
        if (json.has("success") && !json.getBoolean("success")) {
            PersistentLoggers.warn(TAG, "mirror reported failure: ${json.optString("message", "")}")
            return null
        }
        json.optJSONObject("content")?.let { content ->
            content.optString("amQuick", "").takeIf { it.isNotBlank() }?.let { am ->
                findInterfaceBlock(am)?.let { return ExtractedIni(it, "amQuick") }
            }
            content.optString("wgQuick", "").takeIf { it.isNotBlank() }?.let { wg ->
                findInterfaceBlock(wg)?.let { return ExtractedIni(it, "wgQuick") }
            }
            val b64 = content.optString("configBase64", "")
            if (b64.isNotBlank()) {
                return runCatching {
                    String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
                }.getOrNull()?.let { decoded ->
                    val ini = findInterfaceBlock(decoded) ?: decoded
                    ExtractedIni(ini, "configBase64")
                }
            }
        }
        sequenceOf("data", "config", "wireguard", "conf").forEach { key ->
            findInterfaceBlock(json.optString(key))?.let {
                return ExtractedIni(it, "json.$key")
            }
        }
        return findConfInNestedObject(json)
    }

    private fun findConfInNestedObject(json: JSONObject): ExtractedIni? {
        sequenceOf("data", "configs", "config").forEach { key ->
            val nested = json.optJSONObject(key) ?: return@forEach
            val names = nested.names() ?: return@forEach
            for (i in 0 until names.length()) {
                val childKey = names.optString(i)
                if (childKey.isNullOrEmpty()) continue
                findInterfaceBlock(nested.optString(childKey))?.let {
                    return ExtractedIni(it, "json.$key[$childKey]")
                }
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

    companion object {
        private const val TAG = "ProxyWarpAutoConfig"
        const val CLOUDFLARE_WARP_PEER_PUBKEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
        const val CLOUDFLARE_WARP_PEER_HOST = "engage.cloudflareclient.com"
        const val COOLDOWN_MS = 5 * 60 * 1000L
        const val DEFAULT_USER_AGENT = "okhttp/3.12.1"
        const val DEFAULT_CONCURRENCY = 8
        const val DEFAULT_TOTAL_BUDGET_MS = 90_000L
        const val PER_MIRROR_TIMEOUT_MS = 12_000L
        const val REQUEST_BODY =
            "{\"selectedServices\":[],\"siteMode\":\"all\"," +
                "\"deviceType\":\"computer\",\"endpoint\":\"162.159.195.1:500\"}"
        const val GENERATOR_REQUEST_BODY =
            "{\"selectedServices\":[],\"siteMode\":\"all\"," +
                "\"deviceType\":\"awg15\",\"endpoint\":\"162.159.195.1:500\"," +
                "\"configFormat\":\"wireguard\",\"ipv6\":true}"

        fun requestBodyFor(url: String): String =
            if (url.endsWith("/api/generate")) GENERATOR_REQUEST_BODY else REQUEST_BODY

        val DEFAULT_MIRRORS: List<String> = listOf(
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
            "https://vless-portal.netlify.app/api/warp",
            "https://portal-warp.netlify.app/api/warp",
            "https://warp-vless.netlify.app/api/warp",
            "https://portal-vless.netlify.app/api/warp",
            "https://warp-porta.netlify.app/api/warp",
            "https://warpgen.netlify.app/api/warp",
            "https://warp3.llimonix.pw/api/generate",
            "https://warply2.vercel.app/api/generate",
            "https://getwarp2.netlify.app/api/generate",
            "https://warp.llimonix.workers.dev/api/generate",
        )
    }
}
