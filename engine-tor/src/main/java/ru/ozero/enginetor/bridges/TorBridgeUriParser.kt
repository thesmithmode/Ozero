package ru.ozero.enginetor.bridges

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TorBridgeUriParser {

    fun parse(uri: String): Result<TorBridge> {
        if (!uri.startsWith("bridge://")) return Result.failure(IllegalArgumentException("scheme не bridge"))

        val parsed = try {
            URI(uri)
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("невалидный URI: ${e.message}"))
        }

        val transport = parsed.userInfo
        if (transport.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("отсутствует transport (userinfo)"))
        }
        if (transport !in VALID_TRANSPORTS) {
            return Result.failure(IllegalArgumentException("неизвестный transport: $transport"))
        }

        val host = parsed.host ?: return Result.failure(IllegalArgumentException("отсутствует host"))
        val port = parsed.port

        val rawQuery = parsed.rawQuery ?: ""
        val params = rawQuery.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) to
                URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
        }.toMap()

        val fingerprint = params["fingerprint"]
            ?: return Result.failure(IllegalArgumentException("отсутствует fingerprint"))

                        val address = when {
            port == -1 -> host
            host.contains(':') -> "[$host]:$port"
            else -> "$host:$port"
        }
        val args = params.filterKeys { it != "fingerprint" }

        val remark = parsed.rawFragment
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.takeIf { it.isNotEmpty() }

        return Result.success(
            TorBridge(
                transport = transport,
                address = address,
                fingerprint = fingerprint,
                args = args.toSortedMap(), 
                remark = remark,
            ),
        )
    }

    private companion object {
        val VALID_TRANSPORTS = setOf("obfs4", "snowflake", "webtunnel", "meek_lite", "conjure")
    }
}
