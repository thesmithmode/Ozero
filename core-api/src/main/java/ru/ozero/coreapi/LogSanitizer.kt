package ru.ozero.coreapi

import java.net.URI

object LogSanitizer {

    fun redactUrl(raw: String): String =
        runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme ?: return@runCatching "<redacted-uri>"
            val host = uri.host ?: return@runCatching "$scheme://<redacted>"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$host$port/<redacted>"
        }.getOrElse { "<redacted-uri>" }
}
