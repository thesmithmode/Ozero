package ru.ozero.enginewarp

import android.content.SharedPreferences

class PrefsMirrorRanker(
    private val prefs: SharedPreferences,
) : MirrorRanker {

    override fun order(mirrors: List<String>): List<String> {
        val (good, neutral, bad) = mirrors.fold(
            Triple(mutableListOf<String>(), mutableListOf<String>(), mutableListOf<String>()),
        ) { acc, m ->
            val score = scoreOf(m)
            when {
                score > 0 -> acc.first.add(m)
                score < 0 -> acc.third.add(m)
                else -> acc.second.add(m)
            }
            acc
        }
        good.sortByDescending { scoreOf(it) }
        bad.sortByDescending { scoreOf(it) }
        neutral.shuffle()
        return good + neutral + bad
    }

    override fun recordSuccess(mirror: String) {
        val cur = scoreOf(mirror)
        val next = (cur + SUCCESS_DELTA).coerceAtMost(MAX_SCORE)
        prefs.edit().putInt(keyFor(mirror), next).apply()
    }

    override fun recordFailure(mirror: String) {
        val cur = scoreOf(mirror)
        val next = (cur + FAILURE_DELTA).coerceAtLeast(MIN_SCORE)
        prefs.edit().putInt(keyFor(mirror), next).apply()
    }

    private fun scoreOf(mirror: String): Int = prefs.getInt(keyFor(mirror), 0)

    private fun keyFor(mirror: String): String = PREFIX + mirror

    private companion object {
        const val PREFIX = "mirror_score:"
        const val SUCCESS_DELTA = 2
        const val FAILURE_DELTA = -1
        const val MAX_SCORE = 20
        const val MIN_SCORE = -20
    }
}
