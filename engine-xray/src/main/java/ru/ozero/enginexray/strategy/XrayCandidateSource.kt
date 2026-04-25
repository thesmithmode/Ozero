package ru.ozero.enginexray.strategy

import android.util.Log
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.CandidateSource
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import ru.ozero.coresubscriptions.uri.VlessServer
import ru.ozero.enginexray.config.XrayConfigBuilder

/**
 * Превращает live-серверы из подписки в Xray-кандидаты с приоритетами по SPEC §4.3.
 *
 * Каждому кандидату выделяется уникальный SOCKS-порт начиная с [basePort],
 * чтобы StrategyEngine мог пробовать их параллельно, не конфликтуя на одном порту.
 *
 * Фильтрация: VLESS+Reality допускается только с transport ∈ TRANSPORT_SAFE_2026
 * (xhttp / grpc / ws — SPEC §4.3, security=tls/reality задаётся отдельно).
 * Остальные транспорты выбрасываются.
 */
class XrayCandidateSource(
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val builder: XrayConfigBuilder = XrayConfigBuilder(),
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
        Log.i(TAG, "построено ${out.size} Xray-кандидатов из ${live.size} live-серверов")
        return out
    }

    private suspend fun toCandidate(entity: ServerEntity, port: Int): Candidate? =
        when (val parsed = parser.parse(entity.uri)) {
            is ParsedServer.Vless -> {
                if (entity.role == "entry" && entity.pairId != null) {
                    chainCandidate(parsed.server, entity.pairId, port)
                } else {
                    vlessCandidate(parsed.server, port)
                }
            }
            is ParsedServer.Hysteria2 -> {
                val json = runCatching { builder.build(parsed.server, port) }.getOrNull() ?: return null
                Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_HYSTERIA2)
            }
            is ParsedServer.Trojan -> {
                val json = runCatching { builder.build(parsed.server, port) }.getOrNull() ?: return null
                Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_TROJAN)
            }
            is ParsedServer.Shadowsocks -> {
                val json = runCatching { builder.build(parsed.server, port) }.getOrNull() ?: return null
                Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_SHADOWSOCKS)
            }
            is ParsedServer.Error -> {
                Log.w(TAG, "пропуск URI: ${parsed.reason}")
                null
            }
        }

    private suspend fun chainCandidate(
        entry: VlessServer,
        exitPairId: String,
        port: Int,
    ): Candidate? {
        val transport = entry.transport.lowercase()
        if (transport !in TRANSPORT_SAFE_2026 || entry.security.lowercase() != "reality") {
            Log.w(TAG, "пропуск double-hop entry — небезопасный transport/security")
            return null
        }
        val exitEntity = serverDao.findById(exitPairId) ?: run {
            Log.w(TAG, "пропуск double-hop — exit (pairId=$exitPairId) не найден в БД")
            return null
        }
        val exitParsed = parser.parse(exitEntity.uri)
        if (exitParsed !is ParsedServer.Vless) {
            Log.w(TAG, "double-hop exit не VLESS: ${exitEntity.protocol}")
            return null
        }
        val exitTransport = exitParsed.server.transport.lowercase()
        if (exitTransport !in TRANSPORT_SAFE_2026 || exitParsed.server.security.lowercase() != "reality") {
            Log.w(TAG, "double-hop exit имеет небезопасный transport/security")
            return null
        }
        val json = runCatching { builder.buildChain(entry, exitParsed.server, port) }.getOrNull() ?: return null
        return Candidate(
            engineId = EngineId.XRAY,
            config = EngineConfig.Xray(json, port),
            priority = Candidate.PRIORITY_XRAY_VLESS_REALITY,
        )
    }

    private fun vlessCandidate(server: VlessServer, port: Int): Candidate? {
        val transport = server.transport.lowercase()
        if (transport !in TRANSPORT_SAFE_2026) {
            Log.w(TAG, "пропуск VLESS — небезопасный transport=$transport")
            return null
        }
        if (server.security.lowercase() != "reality") {
            Log.w(TAG, "пропуск VLESS — security=${server.security} не Reality")
            return null
        }
        val json = runCatching { builder.build(server, port) }.getOrNull() ?: return null
        return Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_VLESS_REALITY)
    }

    companion object {
        const val DEFAULT_BASE_PORT = 10808
        const val DEFAULT_MAX = 5
        private const val TAG = "XrayCandidateSource"
        val TRANSPORT_SAFE_2026 = setOf("xhttp", "grpc", "ws")
    }
}
