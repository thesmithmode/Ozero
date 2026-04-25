package ru.ozero.app.ui.diag

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

sealed class DiagResult {
    abstract val url: String
    data class Success(override val url: String, val latencyMs: Long, val httpCode: Int) : DiagResult()
    data class Failure(override val url: String, val reason: String) : DiagResult()
}

/**
 * HEAD-запрос к каждому URL через активный SOCKS-прокси Orchestrator. Параллельно (5 одновременно).
 * Используется в DiagnosticsScreen для теста "работает ли VPN".
 */
class DiagnosticsTester(
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int,
    private val timeoutSec: Long = 10,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) {
    private val semaphore = Semaphore(concurrency)

    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort)))
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .build()
    }

    suspend fun runAll(urls: List<String> = DiagnosticTargets.URLS): List<DiagResult> = coroutineScope {
        urls.map { url ->
            async(Dispatchers.IO) { semaphore.withPermit { test(url) } }
        }.awaitAll()
    }

    private suspend fun test(url: String): DiagResult = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).head().build()
        val start = System.currentTimeMillis()
        try {
            client.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - start
                Log.d(TAG, "diag $url: HTTP ${resp.code} ${latency}ms")
                DiagResult.Success(url, latency, resp.code)
            }
        } catch (e: Exception) {
            Log.w(TAG, "diag $url failed: ${e.message}")
            DiagResult.Failure(url, e.message ?: "unknown")
        }
    }

    private companion object {
        const val TAG = "DiagnosticsTester"
        const val DEFAULT_CONCURRENCY = 5
    }
}
