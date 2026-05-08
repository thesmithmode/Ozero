package ru.ozero.commondns

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        execute(DnsMessage.buildAQuery(hostname), parseV6 = false)

    suspend fun resolveAAAA(hostname: String): DohResult =
        execute(DnsMessage.buildAAAAQuery(hostname), parseV6 = true)

    private suspend fun execute(query: ByteArray, parseV6: Boolean): DohResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "resolve via $endpoint v6=$parseV6")
        val request =
            Request.Builder()
                .url(endpoint)
                .header("Accept", "application/dns-message")
                .post(query.toRequestBody("application/dns-message".toMediaType()))
                .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code}")
                    return@use DohResult.Failure("HTTP ${response.code}", response.code)
                }
                val body = response.body?.bytes() ?: return@use DohResult.Failure("empty body")
                val addresses = if (parseV6) DnsMessage.parseAAAAAnswers(body) else DnsMessage.parseAAnswers(body)
                if (addresses.isEmpty()) DohResult.Failure("нет записей") else DohResult.Ok(addresses)
            }
        } catch (e: IOException) {
            Log.w(TAG, "IO fail: ${e.message}")
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
