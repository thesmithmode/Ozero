package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ChainOrchestratorStopContractSentinelTest {

    @Test
    fun `stopInternal does not cancel plugin stop before cleanup completes`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/enginescore/ChainOrchestrator.kt")
        assertTrue(f.exists(), "ChainOrchestrator.kt не найден: $f")
        val source = f.readText()
        val block = source.substringAfter("private suspend fun stopInternal()")
            .substringBefore("private companion object")
        assertTrue(
            !block.contains("withTimeoutOrNull") && block.contains("plugin.stop()"),
            "stopInternal must not wrap plugin.stop() in a timeout that cancels cleanup. Block:\n$block",
        )
    }
}
