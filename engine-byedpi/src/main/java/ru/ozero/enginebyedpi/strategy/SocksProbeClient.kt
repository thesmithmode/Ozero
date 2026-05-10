package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class ProbeResult(
    val site: String,
    val success: Boolean,
    val durationMs: Long,
    val responseCode: Int = -1,
    val declaredLength: Long = -1L,
    val actualLength: Long = -1L,
    val error: String? = null,
)

interface SocksProbeClient {
    suspend fun probe(site: String): ProbeResult
}

class HttpSocksProbeClient(
    private val proxyHost: String = "127.0.0.1",
    private val proxyPort: Int,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxBytesToRead: Long = DEFAULT_MAX_BYTES,
    private val urlOpener: (URL, Proxy) -> HttpURLConnection = { url, proxy ->
        url.openConnection(proxy) as HttpURLConnection
    },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : SocksProbeClient {

    override suspend fun probe(site: String): ProbeResult = withContext(Dispatchers.IO) {
        val started = nowMs()
        val url = runCatching { URL(formatUrl(site)) }
            .getOrElse {
                return@withContext ProbeResult(
                    site = site,
                    success = false,
                    durationMs = nowMs() - started,
                    error = "invalid url: ${it.message}",
                )
            }

        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))
        var connection: HttpURLConnection? = null
        try {
            connection = urlOpener(url, proxy)
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Connection", "close")

            val responseCode = connection.responseCode
            val declaredLength = connection.contentLengthLong

            val actualLength = readUntilLimit(connection, responseCode)

            val isComplete = declaredLength <= 0L || actualLength >= declaredLength
            ProbeResult(
                site = site,
                success = isComplete,
                durationMs = nowMs() - started,
                responseCode = responseCode,
                declaredLength = declaredLength,
                actualLength = actualLength,
            )
        } catch (e: Exception) {
            ProbeResult(
                site = site,
                success = false,
                durationMs = nowMs() - started,
                error = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun readUntilLimit(connection: HttpURLConnection, responseCode: Int): Long {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return 0L
        val declaredLength = connection.contentLengthLong
        val limit = if (declaredLength > 0L) declaredLength else maxBytesToRead
        val buffer = ByteArray(BUFFER_SIZE)
        var actualLength = 0L
        try {
            while (actualLength < limit) {
                val remaining = limit - actualLength
                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                val bytesRead = stream.read(buffer, 0, toRead)
                if (bytesRead == -1) break
                actualLength += bytesRead
            }
        } catch (_: IOException) {
        }
        return actualLength
    }

    private fun formatUrl(site: String): String =
        if (site.startsWith("http://") || site.startsWith("https://")) site else "https://$site"

    companion object {
        const val DEFAULT_TIMEOUT_MS: Int = 5_000
        const val DEFAULT_MAX_BYTES: Long = 1L * 1024 * 1024
        private const val BUFFER_SIZE: Int = 8192
    }
}
