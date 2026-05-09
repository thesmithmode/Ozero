package ru.ozero.commonnet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class OkHttpIpInfoProvider(
    private val client: OkHttpClient = defaultClient(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : IpInfoProvider {

    constructor(
        client: OkHttpClient = defaultClient(),
        endpoint: String,
        clock: () -> Long = { System.currentTimeMillis() },
    ) : this(client, listOf(endpoint), clock)

    override suspend fun fetch(): Result<IpInfo> = doFetchSequential(client)

    override suspend fun fetchVia(socksHost: String?, socksPort: Int?): Result<IpInfo> {
        val effective = if (socksHost != null && socksPort != null && socksPort > 0) {
            client.newBuilder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort)))
                .build()
        } else {
            client
        }
        return doFetchSequential(effective)
    }

    override suspend fun fetchViaSocketFactory(socketFactory: SocketFactory?): Result<IpInfo> {
        val effective = if (socketFactory != null) {
            client.newBuilder().socketFactory(socketFactory).build()
        } else {
            client
        }
        return doFetchSequential(effective)
    }

    private suspend fun doFetchSequential(httpClient: OkHttpClient): Result<IpInfo> {
        require(endpoints.isNotEmpty()) { "endpoints list cannot be empty" }
        var lastFailure: Throwable? = null
        for (endpoint in endpoints) {
            val result = doFetchOne(httpClient, endpoint)
            result.onSuccess { return Result.success(it) }
            result.onFailure { lastFailure = it }
        }
        return Result.failure(lastFailure ?: IOException("no endpoints succeeded"))
    }

    private suspend fun doFetchOne(httpClient: OkHttpClient, endpoint: String): Result<IpInfo> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                val info = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) throw IOException("empty body")
                    parse(body, clock())
                }
                Result.success(info)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private companion object {
        val DEFAULT_ENDPOINTS = listOf("https://ifconfig.co/json", "https://ipapi.co/json/")
        const val USER_AGENT = "Ozero-IpInfo/2"
        const val CONNECT_TIMEOUT_S = 5L
        const val READ_TIMEOUT_S = 5L

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
        country = extractString(json, "country_name") ?: extractString(json, "country"),
        countryCode = extractString(json, "country_code") ?: extractString(json, "country_iso"),
        city = extractString(json, "city"),
        fetchedAtMs = fetchedAtMs,
    )
}

private fun extractString(json: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}
