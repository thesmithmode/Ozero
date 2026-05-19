package ru.ozero.app.ui.settings.engines

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ByeDpiSettingsScreenSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(moduleRoot, "src/main/java/ru/ozero/app/ui/settings/engines/ByeDpiEngineSettingsScreen.kt"),
            File(moduleRoot, "app/src/main/java/ru/ozero/app/ui/settings/engines/ByeDpiEngineSettingsScreen.kt"),
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("ByeDpiEngineSettingsScreen.kt не найден. candidates=$candidates")
        f.readText()
    }

    @Test
    fun `screen использует SingleChoiceSegmentedButtonRow вместо Switch — как ScanModeSelector`() {
        assertTrue(
            source.contains("SingleChoiceSegmentedButtonRow"),
            "выбор UI vs CMD должен быть сегментированным селектором (как fast/deep в StrategyTestScreen), " +
                "не Switch — иначе пользователь не видит что есть два равноправных режима",
        )
        assertTrue(
            !source.contains(".testTag(\"byedpi_mode_switch\")"),
            "старый testTag byedpi_mode_switch должен исчезнуть — Switch заменён на SegmentedButton",
        )
        assertTrue(
            source.contains(".testTag(\"byedpi_mode_ui_segment\")") &&
                source.contains(".testTag(\"byedpi_mode_cmd_segment\")"),
            "оба сегмента должны иметь testTag для UI-тестов",
        )
    }

    @Test
    fun `screen объявляет два SegmentedButton с itemShape index 0 и 1`() {
        assertTrue(source.contains("SegmentedButtonDefaults.itemShape(index = 0, count = 2)"))
        assertTrue(source.contains("SegmentedButtonDefaults.itemShape(index = 1, count = 2)"))
    }

    @Test
    fun `UI-сегмент выбирает useUiMode true CMD-сегмент false`() {
        assertTrue(
            source.contains("viewModel.onToggleUiMode(true)"),
            "UI-сегмент обязан переключать в UI-режим (useUiMode=true)",
        )
        assertTrue(
            source.contains("viewModel.onToggleUiMode(false)"),
            "CMD-сегмент обязан переключать в CMD-режим (useUiMode=false)",
        )
        assertTrue(
            source.contains("selected = s.useUiMode,") &&
                source.contains("selected = !s.useUiMode,"),
            "selected-флаги обоих сегментов должны быть инверсиями друг друга — гарантия mutual exclusion",
        )
    }

    @Test
    fun `строковые ресурсы byedpi_mode_segment объявлены`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(moduleRoot, "src/main/res/values/strings.xml"),
            File(moduleRoot, "app/src/main/res/values/strings.xml"),
        )
        val xml = candidates.firstOrNull { it.exists() }?.readText()
            ?: error("values/strings.xml не найден. candidates=$candidates")
        assertTrue(xml.contains("byedpi_mode_segment_ui"))
        assertTrue(xml.contains("byedpi_mode_segment_cmd"))
        assertTrue(xml.contains("byedpi_mode_title"))
    }
}
