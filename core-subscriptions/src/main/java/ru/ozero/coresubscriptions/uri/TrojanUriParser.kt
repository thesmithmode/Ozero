package ru.ozero.coresubscriptions.uri

import java.net.URI

class TrojanUriParser {

    fun parse(uri: String): UriParseResult<TrojanServer> {
        if (!uri.startsWith("trojan://")) return UriParseResult.Error("scheme не trojan")

        val parsed =
            try {
                URI(uri)
            } catch (e: IllegalArgumentException) {
                return UriParseResult.Error("невалидный URI: ${e.message}")
            }

        val password = parsed.userInfo
        if (password.isNullOrBlank()) return UriParseResult.Error("отсутствует password")
        val host = parsed.host
        if (host.isNullOrBlank()) return UriParseResult.Error("отсутствует host")
        val port = parsed.port
        if (port == -1) return UriParseResult.Error("отсутствует port")

        val query = parsed.rawQuery?.let(::parseQuery) ?: emptyMap()

        return UriParseResult.Ok(
            TrojanServer(
                password = password,
                host = host,
                port = port,
                sni = query["sni"],
                peer = query["peer"],
                remark = decodeFragment(parsed.rawFragment),
            ),
        )
    }
}
