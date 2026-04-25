package ru.ozero.engineamnezia.strategy

import android.util.Log
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.CandidateSource
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import ru.ozero.engineamnezia.config.AwgConfigBuilder

/**
 * Превращает AmneziaWG-серверы из подписки в кандидаты с приоритетом
 * [Candidate.PRIORITY_AMNEZIA]=7. UDP-зависимый: requiresUdp=true → отфильтрован
 * StrategyEngine при CGNAT (как и Hy2 native).
 */
class AwgCandidateSource(
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val builder: AwgConfigBuilder = AwgConfigBuilder(),
    private val maxCandidates: Int = DEFAULT_MAX,
) : CandidateSource {

    override suspend fun candidates(): List<Candidate> {
        val live = serverDao.getLiveServers()
        val out = mutableListOf<Candidate>()
        for (entity in live) {
            if (out.size >= maxCandidates) break
            val parsed = parser.parse(entity.uri)
            if (parsed !is ParsedServer.AmneziaWg) continue
            val ini = runCatching { builder.build(parsed.server) }.getOrElse {
                Log.w(TAG, "пропуск Awg — ошибка сборки: ${it.message}")
                continue
            }
            out += Candidate(
                engineId = EngineId.AMNEZIA,
                config = EngineConfig.Amnezia(ini, socksPort = 0),
                priority = Candidate.PRIORITY_AMNEZIA,
                requiresUdp = true,
            )
        }
        Log.i(TAG, "построено ${out.size} Awg-кандидатов из ${live.size} live-серверов")
        return out
    }

    companion object {
        const val DEFAULT_MAX = 5
        private const val TAG = "AwgCandidateSource"
    }
}
