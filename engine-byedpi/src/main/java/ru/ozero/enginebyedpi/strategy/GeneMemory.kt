package ru.ozero.enginebyedpi.strategy

import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class GeneMemory(private val file: File) {

    private data class Score(val wins: Double, val trials: Double, val lastUpdatedMs: Long)

    private val scores = mutableMapOf<String, Score>()

    @Synchronized
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

    @Synchronized
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

    @Synchronized
    fun record(tokens: List<String>, fitness: Double) {
        val now = System.currentTimeMillis()
        for (token in tokens) {
            val s = scores.getOrDefault(token, Score(0.0, 0.0, now))
            scores[token] = s.copy(wins = s.wins + fitness, trials = s.trials + 1.0, lastUpdatedMs = now)
        }
    }

    @Synchronized
    fun sampleScore(token: String, random: Random): Double {
        val s = scores[token]
        val wins = s?.wins ?: 0.0
        val trials = s?.trials ?: 0.0
        val alpha = wins + 1.0
        val beta = (trials - wins).coerceAtLeast(0.0) + 1.0
        return BetaSampler.sample(alpha, beta, random)
    }

    @Synchronized
    fun hasData(): Boolean = scores.isNotEmpty()

    @Synchronized
    fun isRich(): Boolean = scores.values.sumOf { it.trials } >= RICH_THRESHOLD

    @Synchronized
    fun rawJson(): String? {
        if (scores.isEmpty()) return null
        return runCatching {
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
            obj.toString()
        }.getOrNull()
    }

    @Synchronized
    fun importRawJson(json: String) {
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (parsed.length() == 0) return
        runCatching { file.writeText(json) }
        scores.clear()
        load()
    }

    private object BetaSampler {
        fun sample(alpha: Double, beta: Double, random: Random): Double {
            val x = gammaSample(alpha, random)
            val y = gammaSample(beta, random)
            val sum = x + y
            return if (sum > 0.0) x / sum else FALLBACK_MEAN
        }

        @Suppress("NestedBlockDepth")
        private fun gammaSample(shape: Double, random: Random): Double {
            if (shape < 1.0) {
                val boost = gammaSample(shape + 1.0, random)
                val u = random.nextDouble().coerceAtLeast(EPSILON)
                return boost * u.pow(1.0 / shape)
            }
            val d = shape - GAMMA_D_OFFSET
            val c = 1.0 / sqrt(GAMMA_C_FACTOR * d)
            while (true) {
                var x: Double
                var v: Double
                do {
                    x = nextGaussian(random)
                    v = 1.0 + c * x
                } while (v <= 0.0)
                v = v * v * v
                val u = random.nextDouble()
                val xSq = x * x
                if (u < 1.0 - GAMMA_FAST_BOUND * xSq * xSq) return d * v
                if (ln(u) < GAMMA_LOG_HALF * xSq + d * (1.0 - v + ln(v))) return d * v
            }
        }

        private fun nextGaussian(random: Random): Double {
            val u1 = random.nextDouble().coerceAtLeast(EPSILON)
            val u2 = random.nextDouble()
            return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        }

        private const val EPSILON = 1e-12
        private const val FALLBACK_MEAN = 0.5
        private const val GAMMA_D_OFFSET = 1.0 / 3.0
        private const val GAMMA_C_FACTOR = 9.0
        private const val GAMMA_FAST_BOUND = 0.0331
        private const val GAMMA_LOG_HALF = 0.5
    }

    companion object {
        private const val DECAY_PER_DAY = 0.95
        private const val MIN_TRIALS_TO_PERSIST = 0.5
        private const val MS_PER_DAY = 86_400_000.0
        private const val RICH_THRESHOLD = 50.0
    }
}
