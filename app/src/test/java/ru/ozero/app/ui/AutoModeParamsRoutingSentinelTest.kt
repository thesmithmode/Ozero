package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class AutoModeParamsRoutingSentinelTest {

    private val source by lazy {
        val f = File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/app/ui/RootNavigation.kt")
        assertTrue(f.exists(), "RootNavigation.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `null engineId роутится в AutoModeSettings, не в Servers`() {
        val helperBlock = source
            .substringAfter("private fun engineParamsTarget(engineId: EngineId?)")
            .substringBefore("}")
        assertTrue(
            helperBlock.contains("null -> TopScreen.AutoModeSettings"),
            "При engineId=null (auto-режим) engineParamsTarget должен возвращать AutoModeSettings. " +
                "Ветка null отсутствует — нажатие Параметры в auto-режиме открывает Servers вместо " +
                "экрана приоритетов движков.",
        )
    }

    @Test
    fun `null ветка стоит до else в when блоке`() {
        val helperBlock = source
            .substringAfter("private fun engineParamsTarget(engineId: EngineId?)")
            .substringBefore("}")
        val nullIdx = helperBlock.indexOf("null -> TopScreen.AutoModeSettings")
        val elseIdx = helperBlock.indexOf("else ->")
        assertTrue(nullIdx >= 0, "null-ветка отсутствует в engineParamsTarget when(engineId)")
        assertTrue(elseIdx >= 0, "else-ветка отсутствует в engineParamsTarget when(engineId)")
        assertTrue(nullIdx < elseIdx, "null-ветка должна быть до else (иначе else перехватит null)")
    }
}
