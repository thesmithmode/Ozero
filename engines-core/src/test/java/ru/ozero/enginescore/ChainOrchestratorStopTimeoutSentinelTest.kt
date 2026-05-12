package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ChainOrchestratorStopTimeoutSentinelTest {

    @Test
    fun `stopInternal обязан использовать withTimeoutOrNull per-plugin`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/enginescore/ChainOrchestrator.kt")
        assertTrue(f.exists(), "ChainOrchestrator.kt не найден: $f")
        val source = f.readText()
        val block = source.substringAfter("private suspend fun stopInternal()")
            .substringBefore("private companion object")
        assertTrue(
            block.contains("withTimeoutOrNull") && block.contains("stopTimeoutMs()"),
            "stopInternal обязан гейтить plugin.stop() через withTimeoutOrNull(plugin.stopTimeoutMs()). " +
                "Без timeout висящий plugin.stop() блокирует mutex и весь следующий start. Block:\n$block",
        )
    }
}
