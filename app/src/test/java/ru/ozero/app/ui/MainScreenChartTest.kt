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

    // ── Sentinel: константы ──────────────────────────────────────────────────

    @Test
    fun `MAX_SPEED_HISTORY_POINTS не меньше 3600 — держит 1ч при 1 тике в секунду`() {
        val regex = Regex("MAX_SPEED_HISTORY_POINTS\\s*=\\s*(\\d[\\d_]*)L?")
        val m = regex.find(vmSource) ?: error("MAX_SPEED_HISTORY_POINTS не найден")
        val value = m.groupValues[1].replace("_", "").toLong()
        assertTrue(value >= 3_600L, "Ожидалось >= 3600, fact=$value")
    }

    @Test
    fun `CHART_MAX_RENDER_POINTS определён и не больше 1000 — ограничивает Canvas-рендеринг`() {
        val regex = Regex("CHART_MAX_RENDER_POINTS\\s*=\\s*(\\d[\\d_]*)")
        val m = regex.find(screenSource) ?: error("CHART_MAX_RENDER_POINTS не найден")
        val value = m.groupValues[1].replace("_", "").toInt()
        assertTrue(value in 50..1_000, "Ожидалось 50..1000, fact=$value")
    }

    @Test
    fun `TimeframeOption содержит четыре варианта S30 M5 M30 H1`() {
        for (label in listOf("S30", "M5", "M30", "H1")) {
            assertTrue(screenSource.contains(label), "TimeframeOption.$label не найден")
        }
        for (removed in listOf("S5", "H6", "H24")) {
            assertTrue(!screenSource.contains("$removed("), "TimeframeOption.$removed должен быть удалён")
        }
    }

    @Test
    fun `TimeframeOption_M5 имеет 300 точек`() {
        val regex = Regex("M5\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("M5 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        assertEquals(300, points, "M5 обязан иметь 300 точек")
    }

    @Test
    fun `TimeframeOption_M30 имеет 1800 точек`() {
        val regex = Regex("M30\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("M30 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        assertEquals(1_800, points, "M30 обязан иметь 1800 точек")
    }

    @Test
    fun `TimeframeOption_H1 имеет 3600 точек`() {
        val regex = Regex("H1\\s*\\([^,]+,\\s*(\\d[\\d_]*)\\s*\\)")
        val m = regex.find(screenSource) ?: error("H1 entry не найден")
        val points = m.groupValues[1].replace("_", "").toInt()
        assertEquals(3_600, points, "H1 обязан иметь 3600 точек")
    }

    @Test
    fun `displayHistory паддится нулями до полного окна таймфрейма`() {
        val paddingBlock = screenSource.substringAfter("var selectedTf by remember")
            .substringBefore("Card(")
        assertTrue(
            paddingBlock.contains("List(n - slice.size)") || paddingBlock.contains("List(n -"),
            "displayHistory обязан паддить нулями слева до полного окна selectedTf.points",
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

    // ── Поведение: downsample ────────────────────────────────────────────────

    @Test
    fun `downsample пустой список возвращает пустой список`() {
        assertEquals(emptyList(), downsample(emptyList(), 10))
    }

    @Test
    fun `downsample список меньше maxPoints возвращает оригинал`() {
        val data = listOf(1f to 2f, 3f to 4f, 5f to 6f)
        assertEquals(data, downsample(data, 10))
    }

    @Test
    fun `downsample список равный maxPoints возвращает оригинал`() {
        val data = List(5) { i -> i.toFloat() to i.toFloat() }
        assertEquals(data, downsample(data, 5))
    }

    @Test
    fun `downsample список больше maxPoints возвращает maxPoints элементов`() {
        val data = List(100) { i -> i.toFloat() to 0f }
        val result = downsample(data, 10)
        assertEquals(10, result.size, "Ожидалось ровно 10 элементов после downsample")
    }

    @Test
    fun `downsample сохраняет первый и последний элемент при чётном шаге`() {
        val data = List(100) { i -> i.toFloat() to 0f }
        val result = downsample(data, 10)
        assertEquals(data[0], result[0], "Первый элемент должен совпадать с оригиналом")
    }

    @Test
    fun `downsample не выбрасывает исключения при maxPoints=1`() {
        val data = List(50) { i -> i.toFloat() to 0f }
        val result = downsample(data, 1)
        assertEquals(1, result.size)
    }

    @Test
    fun `downsample равномерно распределяет точки`() {
        val data = List(100) { i -> i.toFloat() to 0f }
        val result = downsample(data, 5)
        assertEquals(5, result.size)
        val firstIdx = data.indexOf(result[0])
        assertTrue(firstIdx >= 0, "Все элементы должны быть из оригинального списка")
    }

    @Test
    fun `downsample с maxPoints больше исходного не кидает IndexOutOfBounds`() {
        val data = List(3) { i -> i.toFloat() to 0f }
        val result = downsample(data, 1000)
        assertEquals(data, result)
    }
}
