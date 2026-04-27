package ru.ozero.enginehysteria2.strategy

import android.util.Log
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.CandidateSource
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.uri.Hysteria2Server
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import ru.ozero.enginehysteria2.config.Hy2BuildOptions
import ru.ozero.enginehysteria2.config.Hy2ConfigBuilder

class Hy2CandidateSource(
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val builder: Hy2ConfigBuilder = Hy2ConfigBuilder(),
    private val basePort: Int = DEFAULT_BASE_PORT,
    private val maxCandidates: Int = DEFAULT_MAX,
) : CandidateSource {

    override suspend fun candidates(): List<Candidate> {
        val live: List<ServerEntity> = serverDao.getLiveServers()
        val out = mutableListOf<Candidate>()
        var portOffset = 0
        for (entity in live) {
            if (out.size >= maxCandidates) break
            val candidate = toCandidate(entity, basePort + portOffset) ?: continue
            out += candidate
            portOffset++
        }
        Log.i(TAG, "построено ${out.size} Hy2-кандидатов из ${live.size} live-серверов")
        return out
    }

    private fun toCandidate(entity: ServerEntity, port: Int): Candidate? {
        val parsed = parser.parse(entity.uri)
        if (parsed !is ParsedServer.Hysteria2) return null
        val server: Hysteria2Server = parsed.server

        val rangeStart = server.portRangeStart
        val rangeEnd = server.portRangeEnd
        val portRange = if (rangeStart != null && rangeEnd != null) rangeStart..rangeEnd else null
        val opts = Hy2BuildOptions(
            socksPort = port,
            portRange = portRange,
            bandwidthUp = server.bandwidthUp,
            bandwidthDown = server.bandwidthDown,
            pinSHA256 = server.pinSHA256,
        )

        val json = runCatching { builder.build(server, opts) }.getOrElse {
            Log.w(TAG, "пропуск Hy2 — ошибка сборки конфига: ${it.message}")
            return null
        }

        return Candidate(
            engineId = EngineId.HYSTERIA2,
            config = EngineConfig.Hysteria2(json, port),
            priority = Candidate.PRIORITY_HYSTERIA2_NATIVE,
            requiresUdp = true,
        )
    }

    companion object {
        const val DEFAULT_BASE_PORT = 11808
        const val DEFAULT_MAX = 5
        private const val TAG = "Hy2CandidateSource"
    }
}
