package ru.ozero.enginebyedpi.strategy

import java.io.File
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneMemoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun memory() = GeneMemory(File(tempDir, "test_memory.json"))

    private fun meanSamples(mem: GeneMemory, token: String, seed: Long, samples: Int = SAMPLE_COUNT): Double {
        val random = Random(seed)
        var sum = 0.0
        repeat(samples) { sum += mem.sampleScore(token, random) }
        return sum / samples
    }

    private fun varianceSamples(mem: GeneMemory, token: String, seed: Long, samples: Int = SAMPLE_COUNT): Double {
        val random = Random(seed)
        val values = DoubleArray(samples) { mem.sampleScore(token, random) }
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / samples
    }

    @Test
    fun `sampleScore returns values in unit interval for unknown token`() {
        val mem = memory()
        val random = Random(SEED_DEFAULT)
        repeat(SAMPLE_COUNT) {
            val s = mem.sampleScore("-unknown", random)
            assertTrue(s in 0.0..1.0, "Thompson sample must be in [0,1], got $s")
        }
    }

    @Test
    fun `sampleScore unknown token mean approximates Beta(1,1) mean 0_5`() {
        val mem = memory()
        val mean = meanSamples(mem, "-unknown", SEED_DEFAULT)
        assertTrue(mean in (0.5 - MEAN_TOLERANCE)..(0.5 + MEAN_TOLERANCE), "unknown mean must be ~0.5, got $mean")
    }

    @Test
    fun `recorded token with high fitness samples higher than unknown`() {
        val mem = memory()
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-good"), fitness = 1.0) }
        val goodMean = meanSamples(mem, "-good", SEED_DEFAULT)
        val unknownMean = meanSamples(mem, "-unknown", SEED_DEFAULT)
        assertTrue(goodMean > unknownMean, "high-fitness token must sample higher: good=$goodMean unknown=$unknownMean")
    }

    @Test
    fun `recorded token with zero fitness samples lower than unknown`() {
        val mem = memory()
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-bad"), fitness = 0.0) }
        val badMean = meanSamples(mem, "-bad", SEED_DEFAULT)
        val unknownMean = meanSamples(mem, "-unknown", SEED_DEFAULT)
        assertTrue(badMean < unknownMean, "zero-fitness token must sample lower: bad=$badMean unknown=$unknownMean")
    }

    @Test
    fun `good token samples higher than bad token`() {
        val mem = memory()
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-good"), fitness = 1.0) }
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-bad"), fitness = 0.0) }
        val goodMean = meanSamples(mem, "-good", SEED_DEFAULT)
        val badMean = meanSamples(mem, "-bad", SEED_DEFAULT)
        assertTrue(goodMean > badMean, "good token must outscore bad in expectation: good=$goodMean bad=$badMean")
    }

    @Test
    fun `save and load round-trips scores`() {
        val file = File(tempDir, "mem.json")
        val mem1 = GeneMemory(file)
        repeat(REPEAT_TIMES_HIGH) { mem1.record(listOf("-x"), fitness = 0.8) }
        mem1.save()

        val mem2 = GeneMemory(file)
        mem2.load()
        val mean = meanSamples(mem2, "-x", SEED_DEFAULT)
        assertTrue(mean > 0.5, "loaded high-fitness token must still sample high: $mean")
    }

    @Test
    fun `load on missing file does not throw`() {
        val mem = GeneMemory(File(tempDir, "nonexistent.json"))
        mem.load()
        assertFalse(mem.hasData())
    }

    @Test
    fun `hasData false before any record`() {
        val mem = memory()
        assertFalse(mem.hasData())
    }

    @Test
    fun `hasData true after record`() {
        val mem = memory()
        mem.record(listOf("-a"), fitness = 0.5)
        assertTrue(mem.hasData())
    }

    @Test
    fun `multiple records accumulate wins — heavier evidence shifts mean closer to fitness`() {
        val mem = memory()
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-heavy"), fitness = 1.0) }
        mem.record(listOf("-light"), fitness = 1.0)
        val heavyMean = meanSamples(mem, "-heavy", SEED_DEFAULT)
        val lightMean = meanSamples(mem, "-light", SEED_DEFAULT)
        assertTrue(heavyMean > lightMean, "heavy evidence shifts mean closer to 1.0: heavy=$heavyMean light=$lightMean")
    }

    @Test
    fun `save skips tokens with negligible trials`() {
        val file = File(tempDir, "skip.json")
        val mem = GeneMemory(file)
        mem.save()
        assertFalse(file.exists() && file.readText().contains("\"wins\""))
    }

    @Test
    fun `record with empty token list leaves memory empty`() {
        val mem = memory()

        mem.record(emptyList(), fitness = 1.0)

        assertFalse(mem.hasData())
        assertNull(mem.rawJson())
    }

    @Test
    fun `rawJson returns null for empty memory and JSON after record`() {
        val mem = memory()
        assertNull(mem.rawJson())

        mem.record(listOf("-x"), fitness = 0.25)
        val raw = mem.rawJson()

        assertTrue(raw.orEmpty().contains("-x"), "rawJson must include persisted token: $raw")
        assertTrue(raw.orEmpty().contains("\"trials\""), "rawJson must include trial count: $raw")
    }

    @Test
    fun `importRawJson ignores empty JSON and keeps existing scores`() {
        val mem = memory()
        mem.record(listOf("-keep"), fitness = 1.0)
        val before = meanSamples(mem, "-keep", SEED_DEFAULT)

        mem.importRawJson("{}")
        val after = meanSamples(mem, "-keep", SEED_DEFAULT)

        assertTrue(before > 0.5)
        assertTrue(after > 0.5, "empty import must not clear in-memory scores")
    }

    @Test
    fun `load decays stale evidence but keeps token available`() {
        val file = File(tempDir, "decay.json")
        val oldMs = System.currentTimeMillis() - 2 * 86_400_000L
        file.writeText(
            JSONObject()
                .put(
                    "-old",
                    JSONObject()
                        .put("wins", 10.0)
                        .put("trials", 10.0)
                        .put("lastMs", oldMs),
                )
                .toString(),
        )
        val mem = GeneMemory(file)

        mem.load()

        assertTrue(mem.hasData())
        assertTrue(meanSamples(mem, "-old", SEED_DEFAULT) > 0.5)
    }

    @Test
    fun `under-tried tokens have higher sampling variance than popular tokens`() {
        val mem = memory()
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-popular"), fitness = 0.5) }
        mem.record(listOf("-rare"), fitness = 0.5)
        val popularVariance = varianceSamples(mem, "-popular", SEED_DEFAULT)
        val rareVariance = varianceSamples(mem, "-rare", SEED_DEFAULT)
        assertTrue(
            rareVariance > popularVariance,
            "rare token must have wider posterior: rare=$rareVariance popular=$popularVariance",
        )
    }

    @Test
    fun `all samples are finite for equal fitness tokens`() {
        val mem = memory()
        listOf("-a", "-b", "-c").forEach { mem.record(listOf(it), fitness = 0.7) }
        val random = Random(SEED_DEFAULT)
        listOf("-a", "-b", "-c").forEach { token ->
            repeat(SAMPLE_COUNT) {
                val s = mem.sampleScore(token, random)
                assertTrue(s.isFinite(), "Thompson sample for $token must be finite, got $s")
            }
        }
    }

    @Test
    fun `concurrent record does not corrupt sampling`() {
        val mem = memory()
        val threads = (1..CONCURRENT_THREADS).map { idx ->
            Thread {
                repeat(CONCURRENT_RECORDS) { mem.record(listOf("-t$idx"), fitness = 1.0) }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        val random = Random(SEED_DEFAULT)
        (1..CONCURRENT_THREADS).forEach { idx ->
            val s = mem.sampleScore("-t$idx", random)
            assertTrue(s.isFinite() && s > 0.0, "sample for -t$idx must be finite positive after concurrent record: $s")
        }
    }

    @Test
    fun `importRawJson rejects malformed JSON without writing to file`() {
        val mem = memory()
        mem.record(listOf("-keep"), fitness = 1.0)
        mem.save()
        mem.importRawJson("not a json")
        val mem2 = GeneMemory(File(tempDir, "test_memory.json"))
        mem2.load()
        assertTrue(mem2.hasData(), "valid prior state must survive failed import")
    }

    @Test
    fun `importRawJson ignores empty array JSON`() {
        val mem = memory()
        mem.record(listOf("-keep"), fitness = 1.0)

        mem.importRawJson("[]")

        assertTrue(mem.hasData())
    }

    @Test
    fun `importRawJson replaces scores from valid JSON file`() {
        val mem = memory()
        mem.record(listOf("-old"), fitness = 1.0)
        val json = JSONObject()
            .put(
                "-new",
                JSONObject()
                    .put("wins", 8.0)
                    .put("trials", 10.0)
                    .put("lastMs", System.currentTimeMillis()),
            )
            .toString()

        mem.importRawJson(json)

        assertTrue(meanSamples(mem, "-new", SEED_DEFAULT) > 0.6)
        assertTrue(meanSamples(mem, "-old", SEED_DEFAULT) < 0.7)
    }

    @Test
    fun `load ignores malformed persisted JSON`() {
        val file = File(tempDir, "persist.json")
        file.writeText("{broken")
        val mem = GeneMemory(file)

        mem.load()

        assertFalse(mem.hasData())
    }

    @Test
    fun `sampleScore supports fractional fitness posterior`() {
        val mem = memory()
        repeat(REPEAT_TIMES_HIGH) { mem.record(listOf("-half"), fitness = 0.5) }

        val mean = meanSamples(mem, "-half", SEED_DEFAULT)

        assertTrue(mean in 0.35..0.65, "fractional fitness should stay near 0.5, got $mean")
    }

    @Test
    fun `sampleScore is stochastic — different random produces different values`() {
        val mem = memory()
        mem.record(listOf("-x"), fitness = 0.7)
        val a = mem.sampleScore("-x", Random(SEED_DEFAULT))
        val b = mem.sampleScore("-x", Random(SEED_DEFAULT + 1))
        assertTrue(a != b, "different random seeds must produce different Thompson samples: a=$a b=$b")
    }

    @Test
    fun `sampleScore concentrates near 1_0 as evidence of high fitness grows`() {
        val mem = memory()
        repeat(REPEAT_TIMES_CONCENTRATION) { mem.record(listOf("-strong"), fitness = 1.0) }
        val mean = meanSamples(mem, "-strong", SEED_DEFAULT)
        val variance = varianceSamples(mem, "-strong", SEED_DEFAULT)
        assertTrue(mean > CONCENTRATION_MEAN_FLOOR, "100x fitness=1 token must concentrate near 1.0, got mean=$mean")
        assertTrue(
            variance < CONCENTRATION_VARIANCE_CEILING,
            "concentrated posterior must have small variance, got $variance",
        )
    }

    @Test
    fun `isRich false when total trials below threshold`() {
        val mem = memory()
        repeat(RICH_BELOW_THRESHOLD) { mem.record(listOf("-a"), fitness = 0.5) }
        assertFalse(mem.isRich(), "isRich must be false below RICH_THRESHOLD=50 total trials")
    }

    @Test
    fun `isRich true when total trials cross threshold`() {
        val mem = memory()
        repeat(RICH_ABOVE_THRESHOLD) { mem.record(listOf("-a"), fitness = 0.5) }
        assertTrue(mem.isRich(), "isRich must be true at or above RICH_THRESHOLD=50 total trials")
    }

    @Test
    fun `gene memory uses thompson sampling not UCB1`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/enginebyedpi/strategy/GeneMemory.kt").readText()
        assertTrue(
            source.contains("BetaSampler") && source.contains("gammaSample"),
            "GeneMemory must use Thompson Sampling (Beta distribution) — UCB1 is too slow for non-stationary rewards",
        )
        assertFalse(source.contains("EXPLORE_WEIGHT"), "UCB exploration weight constant must be removed")
        assertFalse(source.contains("SCORE_UNEXPLORED"), "UCB unexplored-score constant must be removed")
    }

    private companion object {
        const val SEED_DEFAULT = 42L
        const val SAMPLE_COUNT = 200
        const val REPEAT_TIMES_HIGH = 10
        const val REPEAT_TIMES_CONCENTRATION = 100
        const val MEAN_TOLERANCE = 0.1
        const val CONCENTRATION_MEAN_FLOOR = 0.9
        const val CONCENTRATION_VARIANCE_CEILING = 0.01
        const val CONCURRENT_THREADS = 8
        const val CONCURRENT_RECORDS = 200
        const val RICH_BELOW_THRESHOLD = 30
        const val RICH_ABOVE_THRESHOLD = 60
    }
}
