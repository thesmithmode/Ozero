package ru.ozero.engineurnetwork

import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.CandidateSource

/**
 * Источник кандидатов URnetwork для StrategyEngine.
 *
 * Используется как fallback: URnetwork добавляется в конец списка кандидатов
 * (самый низкий приоритет кроме TOR) когда [enabled] = true и [jwtToken] задан.
 *
 * Инъектируется через DI в app-модуле и передаётся в StrategyEngine.extraSources.
 */
class UrnetworkCandidateSource(
    private val enabled: () -> Boolean,
    private val jwtToken: () -> String?,
    private val apiUrl: String = "https://api.urnetwork.com",
    private val region: String? = null,
) : CandidateSource {

    override suspend fun candidates(): List<Candidate> {
        if (!enabled()) return emptyList()
        val jwt = jwtToken()
        if (jwt.isNullOrBlank()) return emptyList()

        return listOf(
            Candidate(
                engineId = EngineId.URNETWORK,
                config = EngineConfig.Urnetwork(
                    jwtToken = jwt,
                    apiUrl = apiUrl,
                    region = region,
                    // ВАЖНО: mode жёстко "consumer" — provider mode требует явного opt-in юзера
                    // (раздаёт чужой трафик через девайс). Никогда не выставлять "provider"
                    // из этого источника, иначе пользователь не знает что трафик чужих идёт через него.
                    mode = "consumer",
                ),
                priority = Candidate.PRIORITY_URNETWORK,
            ),
        )
    }
}
