package ru.ozero.coresubscriptions.uri

import java.net.URI

/**
 * URI: `awg://<privateKey-base64url>@<host>:<port>?publicKey=...&presharedKey=...
 *      &allowedIPs=0.0.0.0/0,::/0&address=10.0.0.2/32&dns=1.1.1.1
 *      &mtu=1280&keepalive=25&jc=4&jmin=40&jmax=70&s1=0&s2=0
 *      &h1=1&h2=2&h3=3&h4=4#remark`
 *
 * Списки CSV. Числа hex/dec. h1..h4 принимают и dec, и `0xDEADBEEF`.
 */
class AmneziaWgUriParser {

    fun parse(uri: String): UriParseResult<AmneziaWgServer> {
        if (!uri.startsWith("awg://")) return UriParseResult.Error("scheme не awg")

        val parsed = try {
            URI(uri)
        } catch (e: IllegalArgumentException) {
            return UriParseResult.Error("невалидный URI: ${e.message}")
        }

        val privateKey = parsed.userInfo
        if (privateKey.isNullOrBlank()) return UriParseResult.Error("отсутствует privateKey")
        val host = parsed.host ?: return UriParseResult.Error("отсутствует host")
        val port = parsed.port
        if (port == -1) return UriParseResult.Error("отсутствует port")

        val q = parsed.rawQuery?.let(::parseQuery) ?: emptyMap()
        val publicKey = q["publicKey"]
        if (publicKey.isNullOrBlank()) return UriParseResult.Error("отсутствует publicKey")

        val server = AmneziaWgServer(
            privateKey = privateKey,
            publicKey = publicKey,
            host = host,
            port = port,
            presharedKey = q["presharedKey"]?.takeIf { it.isNotBlank() },
            allowedIps = q["allowedIPs"]?.csv() ?: listOf("0.0.0.0/0", "::/0"),
            addresses = q["address"]?.csv() ?: emptyList(),
            dns = q["dns"]?.csv() ?: emptyList(),
            mtu = q["mtu"]?.toIntOrNull() ?: AmneziaWgServer.DEFAULT_MTU,
            persistentKeepalive = q["keepalive"]?.toIntOrNull() ?: 0,
            jc = q["jc"]?.toIntOrNull() ?: 0,
            jmin = q["jmin"]?.toIntOrNull() ?: 0,
            jmax = q["jmax"]?.toIntOrNull() ?: 0,
            s1 = q["s1"]?.toIntOrNull() ?: 0,
            s2 = q["s2"]?.toIntOrNull() ?: 0,
            h1 = parseUint32(q["h1"]),
            h2 = parseUint32(q["h2"]),
            h3 = parseUint32(q["h3"]),
            h4 = parseUint32(q["h4"]),
            remark = decodeFragment(parsed.rawFragment),
        )
        return UriParseResult.Ok(server)
    }

    private fun parseUint32(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return if (s.startsWith("0x", ignoreCase = true)) {
            s.substring(2).toLongOrNull(16) ?: 0L
        } else {
            s.toLongOrNull() ?: 0L
        }
    }

    private fun String.csv(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
