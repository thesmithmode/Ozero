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
            val padded = padBase64(text)
            val decoder = if (urlSafe) Base64.getUrlDecoder() else Base64.getMimeDecoder()
            decoder.decode(padded).toString(Charsets.UTF_8)
        }.getOrNull()

    private fun padBase64(s: String): String {
        val rem = s.length % 4
        return if (rem == 0) s else s + "=".repeat(4 - rem)
    }
}
