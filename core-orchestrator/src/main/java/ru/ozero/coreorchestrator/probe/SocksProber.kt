package ru.ozero.coreorchestrator.probe

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.coreapi.ProbeResult
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * SOCKS-probe без поднятия TUN-интерфейса.
 *
 * Engine стартует как локальный SOCKS5 сервер на 127.0.0.1:[socksPort],
 * прообер делает HEAD/GET через `Proxy(SOCKS, ...)` к probe-target
 * и измеряет реальный E2E latency.
 *
 * Контракт: первый успешный target → Success(latency). Иначе → Failure
 * с причиной последней попытки.
 */
class SocksProber(
    private val targets: List<ProbeTarget> = DEFAULT_TARGETS,
    private val timeoutMs: Long = 5_000L,
    private val connector: HttpConnector = JdkHttpConnector(),
) {

    suspend fun probe(socksPort: Int): ProbeResult {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        if (targets.isEmpty()) return ProbeResult.Failure("нет probe targets")

        var lastReason = "не запускался"
        for (target in targets) {
            val result = withTimeoutOrNull(timeoutMs) {
                runCatching { connector.connect(target, socksPort) }
            }
            when {
                result == null -> {
                    lastReason = "timeout target=${target.host}"
                    Log.w(TAG, lastReason)
                }
                result.isFailure -> {
                    lastReason = "fail target=${target.host}: ${result.exceptionOrNull()?.message}"
                    Log.w(TAG, lastReason)
                }
                else -> {
                    val latency = result.getOrThrow()
                    Log.i(TAG, "OK target=${target.host} latency=${latency}мс")
                    return ProbeResult.Success(latencyMs = latency)
                }
            }
        }
        return ProbeResult.Failure(lastReason)
    }

    fun interface HttpConnector {
        /** Возвращает latency в мс, кидает исключение при сетевой ошибке. */
        suspend fun connect(target: ProbeTarget, socksPort: Int): Long
    }

    class JdkHttpConnector(
        // Внутренние таймауты ≤ половины timeoutMs из withTimeoutOrNull, чтобы дать
        // connect/read шанс отработать до внешней отмены (раньше connect=4s+read=4s
        // = 8s, при timeoutMs=5000 внешний выключатель срабатывал первым).
        private val perLegTimeoutMs: Int = 2_000,
    ) : HttpConnector {
        override suspend fun connect(target: ProbeTarget, socksPort: Int): Long =
            withContext(Dispatchers.IO) {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                val started = System.currentTimeMillis()
                val conn = URL(target.url).openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = perLegTimeoutMs
                conn.readTimeout = perLegTimeoutMs
                conn.requestMethod = target.method
                conn.instanceFollowRedirects = false
                try {
                    conn.connect()
                    val code = conn.responseCode
                    if (code !in 200..399) {
                        throw IllegalStateException("HTTP $code")
                    }
                    System.currentTimeMillis() - started
                } finally {
                    conn.disconnect()
                }
            }
    }

    companion object {
        private const val TAG = "SocksProber"
        val DEFAULT_TARGETS = listOf(
            ProbeTarget(host = "youtube.com", url = "https://www.youtube.com/generate_204", method = "HEAD"),
            ProbeTarget(host = "discord.com", url = "https://discord.com/api/v9/", method = "HEAD"),
            ProbeTarget(host = "github.com", url = "https://api.github.com/", method = "HEAD"),
        )
    }
}

data class ProbeTarget(val host: String, val url: String, val method: String = "HEAD")
