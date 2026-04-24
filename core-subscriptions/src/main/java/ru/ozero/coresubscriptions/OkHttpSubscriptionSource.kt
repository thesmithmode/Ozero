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
                    FetchBytesResult.Ok(response.body?.bytes() ?: ByteArray(0))
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
    }
}
