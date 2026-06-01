package ru.ozero.enginescore

import java.net.URI

object LogSanitizer {

    fun sanitize(text: String): String {
        var out = text
        out = USERINFO_URI.replace(out) { m -> "${m.groupValues[1]}://<redacted>@${m.groupValues[3]}" }
        out = PROXY_URI.replace(out, "<redacted-uri>")
        out = KEYED_LONG_TOKEN.replace(out) { m -> "${m.groupValues[1]}=<redacted-token>" }
        out = LONG_TOKEN.replace(out, "<redacted-token>")
        return out
    }

    fun redactUrl(raw: String): String =
        runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme ?: return@runCatching "<redacted-uri>"
            val host = uri.host ?: return@runCatching "$scheme://<redacted>"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$host$port/<redacted>"
        }.getOrElse { "<redacted-uri>" }

    private val USERINFO_URI = Regex(
        "(?i)(\\w+)://([^:/@\\s]+(?::[^@\\s]*)?)@([^\\s/]+)",
    )

    private val PROXY_URI = Regex(
        "(?i)\\b(vless|vmess|trojan|ss|hysteria2?|tuic|naive\\+https?|wireguard|awg)://\\S+",
    )

    private val KEYED_LONG_TOKEN = Regex(
        "(?i)\\b([a-z][a-z0-9_-]{0,31})=([A-Za-z0-9+/_-]{24,})",
    )

    private val LONG_TOKEN = Regex(
        "[A-Za-z0-9+/_=-]{32,}",
    )
}
