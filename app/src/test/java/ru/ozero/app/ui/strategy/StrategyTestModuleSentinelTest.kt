package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyTestModuleSentinelTest {

    @Test
    fun `strategy scan engine binding is qualified and reuses production ByeDpiEngine singleton`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/app/di/StrategyTestModule.kt")
            .readText()

        assertTrue(source.contains("annotation class StrategyTestEngine"))
        assertTrue(source.contains("@StrategyTestEngine"))
        assertTrue(
            source.contains("fun provideStrategyTestEnginePlugin(byeDpiEngine: ByeDpiEngine)"),
            "strategy scan должен использовать существующий ByeDpiEngine из DI, а не создавать второй движок",
        )
        assertFalse(source.contains("ByeDpiEngine(ByeDpiProxy())"))
    }
}
