package ru.ozero.app.selfupdate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

open class ApkDownloader(
    private val client: OkHttpClient,
    private val maxApkBytes: Long = DEFAULT_MAX_APK_BYTES,
    private val maxSigBytes: Long = DEFAULT_MAX_SIG_BYTES,
) {

    sealed class Event {
        data class Progress(val percent: Int) : Event()

        data class Success(val apk: File, val sig: File) : Event()

        data class Failed(val reason: String) : Event()
    }

    open fun download(apkUrl: String, sigUrl: String, destDir: File): Flow<Event> = flow {
        Log.i(TAG, "download start apk=$apkUrl sig=$sigUrl dst=${destDir.absolutePath}")
        if (!destDir.exists() && !destDir.mkdirs()) {
            Log.e(TAG, "mkdirs fail ${destDir.absolutePath}")
            emit(Event.Failed("cache dir недоступен"))
            return@flow
        }
        val apkFile = File(destDir, APK_NAME)
        val sigFile = File(destDir, SIG_NAME)
        if (apkFile.exists() && !apkFile.delete()) {
            Log.e(TAG, "не удалось удалить старый apk: ${apkFile.absolutePath}")
            emit(Event.Failed("apk locked, перезапустите приложение"))
            return@flow
        }
        if (sigFile.exists() && !sigFile.delete()) {
            Log.e(TAG, "не удалось удалить старую sig: ${sigFile.absolutePath}")
            emit(Event.Failed("sig locked"))
            return@flow
        }

        try {
            downloadWithProgress(apkUrl, apkFile, maxApkBytes) { pct ->
                emit(Event.Progress(pct))
            }
            downloadFlat(sigUrl, sigFile, maxSigBytes)
            Log.i(TAG, "download done apk=${apkFile.length()}B sig=${sigFile.length()}B")
            emit(Event.Success(apkFile, sigFile))
        } catch (e: IOException) {
            Log.e(TAG, "download fail", e)
            apkFile.delete()
            sigFile.delete()
            emit(Event.Failed(e.message ?: "io"))
        } catch (e: SizeLimitException) {
            Log.e(TAG, "size limit ${e.message}")
            apkFile.delete()
            sigFile.delete()
            emit(Event.Failed(e.message ?: "size"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadWithProgress(
        url: String,
        out: File,
        sizeLimit: Long,
        onProgress: suspend (Int) -> Unit,
    ) {
        executeFollowingAllowedRedirects(url).use { resp ->
            val body = ensureSuccessfulBody(resp, url)
            val total = headerSize(body, sizeLimit)
            streamWithProgress(body, out, total, sizeLimit, onProgress)
        }
    }

    private fun executeFollowingAllowedRedirects(url: String): Response {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val req = Request.Builder().url(currentUrl).build()
            val resp = client.newCall(req).execute()
            if (!resp.isRedirect) return resp
            val nextUrl = resp.use { redirectTarget(req.url, it) }
            if (redirectCount == MAX_REDIRECTS) throw IOException("too many redirects for $url")
            currentUrl = nextUrl.toString()
        }
        throw IOException("too many redirects for $url")
    }

    private fun redirectTarget(source: HttpUrl, resp: Response): HttpUrl {
        val location = resp.header("Location") ?: throw IOException("redirect without Location for $source")
        val target = source.resolve(location) ?: throw IOException("invalid redirect for $source")
        if (target.scheme != "https") throw IOException("non-https redirect for $source")
        if (target.host !in ALLOWED_DOWNLOAD_HOSTS) throw IOException("unexpected redirect host for $source")
        return target
    }

    private fun ensureSuccessfulBody(resp: Response, url: String): okhttp3.ResponseBody {
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
        return resp.body ?: throw IOException("empty body")
    }

    private fun headerSize(body: okhttp3.ResponseBody, sizeLimit: Long): Long {
        val total = body.contentLength().takeIf { it > 0 } ?: -1L
        if (total > sizeLimit) throw SizeLimitException("size $total > limit $sizeLimit")
        return total
    }

    private suspend fun streamWithProgress(
        body: okhttp3.ResponseBody,
        out: File,
        total: Long,
        sizeLimit: Long,
        onProgress: suspend (Int) -> Unit,
    ) {
        body.byteStream().use { src ->
            out.outputStream().use { sink ->
                copyAndReport(src, sink, total, sizeLimit, onProgress)
            }
        }
    }

    private suspend fun copyAndReport(
        src: java.io.InputStream,
        sink: java.io.OutputStream,
        total: Long,
        sizeLimit: Long,
        onProgress: suspend (Int) -> Unit,
    ) {
        val buf = ByteArray(BUFFER)
        var copied = 0L
        var lastPct = -1
        while (true) {
            val n = src.read(buf)
            if (n < 0) break
            copied += n
            if (copied > sizeLimit) throw SizeLimitException("copied $copied > limit $sizeLimit")
            sink.write(buf, 0, n)
            lastPct = maybeEmitPct(total, copied, lastPct, onProgress)
        }
        if (lastPct < 100) onProgress(100)
    }

    private suspend fun maybeEmitPct(
        total: Long,
        copied: Long,
        lastPct: Int,
        onProgress: suspend (Int) -> Unit,
    ): Int {
        if (total <= 0) return lastPct
        val pct = ((copied * 100L) / total).toInt().coerceIn(0, 100)
        if (pct != lastPct) {
            onProgress(pct)
            return pct
        }
        return lastPct
    }

    private fun downloadFlat(url: String, out: File, sizeLimit: Long) {
        executeFollowingAllowedRedirects(url).use { resp ->
            val body = ensureSuccessfulBody(resp, url)
            if (body.contentLength() > sizeLimit) {
                throw SizeLimitException("sig size ${body.contentLength()} > limit $sizeLimit")
            }
            val bytes = body.bytes()
            if (bytes.size.toLong() > sizeLimit) {
                throw SizeLimitException("sig actual ${bytes.size} > limit $sizeLimit")
            }
            out.writeBytes(bytes)
        }
    }

    private class SizeLimitException(msg: String) : IOException(msg)

    companion object {
        private const val TAG = "ApkDownloader"
        const val APK_NAME = "ozero-update.apk"
        const val SIG_NAME = "ozero-update.apk.sig"
        const val DEFAULT_MAX_APK_BYTES: Long = 200L * 1024 * 1024
        const val DEFAULT_MAX_SIG_BYTES: Long = 4096L
        private const val BUFFER = 64 * 1024
        private const val MAX_REDIRECTS = 5
        private val ALLOWED_DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com")
    }
}
