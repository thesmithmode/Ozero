package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyTestModuleSentinelTest {

    @Test
    fun `strategy scan engine binding is qualified and uses isolated ByeDpiEngine`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/app/di/StrategyTestModule.kt")
            .readText()

        assertTrue(source.contains("annotation class StrategyTestEngine"))
        assertTrue(source.contains("@StrategyTestEngine"))
        assertTrue(
            source.contains("fun provideStrategyTestEnginePlugin(): EnginePlugin"),
            "strategy scan не должен получать production ByeDpiEngine из DI",
        )
        assertTrue(source.contains("ByeDpiEngine(proxy = ByeDpiProxy())"))
        assertFalse(source.contains("provideStrategyTestEnginePlugin(byeDpiEngine: ByeDpiEngine)"))
    }
}
