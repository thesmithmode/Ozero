package ru.ozero.app.selfupdate

data class ReleaseInfo(
    val tag: String,
    val apkUrl: String,
    val sigUrl: String,
    val isPrerelease: Boolean = false,
    val publishedAt: String? = null,
        val versionCode: Long = 0L,
) {
        fun semver(): Triple<Int, Int, Int>? = parseSemver(tag)

    fun isNewerThan(currentTag: String): Boolean {
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
