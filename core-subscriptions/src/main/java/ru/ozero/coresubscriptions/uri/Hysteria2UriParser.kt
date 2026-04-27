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

        val sniRaw = query["sni"]
        val sniList = sniRaw?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val (mportStart, mportEnd) = parseMport(query["mport"])

        return UriParseResult.Ok(
            Hysteria2Server(
                password = password,
                host = host,
                port = port,
                sni = sniList.firstOrNull(),
                sniAlternatives = if (sniList.size > 1) sniList.drop(1) else emptyList(),
                insecure = query["insecure"] == "1" || query["insecure"]?.lowercase() == "true",
                obfs = query["obfs"],
                obfsPassword = query["obfs-password"],
                pinSHA256 = query["pinSHA256"]?.takeIf { it.isNotBlank() },
                portRangeStart = mportStart,
                portRangeEnd = mportEnd,
                bandwidthUp = query["up"]?.takeIf { it.isNotBlank() },
                bandwidthDown = query["down"]?.takeIf { it.isNotBlank() },
                remark = decodeFragment(parsed.rawFragment),
            ),
        )
    }

        private fun parseMport(s: String?): Pair<Int?, Int?> {
        if (s.isNullOrBlank()) return null to null
        val parts = s.split('-')
        if (parts.size != 2) return null to null
        val a = parts[0].toIntOrNull() ?: return null to null
        val b = parts[1].toIntOrNull() ?: return null to null
        if (a !in 1..65535 || b !in 1..65535 || a > b) return null to null
        return a to b
    }

    private companion object {
        val VALID_SCHEMES = listOf("hysteria2", "hy2")
    }
}
