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
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .certificatePinner(defaultPinner())
            .build(),
) {

    suspend fun resolve(hostname: String): DohResult =
        withContext(Dispatchers.IO) {
            // Логируем только endpoint, не hostname — иначе READ_LOGS/ADB видит все резолвы.
            Log.i(TAG, "resolve via $endpoint")
            val query = DnsMessage.buildAQuery(hostname)
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
                    val addresses = DnsMessage.parseAAnswers(body)
                    if (addresses.isEmpty()) DohResult.Failure("нет A-записей") else DohResult.Ok(addresses)
                }
            } catch (e: IOException) {
                Log.w(TAG, "IO fail: ${e.message}")
                DohResult.Failure(e.message ?: "network error")
            }
        }

    companion object {
        const val CLOUDFLARE_ENDPOINT = "https://1.1.1.1/dns-query"
        const val QUAD9_ENDPOINT = "https://9.9.9.9/dns-query"
        private const val TAG = "DohResolver"

        // Cloudflare/Quad9 pin текущих корневых сертификатов (SPKI SHA-256 pins).
        // Обновлять при ротации CA. Источник: https://cloudflare.com/ssl/, https://quad9.net/service/service-addresses-and-features
        private fun defaultPinner(): CertificatePinner =
            CertificatePinner.Builder()
                .add("1.1.1.1", "sha256/v3zZBT4LfPWyUJGyl0NCMCsKnaVj2UZfKUwRk4G3DuA=")
                .add("1.0.0.1", "sha256/v3zZBT4LfPWyUJGyl0NCMCsKnaVj2UZfKUwRk4G3DuA=")
                .add("9.9.9.9", "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY=")
                .add("149.112.112.112", "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY=")
                .build()
    }
}
