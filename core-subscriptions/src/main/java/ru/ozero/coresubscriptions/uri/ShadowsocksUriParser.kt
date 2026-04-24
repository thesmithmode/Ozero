package ru.ozero.coresubscriptions.uri

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

class ShadowsocksUriParser {

    fun parse(uri: String): UriParseResult<ShadowsocksServer> {
        if (!uri.startsWith("ss://")) return UriParseResult.Error("scheme не ss")

        val parsed =
            try {
                URI(uri)
            } catch (e: IllegalArgumentException) {
                return UriParseResult.Error("невалидный URI: ${e.message}")
            }

        val userInfo = parsed.userInfo ?: return UriParseResult.Error("отсутствует userInfo")
        val host = parsed.host
        if (host.isNullOrBlank()) return UriParseResult.Error("отсутствует host")
        val port = parsed.port
        if (port == -1) return UriParseResult.Error("отсутствует port")

        val decoded = decodeUserInfo(userInfo) ?: return UriParseResult.Error("невалидный userInfo")
        val idx = decoded.indexOf(':')
        if (idx <= 0 || idx == decoded.length - 1) return UriParseResult.Error("userInfo не method:password")

        val method = decoded.substring(0, idx)
        val password = decoded.substring(idx + 1)

        return UriParseResult.Ok(
            ShadowsocksServer(
                method = method,
                password = password,
                host = host,
                port = port,
                remark = decodeFragment(parsed.rawFragment),
            ),
        )
    }

    private fun decodeUserInfo(raw: String): String? {
        if (':' in raw) return raw
        return try {
            String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
