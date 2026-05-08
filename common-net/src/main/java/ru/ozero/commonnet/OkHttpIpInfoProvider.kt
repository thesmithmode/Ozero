package ru.ozero.commonnet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpIpInfoProvider(
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : IpInfoProvider {

    override suspend fun fetch(): Result<IpInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("empty body")
                parse(body, clock())
            }
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://ipapi.co/json/"
        const val USER_AGENT = "Ozero-IpInfo/1"
        const val CONNECT_TIMEOUT_S = 10L
        const val READ_TIMEOUT_S = 10L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
}

internal fun parse(json: String, fetchedAtMs: Long): IpInfo {
    val ip = extractString(json, "ip") ?: throw java.io.IOException("ip missing")
    return IpInfo(
        ip = ip,
        country = extractString(json, "country_name"),
        countryCode = extractString(json, "country_code"),
        city = extractString(json, "city"),
        fetchedAtMs = fetchedAtMs,
    )
}

private fun extractString(json: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}
