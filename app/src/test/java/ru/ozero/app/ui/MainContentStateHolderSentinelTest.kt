package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainContentStateHolderSentinelTest {

    private val source: String by lazy {
        val f = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/MainScreen.kt",
        )
        assertTrue(f.exists(), "MainScreen.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `ExpertMainContent принимает state и callbacks data class — не 19 параметров`() {
        val sig = Regex("""(?:private|internal) fun ExpertMainContent\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(source)?.groupValues?.get(1).orEmpty()
        val params = sig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(
            params.size == 2,
            "ExpertMainContent обязан принимать ровно 2 параметра (state, callbacks). Найдено: ${params.size}\n$sig",
        )
        assertTrue(
            sig.contains("state: ExpertMainState") && sig.contains("callbacks: ExpertMainCallbacks"),
            "ExpertMainContent сигнатура: (state: ExpertMainState, callbacks: ExpertMainCallbacks). Текущая:\n$sig",
        )
    }

    @Test
    fun `SimpleMainContent принимает state и callbacks data class — не 10 параметров`() {
        val sig = Regex("""(?:private|internal) fun SimpleMainContent\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(source)?.groupValues?.get(1).orEmpty()
        val params = sig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(
            params.size == 2,
            "SimpleMainContent обязан принимать ровно 2 параметра (state, callbacks). Найдено: ${params.size}\n$sig",
        )
    }

    @Test
    fun `ExpertMainContent не использует Suppress LongParameterList — антипаттерн убран`() {
        val funBlock = source.substringAfter("data class ExpertMainCallbacks")
            .substringAfter("fun ExpertMainContent")
        val headerBlock = source.substringBefore("fun ExpertMainContent")
            .takeLast(200)
        assertFalse(
            headerBlock.contains("@Suppress(\"LongParameterList\")"),
            "ExpertMainContent больше не должен иметь @Suppress(\"LongParameterList\") — " +
                "декомпозирован через ExpertMainState + ExpertMainCallbacks. Header:\n$headerBlock",
        )
        assertTrue(funBlock.isNotEmpty(), "ExpertMainContent тело должно существовать после рефактора")
    }

    @Test
    fun `SimpleMainContent не использует Suppress LongParameterList — антипаттерн убран`() {
        val headerBlock = source.substringBefore("fun SimpleMainContent")
            .takeLast(200)
        assertFalse(
            headerBlock.contains("@Suppress(\"LongParameterList\")"),
            "SimpleMainContent больше не должен иметь @Suppress(\"LongParameterList\"). Header:\n$headerBlock",
        )
    }

    @Test
    fun `state holder data classes объявлены`() {
        val required = listOf(
            "data class ExpertMainState(",
            "data class ExpertMainCallbacks(",
            "data class SimpleMainState(",
            "data class SimpleMainCallbacks(",
        )
        for (decl in required) {
            assertTrue(source.contains(decl), "Не найдено объявление: $decl")
        }
    }
}
