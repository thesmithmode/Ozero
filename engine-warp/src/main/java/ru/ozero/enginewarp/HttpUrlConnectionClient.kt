package ru.ozero.enginewarp

import kotlinx.coroutines.Dispatchers
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
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in HTTP_OK_RANGE) {
                    throw IOException("HTTP $code: $text")
                }
                text
            } finally {
                conn.disconnect()
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000
        val HTTP_OK_RANGE = 200..299
    }
}
