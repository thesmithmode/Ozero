package ru.ozero.enginebyedpi.strategy

import org.json.JSONObject
import java.io.File
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class GeneMemory(private val file: File) {

    private data class Score(val wins: Double, val trials: Double, val lastUpdatedMs: Long)

    private val scores = mutableMapOf<String, Score>()

    fun load() {
        if (!file.exists()) return
        val now = System.currentTimeMillis()
        runCatching {
            val obj = JSONObject(file.readText())
            obj.keys().forEach { token ->
                val entry = obj.getJSONObject(token)
                val lastMs = entry.getLong("lastMs")
                val daysSince = (now - lastMs) / MS_PER_DAY
                val decay = DECAY_PER_DAY.pow(daysSince)
                scores[token] = Score(
                    wins = entry.getDouble("wins") * decay,
                    trials = entry.getDouble("trials") * decay,
                    lastUpdatedMs = lastMs,
                )
            }
        }
    }

    fun save() {
        runCatching {
            val obj = JSONObject()
            scores.forEach { (token, score) ->
                if (score.trials >= MIN_TRIALS_TO_PERSIST) {
                    obj.put(
                        token,
                        JSONObject().apply {
                            put("wins", score.wins)
                            put("trials", score.trials)
                            put("lastMs", score.lastUpdatedMs)
                        },
                    )
                }
            }
            file.writeText(obj.toString())
        }
    }

    fun record(tokens: List<String>, fitness: Double) {
        val now = System.currentTimeMillis()
        for (token in tokens) {
            val s = scores.getOrDefault(token, Score(0.0, 0.0, now))
            scores[token] = s.copy(wins = s.wins + fitness, trials = s.trials + 1.0, lastUpdatedMs = now)
        }
    }

    fun ucbScore(token: String): Double {
        val s = scores[token] ?: return SCORE_UNEXPLORED
        if (s.trials < 1.0) return SCORE_UNEXPLORED
        val totalTrials = scores.values.sumOf { it.trials }.coerceAtLeast(1.0)
        val exploitation = s.wins / s.trials
        val exploration = EXPLORE_WEIGHT * sqrt(2.0 * ln(totalTrials) / s.trials)
        return exploitation + exploration
    }

    fun hasData(): Boolean = scores.isNotEmpty()

    fun rawJson(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    fun importRawJson(json: String) {
        runCatching { file.writeText(json) }
        scores.clear()
        load()
    }

    companion object {
        private const val DECAY_PER_DAY = 0.95
        private const val EXPLORE_WEIGHT = 0.3
        private const val SCORE_UNEXPLORED = 1.5
        private const val MIN_TRIALS_TO_PERSIST = 0.5
        private const val MS_PER_DAY = 86_400_000.0
    }
}
