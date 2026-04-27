package ru.ozero.coresubscriptions.uri

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class NaiveUriParser {

            @Suppress("ReturnCount")
    fun parse(uri: String): UriParseResult<NaiveServer> {
        if (!uri.startsWith("naive+")) return UriParseResult.Error("scheme не naive+")

        val withoutPrefix = uri.removePrefix("naive+")
        val schemeEnd = withoutPrefix.indexOf("://")
        if (schemeEnd <= 0) return UriParseResult.Error("отсутствует подсхема (https/quic)")
        val subScheme = withoutPrefix.substring(0, schemeEnd).lowercase()
        if (subScheme !in VALID_SUBSCHEMES) {
            return UriParseResult.Error("неизвестная подсхема: $subScheme")
        }

        val parsed = try {
            URI(withoutPrefix)
        } catch (e: IllegalArgumentException) {
            return UriParseResult.Error("невалидный URI: ${e.message}")
        }

        val userInfo = parsed.userInfo
        if (userInfo.isNullOrBlank() || !userInfo.contains(':')) {
            return UriParseResult.Error("отсутствует user:pass")
        }
        val (rawUser, rawPass) = userInfo.split(':', limit = 2).let { it[0] to it[1] }
        if (rawUser.isBlank() || rawPass.isBlank()) {
            return UriParseResult.Error("user или pass пустые")
        }

        val host = parsed.host
        if (host.isNullOrBlank()) return UriParseResult.Error("отсутствует host")
        val port = parsed.port
        if (port == -1) return UriParseResult.Error("отсутствует port")

        return UriParseResult.Ok(
            NaiveServer(
                scheme = subScheme,
                username = URLDecoder.decode(rawUser, StandardCharsets.UTF_8),
                password = URLDecoder.decode(rawPass, StandardCharsets.UTF_8),
                host = host,
                port = port,
                remark = decodeFragment(parsed.rawFragment),
            ),
        )
    }

    private companion object {
        val VALID_SUBSCHEMES = setOf("https", "quic", "https3")
    }
}
