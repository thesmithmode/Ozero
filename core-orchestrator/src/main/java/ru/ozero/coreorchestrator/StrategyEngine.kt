package ru.ozero.coreorchestrator

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult

data class Candidate(
    val engineId: EngineId,
    val config: EngineConfig,
    val priority: Int = PRIORITY_BYEDPI,
    /**
     * Кандидату нужен исходящий UDP (QUIC). При CGNAT/UDP-фильтре провайдера
     * StrategyEngine отфильтрует такие кандидаты и оставит TCP-fallback.
     */
    val requiresUdp: Boolean = false,
) {
    companion object {
        const val PRIORITY_HYSTERIA2_NATIVE = 11
        const val PRIORITY_XRAY_VLESS_REALITY = 10
        const val PRIORITY_XRAY_HYSTERIA2 = 9
        const val PRIORITY_XRAY_TROJAN = 8
        const val PRIORITY_AMNEZIA = 7
        const val PRIORITY_XRAY_SHADOWSOCKS = 6
        const val PRIORITY_BYEDPI = 5
        const val PRIORITY_TOR = 1
    }
}

/**
 * Источник дополнительных кандидатов (Xray из подписки, AmneziaWG, и т. п.).
 * Декомпозиция: сборку JSON-конфигов делает специфичный модуль (engine-xray),
 * StrategyEngine не зависит от деталей протоколов.
 */
fun interface CandidateSource {
    suspend fun candidates(): List<Candidate>
}

class StrategyEngine(
    private val engines: Map<EngineId, Engine>,
    private val extraSources: List<CandidateSource> = emptyList(),
    private val parallelProbeCount: Int = DEFAULT_PARALLEL_PROBE,
    /**
     * Доступен ли исходящий UDP. По умолчанию `true` (open NAT). При CGNAT/UDP-фильтре
     * выставляется `false` извне (например, по результату `CgnatDetector`) — тогда
     * `buildCandidates()` отфильтрует UDP-зависимые кандидаты (Hysteria2 native).
     */
    private val udpReachable: () -> Boolean = { true },
) {

    suspend fun buildCandidates(): List<Candidate> {
        val list = mutableListOf<Candidate>()
        for (source in extraSources) {
            list += source.candidates()
        }
        list += Candidate(
            engineId = EngineId.BYEDPI,
            config = EngineConfig.ByeDpi(),
            priority = Candidate.PRIORITY_BYEDPI,
        )
        val udpOk = udpReachable()
        return list
            .filter { udpOk || !it.requiresUdp }
            .sortedByDescending { it.priority }
    }

    /**
     * Параллельный probe первых [parallelProbeCount] кандидатов.
     * После завершения всех — возвращает первый по приоритету с успешным probe.
     * Это означает: даже если низко-приоритетный кандидат отвечает быстрее,
     * мы предпочитаем высоко-приоритетный, если он тоже успешен.
     */
    suspend fun pickBest(candidates: List<Candidate>): Candidate? {
        if (candidates.isEmpty()) return null
        val top = candidates.take(parallelProbeCount)
        val results = coroutineScope {
            top.map { c ->
                async {
                    val engine = engines[c.engineId]
                    val res: ProbeResult = engine?.probe() ?: ProbeResult.Failure("engine отсутствует")
                    c to res
                }
            }.awaitAll()
        }
        return results.firstOrNull { (_, r) -> r is ProbeResult.Success }?.first
    }

    private companion object {
        const val DEFAULT_PARALLEL_PROBE = 3
    }
}
