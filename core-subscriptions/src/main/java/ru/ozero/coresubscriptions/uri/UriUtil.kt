package ru.ozero.coresubscriptions.uri

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun parseQuery(raw: String): Map<String, String> =
    raw.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx == -1) return@mapNotNull null
        val keyRaw = pair.substring(0, idx)
        val valueRaw = pair.substring(idx + 1)
        val key = runCatching { URLDecoder.decode(keyRaw, StandardCharsets.UTF_8) }.getOrElse { keyRaw }
        val value = runCatching { URLDecoder.decode(valueRaw, StandardCharsets.UTF_8) }.getOrElse { valueRaw }
        key to value
    }.toMap()

internal fun decodeFragment(raw: String?): String? {
    if (raw == null) return null
    val decoded = runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8) }.getOrElse { raw }
    return decoded.takeIf { it.isNotEmpty() }
}
