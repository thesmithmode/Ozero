package ru.ozero.app.selfupdate

/**
 * Метаданные релиза с GitHub Releases API.
 * @param tag    версия в формате `vX.Y.Z` (или `vX.Y.Z-rcN`)
 * @param apkUrl прямая ссылка на release-asset .apk
 * @param sigUrl прямая ссылка на release-asset .apk.sig (Ed25519 подпись APK)
 */
data class ReleaseInfo(
    val tag: String,
    val apkUrl: String,
    val sigUrl: String,
    val isPrerelease: Boolean = false,
    val publishedAt: String? = null,
) {
    /** Парсит "vX.Y.Z[-rcN]" → Triple(major, minor, patch). */
    fun semver(): Triple<Int, Int, Int>? {
        val cleaned = tag.removePrefix("v").substringBefore('-')
        val parts = cleaned.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }

    fun isNewerThan(currentTag: String): Boolean {
        val a = semver() ?: return false
        val b = ReleaseInfo(tag = currentTag, apkUrl = "", sigUrl = "").semver() ?: return true
        return when {
            a.first != b.first -> a.first > b.first
            a.second != b.second -> a.second > b.second
            else -> a.third > b.third
        }
    }
}
