package ru.ozero.coreorchestrator

import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult

data class Candidate(
    val engineId: EngineId,
    val config: EngineConfig,
)

class StrategyEngine(private val engines: Map<EngineId, Engine>) {

    fun buildCandidates(): List<Candidate> =
        listOf(
            Candidate(
                engineId = EngineId.BYEDPI,
                config = EngineConfig.ByeDpi(),
            ),
        )

    // Probe выполняется через локальный SOCKS порт движка ДО TUN-активации.
    // Возвращает первый кандидат с успешным probe.
    suspend fun pickBest(candidates: List<Candidate>): Candidate? {
        for (candidate in candidates) {
            val engine = engines[candidate.engineId] ?: continue
            val result = engine.probe()
            if (result is ProbeResult.Success) return candidate
        }
        return null
    }
}
