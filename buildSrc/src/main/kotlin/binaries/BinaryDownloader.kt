package binaries

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

class BinaryDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class BinaryDownloader(
    private val cacheDir: Path,
    private val retryDelaysMs: List<Long> = listOf(3000L, 9000L, 27000L),
    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build(),
) {
    fun download(url: String, expectedSha256: String, finalDst: Path) {
        Files.createDirectories(cacheDir)
        val cached = cacheDir.resolve(expectedSha256).resolve(finalDst.fileName.toString())
        Sha256Verifier.withFileLock(cacheDir.resolve("$expectedSha256.lock")) {
            if (!Files.exists(cached)) {
                fetchToCache(url, cached, expectedSha256)
            }
            Files.createDirectories(finalDst.parent)
            Files.copy(cached, finalDst, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Suppress("LongMethod", "ThrowsCount", "ComplexMethod")
    private fun fetchToCache(url: String, cached: Path, expectedSha256: String) {
        val parent = cached.parent
        Files.createDirectories(parent)
        val tmp = parent.resolve("${cached.fileName}.tmp.${ProcessHandle.current().pid()}")

        var lastError: Exception? = null
        val attempts = retryDelaysMs.size
        for (attempt in 1..attempts) {
            try {
                val request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(5))
                        .GET()
                        .build()
                val response: HttpResponse<Path> =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tmp))
                when (val status = response.statusCode()) {
                    in 200..299 -> {
                        Sha256Verifier.verifyAndMove(tmp, cached, expectedSha256)
                        return
                    }
                    404 -> {
                        Files.deleteIfExists(tmp)
                        throw BinaryDownloadException(
                            "HTTP 404 for $url. Tag may not exist. " +
                                "Run: gh workflow run binaries.yml --ref dev",
                        )
                    }
                    in 500..599 -> {
                        Files.deleteIfExists(tmp)
                        lastError = BinaryDownloadException("HTTP $status for $url")
                        if (attempt < attempts) Thread.sleep(retryDelaysMs[attempt - 1])
                    }
                    else -> {
                        Files.deleteIfExists(tmp)
                        throw BinaryDownloadException("Unexpected HTTP $status for $url")
                    }
                }
            } catch (e: java.io.IOException) {
                Files.deleteIfExists(tmp)
                lastError = e
                if (attempt < attempts) Thread.sleep(retryDelaysMs[attempt - 1])
            }
        }
        throw BinaryDownloadException(
            "Failed to download $url after $attempts attempts: ${lastError?.message}",
            lastError,
        )
    }
}
