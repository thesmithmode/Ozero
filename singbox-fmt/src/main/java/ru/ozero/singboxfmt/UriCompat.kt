package ru.ozero.singboxfmt

import java.net.URI
import java.net.URLDecoder

internal class UriCompat private constructor(private val raw: String) {

    private val uri: URI? = runCatching { URI(raw) }.getOrNull()
    private val queryParams: Map<String, String> by lazy { parseQuery(uri?.rawQuery) }

    val host: String? get() = uri?.host
    val port: Int get() = uri?.port ?: -1
    val userInfo: String? get() = uri?.userInfo
    val fragment: String? get() = uri?.fragment

    fun getQueryParameter(key: String): String? = queryParams[key]

    companion object {
        fun parse(uriString: String): UriCompat = UriCompat(uriString)

        private fun parseQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrEmpty()) return emptyMap()
            val result = LinkedHashMap<String, String>()
            for (pair in rawQuery.split('&')) {
                val eqIdx = pair.indexOf('=')
                if (eqIdx < 0) {
                    result[decode(pair)] = ""
                } else {
                    result[decode(pair.substring(0, eqIdx))] = decode(pair.substring(eqIdx + 1))
                }
            }
            return result
        }

        private fun decode(s: String): String =
            runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}
