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
        val whenBlock = source
            .substringAfter("onOpenEngineParams = { engineId ->")
            .substringBefore("},")
        assertTrue(
            whenBlock.contains("null -> navigate(TopScreen.AutoModeSettings)"),
            "При engineId=null (auto-режим) вкладка Параметры должна открывать AutoModeSettings. " +
                "Ветка null отсутствует — нажатие Параметры в auto-режиме открывает Servers вместо " +
                "экрана приоритетов движков.",
        )
    }

    @Test
    fun `null ветка стоит до else в when блоке`() {
        val whenBlock = source
            .substringAfter("onOpenEngineParams = { engineId ->")
            .substringBefore("},")
        val nullIdx = whenBlock.indexOf("null -> navigate(TopScreen.AutoModeSettings)")
        val elseIdx = whenBlock.indexOf("else ->")
        assertTrue(nullIdx >= 0, "null-ветка отсутствует в when(engineId)")
        assertTrue(elseIdx >= 0, "else-ветка отсутствует в when(engineId)")
        assertTrue(nullIdx < elseIdx, "null-ветка должна быть до else (иначе else перехватит null)")
    }
}
