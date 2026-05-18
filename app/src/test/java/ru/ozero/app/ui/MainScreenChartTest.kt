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
    fun `displayHistory паддится нулями до полного окна таймфрейма`() {
        val paddingBlock = screenSource.substringAfter("var selectedTf by remember")
            .substringBefore("Card(")
        assertTrue(
            paddingBlock.contains("List(n - slice.size)") || paddingBlock.contains("List(n -"),
            "displayHistory обязан паддить нулями слева до полного окна selectedTf.points",
        )
        assertTrue(
            paddingBlock.contains("bucketize"),
            "displayHistory обязан вызывать bucketize для агрегации в фиксированное число buckets",
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
    fun `bucketize пустой список возвращает пустой список`() {
        assertEquals(emptyList(), bucketize(emptyList(), 10))
    }

    @Test
    fun `bucketize buckets ноль возвращает пустой список`() {
        val data = listOf(1f to 2f, 3f to 4f)
        assertEquals(emptyList(), bucketize(data, 0))
    }

    @Test
    fun `bucketize buckets больше или равно размеру возвращает оригинал`() {
        val data = List(5) { i -> i.toFloat() to i.toFloat() }
        assertEquals(data, bucketize(data, 10))
        assertEquals(data, bucketize(data, 5))
    }

    @Test
    fun `bucketize 60 элементов в 30 buckets — каждый bucket усредняет 2 элемента`() {
        val data = List(60) { i -> i.toFloat() to (i * 2f) }
        val result = bucketize(data, 30)
        assertEquals(30, result.size)
        assertEquals((0f + 1f) / 2f, result[0].first)
        assertEquals((0f + 2f) / 2f, result[0].second)
        assertEquals((58f + 59f) / 2f, result[29].first)
    }

    @Test
    fun `bucketize 1800 элементов в 30 buckets — каждый bucket усредняет 60 секунд`() {
        val data = List(1800) { i -> i.toFloat() to 0f }
        val result = bucketize(data, 30)
        assertEquals(30, result.size, "30 buckets как в TF M30")
        val firstBucketExpected = (0 until 60).sumOf { it.toDouble() }.toFloat() / 60
        assertEquals(firstBucketExpected, result[0].first, "первый bucket = avg 60 секунд")
    }

    @Test
    fun `bucketize всегда возвращает ровно buckets элементов когда данных больше`() {
        val data = List(100) { i -> i.toFloat() to 0f }
        for (b in listOf(1, 2, 5, 10, 30, 60)) {
            assertEquals(b, bucketize(data, b).size, "buckets=$b — должно быть ровно $b элементов")
        }
    }

    @Test
    fun `bucketize устойчив к маленьким buckets — buckets=1 средний всего диапазона`() {
        val data = List(10) { it.toFloat() to it.toFloat() }
        val result = bucketize(data, 1)
        assertEquals(1, result.size)
        val expected = (0..9).sumOf { it.toDouble() }.toFloat() / 10
        assertEquals(expected, result[0].first)
    }
}
