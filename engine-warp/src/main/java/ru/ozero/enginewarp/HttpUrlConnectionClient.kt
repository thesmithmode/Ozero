package ru.ozero.enginewarp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlConnectionClient(
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : HttpClient {

    override suspend fun postJson(
        url: String,
        body: String,
        userAgent: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            val cancelHook = currentCoroutineContext().job.invokeOnCompletion {
                runCatching { conn.disconnect() }
            }
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", userAgent)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in HTTP_OK_RANGE) conn.inputStream else conn.errorStream
                val text = stream?.use { readBounded(it, MAX_RESPONSE_BYTES) }.orEmpty()
                if (code !in HTTP_OK_RANGE) {
                    throw IOException("HTTP $code: $text")
                }
                text
            } finally {
                cancelHook.dispose()
                conn.disconnect()
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000
        const val MAX_RESPONSE_BYTES = 524_288L
        val HTTP_OK_RANGE = 200..299

        fun readBounded(stream: java.io.InputStream, maxBytes: Long): String {
            val buf = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) {
                    throw IOException("response too large: > $maxBytes bytes")
                }
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
