package ru.ozero.engineurnetwork

import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.CandidateSource

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
                                                                                mode = "consumer",
                ),
                priority = Candidate.PRIORITY_URNETWORK,
            ),
        )
    }
}
