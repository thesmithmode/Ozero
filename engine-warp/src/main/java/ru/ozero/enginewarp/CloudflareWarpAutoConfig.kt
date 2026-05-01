package ru.ozero.enginewarp

import org.json.JSONObject
import ru.ozero.enginescore.PersistentLoggers
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CloudflareWarpAutoConfig(
    private val httpClient: HttpClient,
    private val keypairGen: WireguardKeyPairGenerator,
    private val installIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val nowProvider: () -> Date = { Date() },
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : WarpAutoConfig {

    override suspend fun register(): Result<WarpConfig> {
        val (priv, pub) = keypairGen.generate()
        val body = buildRequestBody(pub, installIdProvider(), nowProvider())
        val httpResult = httpClient.postJson(endpoint, body, userAgent)
        if (httpResult.isFailure) {
            val cause = httpResult.exceptionOrNull()
            PersistentLoggers.error(TAG, "register HTTP failure: ${cause?.message}")
            return Result.failure(cause ?: IOException("WARP register HTTP failed"))
        }
        return parseResponse(httpResult.getOrThrow(), priv, pub)
    }

    private fun buildRequestBody(pub: String, installId: String, now: Date): String {
        val tos = ISO8601.apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
        return JSONObject().apply {
            put("key", pub)
            put("install_id", "")
            put("fcm_token", "")
            put("tos", tos)
            put("model", "PC")
            put("serial_number", installId)
            put("locale", "en_US")
        }.toString()
    }

    private fun parseResponse(json: String, priv: String, pub: String): Result<WarpConfig> = try {
        val root = JSONObject(json)
        val account = root.getJSONObject("account")
        val license = account.getString("license")
        val config = root.getJSONObject("config")
        val iface = config.getJSONObject("interface")
        val addrs = iface.getJSONObject("addresses")
        val v4 = addrs.getString("v4")
        val v6 = addrs.getString("v6")
        val peers = config.getJSONArray("peers")
        if (peers.length() == 0) throw IOException("WARP response: peers empty")
        val peer = peers.getJSONObject(0)
        val peerPub = peer.getString("public_key")
        val ep = peer.getJSONObject("endpoint")
        val host = ep.getString("host")
        Result.success(
            WarpConfig(
                privateKey = priv,
                publicKey = pub,
                peerPublicKey = peerPub,
                peerEndpoint = host,
                interfaceAddressV4 = if (v4.contains("/")) v4 else "$v4/32",
                interfaceAddressV6 = if (v6.contains("/")) v6 else "$v6/128",
                accountLicense = license,
            ),
        )
    } catch (t: Throwable) {
        PersistentLoggers.error(TAG, "register parse failure: ${t.message}")
        Result.failure(IOException("WARP response parse failed: ${t.message}", t))
    }

    private companion object {
        const val TAG = "CloudflareWarpAutoConfig"
        const val DEFAULT_ENDPOINT = "https://api.cloudflareclient.com/v0a2158/reg"
        const val DEFAULT_USER_AGENT = "okhttp/3.12.1"
        val ISO8601: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    }
}
