package ru.ozero.singboxsubscription.parser

import ru.ozero.singboxfmt.AbstractBean
import java.util.Base64

object Base64BundleParser {
    fun parse(encoded: String): List<AbstractBean> {
        val cleaned = encoded.trim()
        val decoded = tryDecode(cleaned, urlSafe = false)
            ?: tryDecode(cleaned, urlSafe = true)
            ?: return emptyList()
        return RawShareLinksParser.parse(decoded)
    }

    private fun tryDecode(text: String, urlSafe: Boolean): String? =
        runCatching {
            val decoder = if (urlSafe) Base64.getUrlDecoder() else Base64.getDecoder()
            decoder.decode(text).toString(Charsets.UTF_8)
        }.getOrNull()
}
