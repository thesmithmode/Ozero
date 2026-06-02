package binaries

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class BinaryDownloaderTest {
    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tmp: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun bodyResponse(bytes: ByteArray): MockResponse {
        val buffer = Buffer().write(bytes)
        return MockResponse().setResponseCode(200).setBody(buffer)
    }

    @Test
    fun `downloads file and writes to destination with matching SHA`() {
        val data = "byedpi-binary".toByteArray()
        server.enqueue(bodyResponse(data))
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        downloader.download(server.url("/x.so").toString(), sha256(data), dst)
        assertThat(Files.readAllBytes(dst)).isEqualTo(data)
    }

    @Test
    fun `cache hit short-circuits — no HTTP call on second download`() {
        val data = "byedpi-binary".toByteArray()
        val sha = sha256(data)
        server.enqueue(bodyResponse(data))
        val cache = tmp.resolve("cache")
        val dst1 = tmp.resolve("a/libbyedpi.so")
        val dst2 = tmp.resolve("b/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        downloader.download(server.url("/x.so").toString(), sha, dst1)
        downloader.download(server.url("/x.so").toString(), sha, dst2)
        assertThat(Files.readAllBytes(dst2)).isEqualTo(data)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `503 503 200 — retries succeed`() {
        val data = "byedpi-binary".toByteArray()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(bodyResponse(data))
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        downloader.download(server.url("/x.so").toString(), sha256(data), dst)
        assertThat(Files.readAllBytes(dst)).isEqualTo(data)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `404 fails immediately with canonical message — no retry`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        assertThatThrownBy {
            downloader.download(server.url("/x.so").toString(), "0".repeat(64), dst)
        }
            .isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("404")
            .hasMessageContaining("gh workflow run binaries.yml")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `unexpected non retryable status fails immediately`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        assertThatThrownBy {
            downloader.download(server.url("/x.so").toString(), "0".repeat(64), dst)
        }
            .isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("Unexpected HTTP 403")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `5xx persistent fails after exhausting retries`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        assertThatThrownBy {
            downloader.download(server.url("/x.so").toString(), "0".repeat(64), dst)
        }
            .isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("500")
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `SHA mismatch fails without retry`() {
        val data = "byedpi-binary".toByteArray()
        server.enqueue(bodyResponse(data))
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))
        assertThatThrownBy {
            downloader.download(server.url("/x.so").toString(), "0".repeat(64), dst)
        }
            .isInstanceOf(IntegrityException::class.java)
        assertThat(Files.exists(dst)).isFalse()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `io failures are retried and wrapped after exhaustion`() {
        val url = server.url("/x.so").toString()
        server.shutdown()
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = listOf(0, 0, 0))

        assertThatThrownBy {
            downloader.download(url, "0".repeat(64), dst)
        }
            .isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("after 3 attempts")
            .hasCauseInstanceOf(java.io.IOException::class.java)
    }

    @Test
    fun `empty retry schedule fails without issuing HTTP request`() {
        val cache = tmp.resolve("cache")
        val dst = tmp.resolve("out/libbyedpi.so")
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = emptyList())

        assertThatThrownBy {
            downloader.download(server.url("/x.so").toString(), "0".repeat(64), dst)
        }
            .isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("after 0 attempts")

        assertThat(server.requestCount).isEqualTo(0)
        assertThat(Files.exists(dst)).isFalse()
    }
}
