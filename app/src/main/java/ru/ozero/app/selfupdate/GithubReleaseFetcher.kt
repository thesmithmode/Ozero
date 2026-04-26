package ru.ozero.app.selfupdate

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Тянет latest release с GitHub Releases API.
 * URL по умолчанию: https://api.github.com/repos/OWNER/REPO/releases/latest
 * apk и apk.sig ассеты определяются по суффиксу имени.
 *
 * Безопасность:
 * - HTTPS обязателен (network_security_config с E10 запрещает cleartext)
 * - Сertificate pinning api.github.com через [GithubPinnedClient] — защита от
 *   compromised CA (любой публично доверенный CA мог бы выпустить mitm-сертификат
 *   и подменить APK URL)
 * - Подпись APK верифицируется отдельно через Ed25519 (см. ApkUpdateVerifier)
 */
open class GithubReleaseFetcher(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient = GithubPinnedClient.create(),
    private val baseUrl: String = "https://api.github.com",
) {

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

        var apkUrl: String? = null
        var sigUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            // Валидация схемы — отбрасываем file://, http://, ftp://, javascript: и пр.
            if (!url.startsWith("https://")) continue
            when {
                name.endsWith(".apk") && !name.endsWith(".apk.sig") -> apkUrl = url
                name.endsWith(".apk.sig") -> sigUrl = url
            }
        }
        if (apkUrl.isNullOrBlank() || sigUrl.isNullOrBlank()) return null
        // Опциональный versionCode из release body (формат: "version_code: 12345" или JSON-блок).
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
        // Поддерживаем строку вида "version_code: 12345" в release body
        val m = Regex("""version_code:\s*(\d+)""", RegexOption.IGNORE_CASE).find(body)
        return m?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
