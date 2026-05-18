package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainScreenChartTest {

    private val vmSource by lazy {
        val f = File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/app/ui/MainViewModel.kt")
        assertTrue(f.exists(), "MainViewModel.kt не найден: $f")
        f.readText()
    }

    private val screenSource by lazy {
        val f = File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/app/ui/MainScreen.kt")
        assertTrue(f.exists(), "MainScreen.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `MAX_SPEED_HISTORY_POINTS не меньше 3600 — держит 1ч при 1 тике в секунду`() {
        val regex = Regex("MAX_SPEED_HISTORY_POINTS\\s*=\\s*(\\d[\\d_]*)L?")
        val m = regex.find(vmSource) ?: error("MAX_SPEED_HISTORY_POINTS не найден")
        val value = m.groupValues[1].replace("_", "").toLong()
        assertTrue(value >= 3_600L, "Ожидалось >= 3600, fact=$value")
    }

    @Test
    fun `TimeframeOption содержит четыре варианта M1 M5 M30 H1`() {
        for (label in listOf("M1", "M5", "M30", "H1")) {
            assertTrue(screenSource.contains(label), "TimeframeOption.$label не найден")
        }
        for (removed in listOf("S5", "S30", "H6", "H24")) {
            assertTrue(!screenSource.contains("$removed("), "TimeframeOption.$removed должен быть удалён")
        }
    }

    @Test
    fun `TimeframeOption_M1 имеет 60 точек и 60 buckets — 1 минута 1с шаг`() {
        val regex = Regex("M1\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("M1 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        val buckets = m.groupValues[2].replace("_", "").toInt()
        assertEquals(60, points, "M1 обязан иметь 60 точек")
        assertEquals(60, buckets, "M1 обязан иметь 60 buckets (1с гранулярность)")
    }

    @Test
    fun `TimeframeOption_M5 имеет 300 точек и 30 buckets — 5 минут 10с шаг`() {
        val regex = Regex("M5\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("M5 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        val buckets = m.groupValues[2].replace("_", "").toInt()
        assertEquals(300, points, "M5 обязан иметь 300 точек")
        assertEquals(30, buckets, "M5 обязан иметь 30 buckets")
    }

    @Test
    fun `TimeframeOption_M30 имеет 1800 точек и 30 buckets — каждый bucket = 60с`() {
        val regex = Regex("M30\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("M30 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        val buckets = m.groupValues[2].replace("_", "").toInt()
        assertEquals(1_800, points, "M30 обязан иметь 1800 точек")
        assertEquals(30, buckets, "M30: 30 buckets × 60с — точно как просил юзер")
    }

    @Test
    fun `TimeframeOption_H1 имеет 3600 точек и 60 buckets — каждый bucket = 60с`() {
        val regex = Regex("H1\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("H1 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        val buckets = m.groupValues[2].replace("_", "").toInt()
        assertEquals(3_600, points, "H1 обязан иметь 3600 точек")
        assertEquals(60, buckets, "H1: 60 buckets × 60с")
    }

    @Test
    fun `displayHistory вызывает bucketizeTimeAligned без position-based padding`() {
        val block = screenSource.substringAfter("var selectedTf by remember")
            .substringBefore("Card(")
        assertTrue(
            block.contains("bucketizeTimeAligned"),
            "displayHistory обязан вызывать bucketizeTimeAligned — time-anchored buckets " +
                "избавляют от деформации формы при сдвиге окна",
        )
        assertTrue(
            !block.contains("List(n - slice.size)"),
            "position-based padding нулями запрещён — bucketizeTimeAligned сам заполняет пустые buckets",
        )
        assertTrue(
            !block.contains(".takeLast("),
            "takeLast по индексу запрещён — bucketizeTimeAligned фильтрует по timestamp",
        )
    }

    @Test
    fun `MainScreen использует AnimatedContent для переключения режима`() {
        assertTrue(
            screenSource.contains("AnimatedContent") && screenSource.contains("mode_switch"),
            "MainScreen обязан использовать AnimatedContent с label=\"mode_switch\" для переключения режимов",
        )
    }

    @Test
    fun `LiveTrafficChart использует addSmooth вместо lineTo`() {
        val chartBody = screenSource.substringAfter("private fun LiveTrafficChart")
            .substringBefore("private const val DOCK_TAB_HOME")
        assertTrue(
            chartBody.contains("addSmooth"),
            "LiveTrafficChart обязан использовать addSmooth для сглаживания кривых",
        )
        assertTrue(
            !chartBody.contains(".lineTo("),
            "LiveTrafficChart не должен напрямую вызывать lineTo для точек данных — только addSmooth",
        )
    }

    @Test
    fun `addSmooth использует quadraticBezierTo`() {
        val smoothBody = screenSource.substringAfter("fun Path.addSmooth")
        assertTrue(
            smoothBody.contains("quadraticBezierTo"),
            "addSmooth обязан использовать quadraticBezierTo для сглаженных кривых",
        )
    }

    @Test
    fun `chartNiceMax содержит 1-2-5 гранулярные уровни`() {
        val body = screenSource.substringAfter("private fun chartNiceMax")
            .substringBefore("private fun chartTimeAgo")
        assertTrue(body.contains("2_048") || body.contains("2048"), "2KB/s уровень отсутствует")
        assertTrue(body.contains("20_480") || body.contains("20480"), "20KB/s уровень отсутствует")
        assertTrue(body.contains("51_200") || body.contains("51200"), "50KB/s уровень отсутствует")
    }

    @Test
    fun `SPEED_SAMPLE_INTERVAL_MS не менее 1000 — граф не обновляется чаще раза в секунду`() {
        val regex = Regex("SPEED_SAMPLE_INTERVAL_MS\\s*=\\s*(\\d[\\d_]*)L?")
        val m = regex.find(vmSource) ?: error("SPEED_SAMPLE_INTERVAL_MS не найден в MainViewModel")
        val value = m.groupValues[1].replace("_", "").toLong()
        assertTrue(value >= 1_000L, "Ожидалось >= 1000, fact=$value")
    }

    @Test
    fun `bucketizeTimeAligned — пустой список возвращает N пустых buckets`() {
        val result = bucketizeTimeAligned(emptyList(), windowMs = 10_000L, bucketCount = 10)
        assertEquals(10, result.size)
        for (i in 0..9) assertEquals(0f to 0f, result[i])
    }

    @Test
    fun `bucketizeTimeAligned — bucketCount=0 возвращает пустой список`() {
        val s = listOf(SpeedSample(1_000L, 1f, 1f))
        assertEquals(emptyList(), bucketizeTimeAligned(s, windowMs = 10_000L, bucketCount = 0))
    }

    @Test
    fun `bucketizeTimeAligned — bucketMs ноль (window меньше bucketCount) возвращает пустой список`() {
        val s = listOf(SpeedSample(1_000L, 1f, 1f))
        assertEquals(emptyList(), bucketizeTimeAligned(s, windowMs = 5L, bucketCount = 10))
    }

    @Test
    fun `bucketizeTimeAligned — все samples в одном bucket дают среднее`() {
        val bucketMs = 10_000L
        val grid = (1_700_000_000_000L / bucketMs) * bucketMs
        val samples = (0..9).map { i -> SpeedSample(grid + i * 1_000L, i.toFloat(), 0f) }
        val result = bucketizeTimeAligned(samples, windowMs = 10_000L, bucketCount = 1)
        assertEquals(1, result.size)
        val expected = (0..9).sumOf { it.toDouble() }.toFloat() / 10
        assertEquals(expected, result[0].first)
    }

    @Test
    fun `bucketizeTimeAligned — 300 секундных samples в 30 buckets по 10s — корректные средние`() {
        val bucketMs = 10_000L
        val grid = (1_700_000_000_000L / bucketMs) * bucketMs
        val samples = (0..299).map { i -> SpeedSample(grid + i * 1_000L, i.toFloat(), 0f) }
        val result = bucketizeTimeAligned(samples, windowMs = 300_000L, bucketCount = 30)
        assertEquals(30, result.size)
        val firstBucketAvg = (0..9).sumOf { it.toDouble() }.toFloat() / 10
        assertEquals(firstBucketAvg, result[0].first, "bucket[0] = avg первых 10 секунд")
        val lastBucketAvg = (290..299).sumOf { it.toDouble() }.toFloat() / 10
        assertEquals(lastBucketAvg, result[29].first, "bucket[29] = avg последних 10 секунд")
    }

    @Test
    fun `bucketizeTimeAligned — sentinel — история неизменна при sample внутри текущего bucket`() {
        val bucketMs = 10_000L
        val grid = (1_700_000_000_000L / bucketMs) * bucketMs
        val samples0 = (0..299).map { i -> SpeedSample(grid + i * 1_000L, i.toFloat(), 0f) }
        val tf0 = bucketizeTimeAligned(samples0, windowMs = 300_000L, bucketCount = 30)
        val sampleInsideLastBucket = SpeedSample(grid + 299_500L, 999f, 0f)
        val tf1 = bucketizeTimeAligned(samples0 + sampleInsideLastBucket, windowMs = 300_000L, bucketCount = 30)
        for (i in 0..28) {
            assertEquals(
                tf0[i],
                tf1[i],
                "bucket $i деформировался — buckets обязаны быть anchored к wall clock, " +
                    "новый sample внутри текущего bucket не должен менять историю",
            )
        }
    }

    @Test
    fun `bucketizeTimeAligned — sentinel — grid сдвигается на 1 bucket при пересечении границы`() {
        val bucketMs = 10_000L
        val grid = (1_700_000_000_000L / bucketMs) * bucketMs
        val samplesA = (0..299).map { i -> SpeedSample(grid + i * 1_000L, i.toFloat(), 0f) }
        val tfA = bucketizeTimeAligned(samplesA, windowMs = 300_000L, bucketCount = 30)
        val newBucketSample = SpeedSample(grid + 300_000L, 999f, 0f)
        val tfB = bucketizeTimeAligned(samplesA + newBucketSample, windowMs = 300_000L, bucketCount = 30)
        for (i in 0..28) {
            assertEquals(
                tfA[i + 1],
                tfB[i],
                "bucket B[$i] обязан равняться A[${i + 1}] — окно сдвинулось ровно на 1 bucket",
            )
        }
    }

    @Test
    fun `bucketizeTimeAligned — sentinel — M1 (60 buckets, 1s each) сдвигается каждую секунду`() {
        val bucketMs = 1_000L
        val grid = (1_700_000_000_000L / bucketMs) * bucketMs
        val samplesA = (0..59).map { i -> SpeedSample(grid + i * 1_000L, i.toFloat(), 0f) }
        val tfA = bucketizeTimeAligned(samplesA, windowMs = 60_000L, bucketCount = 60)
        val newSample = SpeedSample(grid + 60_000L, 999f, 0f)
        val tfB = bucketizeTimeAligned(samplesA + newSample, windowMs = 60_000L, bucketCount = 60)
        for (i in 0..58) {
            assertEquals(tfA[i + 1], tfB[i], "M1 bucket[$i] обязан сдвигаться каждую секунду")
        }
        assertEquals(999f, tfB[59].first, "rightmost bucket — новый sample")
    }

    @Test
    fun `bucketizeTimeAligned — старые samples вне окна игнорируются`() {
        val bucketMs = 10_000L
        val grid = (1_700_000_000_000L / bucketMs) * bucketMs
        val samples = listOf(
            SpeedSample(grid - 500_000L, 100f, 0f),
            SpeedSample(grid + 0L, 1f, 0f),
            SpeedSample(grid + 290_000L, 2f, 0f),
        )
        val result = bucketizeTimeAligned(samples, windowMs = 300_000L, bucketCount = 30)
        assertEquals(30, result.size)
        assertEquals(1f, result[0].first)
        assertEquals(2f, result[29].first)
    }
}
