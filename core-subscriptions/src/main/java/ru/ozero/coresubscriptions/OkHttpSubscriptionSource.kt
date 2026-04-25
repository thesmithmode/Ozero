package ru.ozero.coresubscriptions

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpSubscriptionSource(
    private val timeoutMs: Long = 10_000L,
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build(),
) : SubscriptionSource {

    override suspend fun fetch(url: String): SubscriptionFetchResult =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "fetch $url")
            val bodyResult = fetchBytes(url)
            if (bodyResult !is FetchBytesResult.Ok) {
                return@withContext toFailure(bodyResult)
            }
            val sigResult = fetchBytes("$url.sig")
            val signature = (sigResult as? FetchBytesResult.Ok)?.bytes
            SubscriptionFetchResult.Success(bodyResult.bytes, signature)
        }

    private fun fetchBytes(url: String): FetchBytesResult {
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} для $url")
                    FetchBytesResult.HttpError(response.code)
                } else {
                    val body = response.body
                    if (body == null) {
                        FetchBytesResult.Ok(ByteArray(0))
                    } else {
                        val source = body.source()
                        // Не доверяем Content-Length: пробуем запросить (limit+1) байт.
                        // Если буфер их получает → тело > лимита → отбрасываем.
                        if (source.request(maxBodyBytes + 1)) {
                            Log.w(TAG, "тело > $maxBodyBytes байт для $url — отброшено")
                            FetchBytesResult.IoError("тело превысило лимит $maxBodyBytes")
                        } else {
                            FetchBytesResult.Ok(source.readByteArray())
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "IO fail $url: ${e.message}")
            FetchBytesResult.IoError(e.message ?: "network error")
        }
    }

    private fun toFailure(result: FetchBytesResult): SubscriptionFetchResult.Failure =
        when (result) {
            is FetchBytesResult.HttpError -> SubscriptionFetchResult.Failure("HTTP ${result.code}", result.code)
            is FetchBytesResult.IoError -> SubscriptionFetchResult.Failure(result.reason)
            is FetchBytesResult.Ok -> error("unreachable")
        }

    private sealed class FetchBytesResult {
        data class Ok(val bytes: ByteArray) : FetchBytesResult()
        data class HttpError(val code: Int) : FetchBytesResult()
        data class IoError(val reason: String) : FetchBytesResult()
    }

    private companion object {
        const val TAG = "OkHttpSubscriptionSource"
        const val DEFAULT_MAX_BODY_BYTES: Long = 4L * 1024 * 1024 // 4 МБ — подписка как правило <100 КБ
    }
}
