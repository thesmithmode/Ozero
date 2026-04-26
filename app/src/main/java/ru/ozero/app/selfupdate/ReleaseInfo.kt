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
    /**
     * Опциональный versionCode из GitHub release body (например, поле `version_code`).
     * 0 = не предоставлен, downgrade-проверка по versionCode пропускается.
     */
    val versionCode: Long = 0L,
) {
    /** Парсит "vX.Y.Z[-rcN]" → Triple(major, minor, patch). */
    fun semver(): Triple<Int, Int, Int>? = parseSemver(tag)

    fun isNewerThan(currentTag: String): Boolean {
        // Pre-release не должен авто-устанавливаться в stable build: substringBefore('-')
        // в parseSemver делал v2.0.0-rc1 эквивалентным v2.0.0, юзер silently получал rc-версию.
        if (isPrerelease) return false
        val a = semver() ?: return false
        val b = parseSemver(currentTag) ?: return false
        return when {
            a.first != b.first -> a.first > b.first
            a.second != b.second -> a.second > b.second
            else -> a.third > b.third
        }
    }

    companion object {
        fun parseSemver(tag: String): Triple<Int, Int, Int>? {
            val cleaned = tag.removePrefix("v").substringBefore('-')
            val parts = cleaned.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size < 3) return null
            return Triple(parts[0], parts[1], parts[2])
        }
    }
}
