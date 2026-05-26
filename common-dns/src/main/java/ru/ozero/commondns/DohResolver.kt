package ru.ozero.commondns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.ozero.enginescore.PersistentLoggers
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class DohResult {
    data class Ok(val addresses: List<String>) : DohResult()
    data class Failure(val reason: String, val statusCode: Int? = null) : DohResult()
}

class DohResolver(
    private val endpoint: String = CLOUDFLARE_ENDPOINT,
    private val timeoutMs: Long = 5_000L,
    private val client: OkHttpClient = defaultClient(timeoutMs),
) {

    suspend fun resolve(hostname: String): DohResult =
        buildOrFail(hostname) { DnsMessage.buildAQuery(it) }?.let { execute(it, parseV6 = false) }
            ?: DohResult.Failure("invalid hostname")

    suspend fun resolveAAAA(hostname: String): DohResult =
        buildOrFail(hostname) { DnsMessage.buildAAAAQuery(it) }?.let { execute(it, parseV6 = true) }
            ?: DohResult.Failure("invalid hostname")

    private fun buildOrFail(hostname: String, builder: (String) -> ByteArray): ByteArray? =
        try {
            builder(hostname)
        } catch (e: IllegalArgumentException) {
            PersistentLoggers.warn(TAG, "DoH invalid hostname: ${e.message}")
            null
        }

    private suspend fun execute(query: ByteArray, parseV6: Boolean): DohResult = withContext(Dispatchers.IO) {
        PersistentLoggers.info(TAG, "resolve via $endpoint v6=$parseV6")
        val request =
            Request.Builder()
                .url(endpoint)
                .header("Accept", "application/dns-message")
                .post(query.toRequestBody("application/dns-message".toMediaType()))
                .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    PersistentLoggers.warn(TAG, "DoH $endpoint HTTP ${response.code}")
                    return@use DohResult.Failure("HTTP ${response.code}", response.code)
                }
                val body = response.body?.bytes() ?: return@use DohResult.Failure("empty body")
                val addresses = if (parseV6) DnsMessage.parseAAAAAnswers(body) else DnsMessage.parseAAnswers(body)
                if (addresses.isEmpty()) DohResult.Failure("нет записей") else DohResult.Ok(addresses)
            }
        } catch (e: IOException) {
            PersistentLoggers.warn(TAG, "DoH $endpoint IO fail: ${e.message}", e)
            DohResult.Failure(e.message ?: "network error")
        }
    }

    companion object {
        const val CLOUDFLARE_ENDPOINT = "https://cloudflare-dns.com/dns-query"
        const val QUAD9_ENDPOINT = "https://dns.quad9.net/dns-query"
        private const val TAG = "DohResolver"

        fun defaultPinner(): CertificatePinner = CertificatePinner.Builder()
            .add("cloudflare-dns.com", "sha256/v3zZBT4LfPWyUJGyl0NCMCsKnaVj2UZfKUwRk4G3DuA=")
            .add("dns.quad9.net", "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY=")
            .build()

        fun defaultClient(timeoutMs: Long = 5_000L): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .certificatePinner(defaultPinner())
            .build()
    }
}
