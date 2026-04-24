package ru.ozero.coresubscriptions.uri

import java.net.URI

class VlessUriParser {

    fun parse(uri: String): UriParseResult<VlessServer> {
        if (!uri.startsWith("vless://")) return UriParseResult.Error("scheme не vless")
        val parsed =
            try {
                URI(uri)
            } catch (e: IllegalArgumentException) {
                return UriParseResult.Error("невалидный URI: ${e.message}")
            }

        val userInfo = parsed.userInfo
        if (userInfo.isNullOrBlank()) return UriParseResult.Error("отсутствует UUID")
        val host = parsed.host
        if (host.isNullOrBlank()) return UriParseResult.Error("отсутствует host")
        val port = parsed.port
        if (port == -1) return UriParseResult.Error("отсутствует port")

        val query = parsed.rawQuery?.let(::parseQuery) ?: emptyMap()

        return UriParseResult.Ok(
            VlessServer(
                uuid = userInfo,
                host = host,
                port = port,
                encryption = query["encryption"] ?: "none",
                security = query["security"] ?: "none",
                fingerprint = query["fp"],
                publicKey = query["pbk"],
                shortId = query["sid"],
                sni = query["sni"],
                transport = query["type"] ?: "tcp",
                path = query["path"],
                flow = query["flow"]?.takeIf { it.isNotEmpty() },
                remark = decodeFragment(parsed.rawFragment),
            ),
        )
    }
}
