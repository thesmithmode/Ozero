package ru.ozero.singboxsubscription.parser

import android.util.Base64
import ru.ozero.singboxfmt.AbstractBean

object Base64BundleParser {
    fun parse(encoded: String): List<AbstractBean> {
        val cleaned = encoded.trim()
        val decoded = tryDecode(cleaned, Base64.DEFAULT)
            ?: tryDecode(cleaned, Base64.URL_SAFE)
            ?: return emptyList()
        return RawShareLinksParser.parse(decoded)
    }

    private fun tryDecode(text: String, flags: Int): String? =
        runCatching {
            Base64.decode(text, flags).toString(Charsets.UTF_8)
        }.getOrNull()
}
