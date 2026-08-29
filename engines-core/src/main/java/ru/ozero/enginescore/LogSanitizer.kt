package ru.ozero.enginescore

import java.net.URI

object LogSanitizer {

    fun sanitize(text: String): String {
        var out = text
        out = URL.replace(out, "<redacted-url>")
        out = USERINFO_URI.replace(out) { m -> "${m.groupValues[1]}://<redacted>@${m.groupValues[3]}" }
        out = PROXY_URI.replace(out, "<redacted-uri>")
        out = UUID.replace(out, "<redacted-uuid>")
        out = JSON_SENSITIVE_FIELD.replace(out) { m -> "${m.groupValues[1]}<redacted>${m.groupValues[3]}" }
        out = SENSITIVE_FIELD.replace(out) { m -> "${m.groupValues[1]}=<redacted-token>" }
        out = HOST_FIELD.replace(out) { m -> "${m.groupValues[1]}=<redacted-host>" }
        out = KEYED_LONG_TOKEN.replace(out) { m -> "${m.groupValues[1]}=<redacted-token>" }
        out = LONG_TOKEN.replace(out) { m ->
            if (m.value.isBareTokenLike()) "<redacted-token>" else m.value
        }
        return out.replace(Regex("\\s+"), " ").take(MAX_LENGTH)
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

    private val URL = Regex("(?i)\\bhttps?://[^\\s,;]+")

    private val UUID = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")

    private val SENSITIVE_FIELD = Regex(
        "(?i)\\b(authorization|auth|cookie|credential|token|password|username|uuid|private[_-]?key|" +
            "public[_-]?key|short[_-]?id|reality[_-]?key|key|headers|" +
            "proxy[_-]?(?:user|password|credential)s?)\\s*[:=]\\s*[^\\s,;]+",
    )

    private val HOST_FIELD = Regex(
        "(?i)\\b(host|hostname|server|serverName|server[_-]?address|server[_-]?name|sni|address)\\s*[:=]\\s*[^\\s,;]+",
    )

    private val JSON_SENSITIVE_FIELD = Regex(
        "(?i)(\"(?:authorization|auth|cookie|credential|token|password|username|uuid|private[_-]?key|" +
            "public[_-]?key|short[_-]?id|reality[_-]?key|key|headers|host|hostname|server|serverName|" +
            "server[_-]?address|server[_-]?name|sni|address)\"\\s*:\\s*\")([^\"]*)(\")",
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

    private fun String.isBareTokenLike(): Boolean {
        val hasDigit = any { it.isDigit() }
        val hasTokenSeparator = any { it == '+' || it == '/' || it == '_' || it == '-' || it == '=' }
        return hasTokenSeparator || hasDigit
    }

    private const val MAX_LENGTH = 2_000
}
