package ru.ozero.coreorchestrator

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ManualEngineSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualEngineFilterTest {

    private val xrayCandidate = Candidate(
        engineId = EngineId.XRAY,
        config = EngineConfig.Xray(configJson = "{}"),
        priority = Candidate.PRIORITY_XRAY_VLESS_REALITY,
    )

    private val hy2Candidate = Candidate(
        engineId = EngineId.HYSTERIA2,
        config = EngineConfig.Hysteria2(configJson = "{}"),
        priority = Candidate.PRIORITY_HYSTERIA2_NATIVE,
    )

    @Test
    fun `manualEngine = XRAY → buildCandidates оставляет только XRAY (не BYEDPI fallback)`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(xrayCandidate, hy2Candidate) }),
            manualEngineSource = ManualEngineSource { EngineId.XRAY },
        )

        val candidates = strategy.buildCandidates()

        assertEquals(1, candidates.size, "manualEngine=XRAY → ровно один кандидат")
        assertEquals(EngineId.XRAY, candidates[0].engineId)
        assertTrue(
            candidates.none { it.engineId == EngineId.BYEDPI },
            "manualEngine=XRAY → BYEDPI fallback должен быть отфильтрован — иначе игнорируется выбор юзера",
        )
    }

    @Test
    fun `manualEngine = null → старая логика (все engines + BYEDPI fallback)`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(xrayCandidate) }),
            manualEngineSource = ManualEngineSource { null },
        )

        val candidates = strategy.buildCandidates()

        assertTrue(
            candidates.any { it.engineId == EngineId.XRAY },
            "auto-режим → XRAY кандидат сохраняется",
        )
        assertTrue(
            candidates.any { it.engineId == EngineId.BYEDPI },
            "auto-режим → BYEDPI fallback сохраняется",
        )
    }

    @Test
    fun `manualEngine = XRAY но в списке нет XRAY → empty result`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(hy2Candidate) }),
            manualEngineSource = ManualEngineSource { EngineId.XRAY },
        )

        val candidates = strategy.buildCandidates()

        assertEquals(
            0,
            candidates.size,
            "manualEngine=XRAY, но кандидатов XRAY нет → empty list. UI должен показать «нет серверов для XRAY».",
        )
    }

    @Test
    fun `manualEngine = BYEDPI → возвращается только BYEDPI candidate`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(xrayCandidate, hy2Candidate) }),
            manualEngineSource = ManualEngineSource { EngineId.BYEDPI },
        )

        val candidates = strategy.buildCandidates()

        assertEquals(1, candidates.size)
        assertEquals(EngineId.BYEDPI, candidates[0].engineId)
    }

    @Test
    fun `manualEngine() метод возвращает текущее значение из source`() = runTest {
        val strategy = StrategyEngine(
            engines = emptyMap(),
            manualEngineSource = ManualEngineSource { EngineId.HYSTERIA2 },
        )
        assertEquals(EngineId.HYSTERIA2, strategy.manualEngine())
    }

    @Test
    fun `manualEngine() возвращает null если source отсутствует`() = runTest {
        val strategy = StrategyEngine(engines = emptyMap())
        assertEquals(null, strategy.manualEngine())
    }
}
