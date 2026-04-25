package ru.ozero.enginenaive.strategy

import android.util.Log
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.CandidateSource
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import ru.ozero.enginenaive.config.NaiveConfigBuilder

/**
 * Превращает Naive-серверы из подписки в кандидаты NAIVE с приоритетом 6
 * (между AmneziaWG=7 и Xray-SS=6 — выше SS из-за Chrome-fingerprint).
 *
 * Naive — TCP (HTTP/2), не UDP-зависим. requiresUdp=false → не отбрасывается при CGNAT.
 */
class NaiveCandidateSource(
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val builder: NaiveConfigBuilder = NaiveConfigBuilder(),
    private val basePort: Int = DEFAULT_BASE_PORT,
    private val maxCandidates: Int = DEFAULT_MAX,
) : CandidateSource {

    override suspend fun candidates(): List<Candidate> {
        val live = serverDao.getLiveServers()
        val out = mutableListOf<Candidate>()
        var portOffset = 0
        for (entity in live) {
            if (out.size >= maxCandidates) break
            val parsed = parser.parse(entity.uri)
            if (parsed !is ParsedServer.Naive) continue
            val port = basePort + portOffset
            val jsonResult = runCatching { builder.build(parsed.server, port) }
            val json = jsonResult.getOrNull()
            if (json == null) {
                Log.w(TAG, "пропуск Naive — ошибка сборки: ${jsonResult.exceptionOrNull()?.message}")
                continue
            }
            out += Candidate(
                engineId = EngineId.NAIVE,
                config = EngineConfig.Naive(proxyUrl = json, socksPort = port),
                priority = PRIORITY,
                requiresUdp = false,
            )
            portOffset++
        }
        Log.i(TAG, "построено ${out.size} Naive-кандидатов из ${live.size} live-серверов")
        return out
    }

    companion object {
        const val DEFAULT_BASE_PORT = 12808
        const val DEFAULT_MAX = 5
        // Между Xray-SS (6) и AmneziaWG (7). Chrome-fingerprint надёжнее SS, но
        // ниже WG-обфускации, потому что HTTP/2 сильнее зависит от стабильности TLS-handshake.
        const val PRIORITY = 6
        private const val TAG = "NaiveCandidateSource"
    }
}
