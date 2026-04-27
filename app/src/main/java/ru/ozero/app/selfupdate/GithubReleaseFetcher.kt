package ru.ozero.app.selfupdate

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

open class GithubReleaseFetcher(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient = GithubPinnedClient.create(),
    private val baseUrl: String = "https://api.github.com",
) {

    init {
        require(baseUrl == "https://api.github.com") {
            "GithubReleaseFetcher.baseUrl должен быть https://api.github.com"
        }
    }

    open fun latest(): ReleaseInfo? {
        val req = Request.Builder()
            .url("$baseUrl/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            parse(body)
        }
    }

    internal fun parse(json: String): ReleaseInfo? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tag = obj.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val isPrerelease = obj.optBoolean("prerelease", false)
        val publishedAt = obj.optString("published_at").takeIf { it.isNotBlank() }
        val assets: JSONArray = obj.optJSONArray("assets") ?: return null

        val apkUrls = mutableListOf<String>()
        val sigUrls = mutableListOf<String>()
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (!url.startsWith("https://")) continue
            when {
                name.endsWith(".apk") && !name.endsWith(".apk.sig") -> apkUrls += url
                name.endsWith(".apk.sig") -> sigUrls += url
            }
        }
        if (apkUrls.size != 1 || sigUrls.size != 1) return null
        val apkUrl = apkUrls.single()
        val sigUrl = sigUrls.single()
        val versionCode = obj.optLong("version_code", 0L)
            .takeIf { it > 0 }
            ?: parseVersionCodeFromBody(obj.optString("body"))
        return ReleaseInfo(
            tag = tag,
            apkUrl = apkUrl,
            sigUrl = sigUrl,
            isPrerelease = isPrerelease,
            publishedAt = publishedAt,
            versionCode = versionCode,
        )
    }

    private fun parseVersionCodeFromBody(body: String): Long {
        val m = Regex("""version_code:\s*(\d+)""", RegexOption.IGNORE_CASE).find(body)
        return m?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
