package binaries

import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class GitHubReleaseDownloader(
    private val token: String? = System.getenv("GH_TOKEN") ?: System.getenv("GITHUB_TOKEN"),
    private val runDownload: (List<String>, String) -> Int = ::runGitHubDownload,
) {
    fun download(url: String, destination: Path): Boolean {
        if (token.isNullOrBlank()) return false
        val uri = URI.create(url)
        val secureOrigin = uri.scheme == "https" && uri.host == "github.com"
        val noAuthorityOverrides = uri.port == -1 && uri.rawUserInfo == null
        val plainAssetPath = uri.rawQuery == null && uri.rawFragment == null
        if (!secureOrigin || !noAuthorityOverrides || !plainAssetPath) return false
        val match = RELEASE_PATH.matchEntire(uri.path) ?: return false
        val (owner, repo, tag, asset) = match.destructured
        val args = listOf(
            "gh", "release", "download", "--repo", "$owner/$repo",
            "--pattern", asset, "--output", destination.toString(), "--", tag,
        )
        if (runDownload(args, token) != 0) {
            throw BinaryDownloadException(
                "Authenticated GitHub asset download failed; check contents:read and release asset availability",
            )
        }
        return true
    }

    companion object {
        private val RELEASE_PATH = Regex(
            "^/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/releases/download/([A-Za-z0-9._/-]+)/([A-Za-z0-9._-]+)$",
        )
    }
}

private fun runGitHubDownload(args: List<String>, token: String): Int {
    val process = ProcessBuilder(args).apply {
        environment()["GH_TOKEN"] = token
        redirectOutput(ProcessBuilder.Redirect.DISCARD)
        redirectError(ProcessBuilder.Redirect.DISCARD)
    }.start()
    if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly()
        throw BinaryDownloadException("Authenticated GitHub asset download timed out")
    }
    return process.exitValue()
}
