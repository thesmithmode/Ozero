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
                val pairId = entity.pairId
                if (entity.role == "entry" && pairId != null) {
                    chainCandidate(parsed.server, pairId, port)
                } else {
                    vlessCandidate(parsed.server, port)
                }
            }
            is ParsedServer.Hysteria2 -> {
                val json = runCatching { builder.build(parsed.server, port) }.getOrElse {
                    Log.w(TAG, "пропуск Hy2 — ошибка конфига: ${it.message}")
                    return null
                }
                Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_HYSTERIA2)
            }
            is ParsedServer.Trojan -> {
                val json = runCatching { builder.build(parsed.server, port) }.getOrElse {
                    Log.w(TAG, "пропуск Trojan — ошибка конфига: ${it.message}")
                    return null
                }
                Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_TROJAN)
            }
            is ParsedServer.Shadowsocks -> {
                val json = runCatching { builder.build(parsed.server, port) }.getOrElse {
                    Log.w(TAG, "пропуск SS — ошибка конфига: ${it.message}")
                    return null
                }
                Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_SHADOWSOCKS)
            }
            is ParsedServer.Error -> {
                Log.w(TAG, "пропуск URI: ${parsed.reason}")
                null
            }
            is ParsedServer.AmneziaWg, is ParsedServer.Naive -> null
        }

    private suspend fun chainCandidate(
        entry: VlessServer,
        exitPairId: String,
        port: Int,
    ): Candidate? {
        if (!entry.isSafe2026()) {
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
        if (!exitParsed.server.isSafe2026()) {
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
        if (!server.isSafe2026()) {
            Log.w(TAG, "пропуск VLESS — небезопасный transport=${server.transport}/security=${server.security}")
            return null
        }
        val json = runCatching { builder.build(server, port) }.getOrNull() ?: return null
        return Candidate(EngineId.XRAY, EngineConfig.Xray(json, port), Candidate.PRIORITY_XRAY_VLESS_REALITY)
    }

    private fun VlessServer.isSafe2026(): Boolean =
        transport.lowercase() in TRANSPORT_SAFE_2026 && security.lowercase() == "reality"

    companion object {
        const val DEFAULT_BASE_PORT = 10808
        const val DEFAULT_MAX = 5
        private const val TAG = "XrayCandidateSource"
        val TRANSPORT_SAFE_2026 = setOf("tcp", "xhttp", "grpc", "ws")
    }
}
