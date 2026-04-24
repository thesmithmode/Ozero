package ru.ozero.coresubscriptions.uri

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun parseQuery(raw: String): Map<String, String> =
    raw.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx == -1) return@mapNotNull null
        val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
        val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
        key to value
    }.toMap()

internal fun decodeFragment(raw: String?): String? =
    raw?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }?.takeIf { it.isNotEmpty() }
