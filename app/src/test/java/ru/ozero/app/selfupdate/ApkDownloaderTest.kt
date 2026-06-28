package ru.ozero.app.selfupdate

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApkDownloaderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `download success emits progress and success`() = runTest {
        val downloader = ApkDownloader(
            client = clientOf(
                "https://x/a.apk" to ResponseSpec(200, ByteArray(4) { it.toByte() }),
                "https://x/a.sig" to ResponseSpec(200, ByteArray(3) { (it + 10).toByte() }),
            )
        )

        val events = downloader.download("https://x/a.apk", "https://x/a.sig", tempDir.toFile()).toList()

        assertTrue(events.first() is ApkDownloader.Event.Progress)
        assertIs<ApkDownloader.Event.Success>(events.last())
        assertTrue(File(tempDir.toFile(), ApkDownloader.APK_NAME).exists())
        assertTrue(File(tempDir.toFile(), ApkDownloader.SIG_NAME).exists())
    }

    @Test
    fun `download http error emits failed and cleans partial files`() = runTest {
        val dest = tempDir.toFile()
        val downloader = ApkDownloader(
            client = clientOf(
                "https://x/a.apk" to ResponseSpec(500, "boom".encodeToByteArray()),
            )
        )

        val events = downloader.download("https://x/a.apk", "https://x/a.sig", dest).toList()

        val failed = assertIs<ApkDownloader.Event.Failed>(events.last())
        assertTrue(failed.reason.contains("HTTP 500"))
        assertTrue(File(dest, ApkDownloader.APK_NAME).notExists())
        assertTrue(File(dest, ApkDownloader.SIG_NAME).notExists())
    }

    @Test
    fun `download size limit emits failed and removes partial apk`() = runTest {
        val dest = tempDir.toFile()
        val downloader = ApkDownloader(
            client = clientOf(
                "https://x/a.apk" to ResponseSpec(200, ByteArray(8) { 1 }),
            ),
            maxApkBytes = 4,
        )

        val events = downloader.download("https://x/a.apk", "https://x/a.sig", dest).toList()

        val failed = assertIs<ApkDownloader.Event.Failed>(events.last())
        assertTrue(failed.reason.contains("limit"))
        assertTrue(File(dest, ApkDownloader.APK_NAME).notExists())
    }

    @Test
    fun `signature with unknown length is streamed with size limit`() = runTest {
        val dest = tempDir.toFile()
        val downloader = ApkDownloader(
            client = clientOf(
                "https://x/a.apk" to ResponseSpec(200, ByteArray(4) { 1 }),
                "https://x/a.sig" to ResponseSpec(200, ByteArray(1024 * 1024) { 2 }, declaredLength = -1),
            ),
            maxSigBytes = 4,
        )

        val events = downloader.download("https://x/a.apk", "https://x/a.sig", dest).toList()

        val failed = assertIs<ApkDownloader.Event.Failed>(events.last())
        assertTrue(failed.reason.contains("limit"))
        assertTrue(File(dest, ApkDownloader.APK_NAME).notExists())
        assertTrue(File(dest, ApkDownloader.SIG_NAME).notExists())
    }

    @Test
    fun `mkdirs failure emits failed`() = runTest {
        val blockedParent = File(tempDir.toFile(), "blocked-parent").apply { writeText("x") }
        val blocked = File(blockedParent, "child")
        val downloader = ApkDownloader(client = clientOf())

        val events = downloader.download("https://x/a.apk", "https://x/a.sig", blocked).toList()

        val failed = assertIs<ApkDownloader.Event.Failed>(events.last())
        assertTrue(failed.reason.contains("недоступен"))
    }

    private data class ResponseSpec(
        val code: Int,
        val bodyBytes: ByteArray,
        val declaredLength: Long = bodyBytes.size.toLong(),
    )

    private fun clientOf(vararg specs: Pair<String, ResponseSpec>): OkHttpClient {
        val map = specs.toMap()
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val spec = map[req.url.toString()] ?: throw IOException("unexpected url ${req.url}")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(spec.code)
                    .message("ok")
                    .body(responseBodyOf(spec))
                    .build()
            }
            .build()
    }

    private fun responseBodyOf(spec: ResponseSpec): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType = "application/octet-stream".toMediaType()

        override fun contentLength(): Long = spec.declaredLength

        override fun source(): BufferedSource = spec.bodyBytes.inputStream().source().buffer()
    }

    private fun File.notExists(): Boolean = !exists()
}
