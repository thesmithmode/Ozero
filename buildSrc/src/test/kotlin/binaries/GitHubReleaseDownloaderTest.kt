package binaries

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GitHubReleaseDownloaderTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `private release downloads use gh with scoped credentials and separate arguments`() {
        val target = tmp.resolve("asset.so")
        val downloader = GitHubReleaseDownloader("test-secret") { args, token ->
            assertThat(token).isEqualTo("test-secret")
            assertThat(args).containsExactly(
                "gh", "release", "download", "--repo", "example/project",
                "--pattern", "asset.so", "--output", target.toString(), "--", "v1.0",
            )
            assertThat(args).noneMatch { it.contains("test-secret") }
            Files.writeString(target, "binary")
            0
        }

        val downloaded = downloader.download(
            "https://github.com/example/project/releases/download/v1.0/asset.so",
            target,
        )
        assertThat(downloaded).isTrue()
        assertThat(Files.readString(target)).isEqualTo("binary")
    }

    @Test
    fun `credentials are never sent to non GitHub or unrecognized URLs`() {
        val downloader = GitHubReleaseDownloader("test-secret") { _, _ ->
            error("Credentials must not be used")
        }
        val urls = listOf(
            "https://example.com/example/project/releases/download/v1.0/asset.so",
            "http://github.com/example/project/releases/download/v1.0/asset.so",
            "https://github.com:444/example/project/releases/download/v1.0/asset.so",
            "https://user@github.com/example/project/releases/download/v1.0/asset.so",
            "https://github.com/example/project/blob/main/asset.so",
            "https://github.com/example/project/releases/download/v1.0/asset.so?download=1",
            "https://github.com/example/project/releases/download/v1.0/asset.so#fragment",
        )

        for (url in urls) {
            assertThat(downloader.download(url, tmp.resolve("asset.so"))).isFalse()
        }
    }

    @Test
    fun `missing or blank token keeps the ordinary downloader path`() {
        for (token in listOf(null, "", "   ")) {
            val downloader = GitHubReleaseDownloader(token) { _, _ -> error("Must not execute gh") }
            val downloaded = downloader.download(
                "https://github.com/example/project/releases/download/v1.0/asset.so",
                tmp.resolve("asset.so"),
            )
            assertThat(downloaded).isFalse()
        }
    }

    @Test
    fun `failed authenticated downloads report access failure without credentials`() {
        val downloader = GitHubReleaseDownloader("test-secret") { _, _ -> 1 }

        assertThatThrownBy {
            downloader.download(
                "https://github.com/example/project/releases/download/v1.0/asset.so",
                tmp.resolve("asset.so"),
            )
        }.isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("contents:read")
            .hasMessageNotContaining("test-secret")
    }

    @Test
    fun `process runner passes credentials through environment`() {
        val code = runGitHubDownload(
            listOf("sh", "-c", "test \"\$GH_TOKEN\" = test-secret"),
            "test-secret",
        )

        assertThat(code).isZero()
    }

    @Test
    fun `process runner preserves failed exit status`() {
        assertThat(runGitHubDownload(listOf("sh", "-c", "exit 7"), "test-secret")).isEqualTo(7)
    }

    @Test
    fun `process runner terminates a timed out download`() {
        assertThatThrownBy {
            runGitHubDownload(listOf("sleep", "5"), "test-secret", timeoutMs = 1)
        }.isInstanceOf(BinaryDownloadException::class.java)
            .hasMessageContaining("timed out")
            .hasMessageNotContaining("test-secret")
    }
}
