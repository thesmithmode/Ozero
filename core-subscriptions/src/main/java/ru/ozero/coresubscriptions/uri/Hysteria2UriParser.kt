package ru.ozero.coresubscriptions.uri

import java.net.URI

class Hysteria2UriParser {

    fun parse(uri: String): UriParseResult<Hysteria2Server> {
        val prefix = VALID_SCHEMES.firstOrNull { uri.startsWith("$it://") }
            ?: return UriParseResult.Error("scheme не hysteria2/hy2")

        val normalized = if (prefix == "hy2") uri.replaceFirst("hy2://", "hysteria2://") else uri

        val parsed =
            try {
                URI(normalized)
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
            Hysteria2Server(
                password = password,
                host = host,
                port = port,
                sni = query["sni"],
                insecure = query["insecure"] == "1" || query["insecure"]?.lowercase() == "true",
                obfs = query["obfs"],
                obfsPassword = query["obfs-password"],
                remark = decodeFragment(parsed.rawFragment),
            ),
        )
    }

    private companion object {
        val VALID_SCHEMES = listOf("hysteria2", "hy2")
    }
}
