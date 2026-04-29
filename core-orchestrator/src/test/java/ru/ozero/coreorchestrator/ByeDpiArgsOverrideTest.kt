package ru.ozero.coreorchestrator

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.ByeDpiArgsSource
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import kotlin.test.assertEquals

class ByeDpiArgsOverrideTest {

    @Test
    fun `BYEDPI candidate использует args из ByeDpiArgsSource если не null`() = runTest {
        val customArgs = "-Ku -An -At -o1 -d4+s"
        val source = ByeDpiArgsSource { customArgs }
        val strategy = StrategyEngine(
            engines = emptyMap(),
            byedpiArgsSource = source,
        )

        val candidates = strategy.buildCandidates()

        val byedpi = candidates.first { it.engineId == EngineId.BYEDPI }
        val cfg = byedpi.config
        check(cfg is EngineConfig.ByeDpi)
        assertEquals(
            customArgs,
            cfg.args,
            "Когда ByeDpiArgsSource возвращает не-null, BYEDPI candidate обязан использовать эти args. " +
                "Это путь для пользовательского override через Settings UI.",
        )
    }

    @Test
    fun `BYEDPI candidate использует EngineConfig_ByeDpi default если source = null`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
            byedpiArgsSource = ByeDpiArgsSource { null },
        )

        val candidates = strategy.buildCandidates()
        val byedpi = candidates.first { it.engineId == EngineId.BYEDPI }
        val cfg = byedpi.config
        check(cfg is EngineConfig.ByeDpi)
        assertEquals(
            EngineConfig.ByeDpi().args,
            cfg.args,
            "Когда source возвращает null, BYEDPI candidate fallback на EngineConfig.ByeDpi default args.",
        )
    }

    @Test
    fun `BYEDPI candidate fallback на default если source отсутствует`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
        )

        val candidates = strategy.buildCandidates()
        val byedpi = candidates.first { it.engineId == EngineId.BYEDPI }
        val cfg = byedpi.config
        check(cfg is EngineConfig.ByeDpi)
        assertEquals(
            EngineConfig.ByeDpi().args,
            cfg.args,
            "Без ByeDpiArgsSource StrategyEngine должен использовать default args — backwards compatible.",
        )
    }

    @Test
    fun `пустая строка из source трактуется как null`() = runTest {
        val source = ByeDpiArgsSource { "" }
        val strategy = StrategyEngine(
            engines = emptyMap(),
            byedpiArgsSource = source,
        )

        val candidates = strategy.buildCandidates()
        val byedpi = candidates.first { it.engineId == EngineId.BYEDPI }
        val cfg = byedpi.config
        check(cfg is EngineConfig.ByeDpi)
        assertEquals(
            EngineConfig.ByeDpi().args,
            cfg.args,
            "Пустая строка из source НЕ override — иначе пользователь обнулит и сломает byedpi.",
        )
    }
}
