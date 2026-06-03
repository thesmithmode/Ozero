package ru.ozero.singboxfmt

import java.net.URI
import java.net.URLDecoder

internal class UriCompat private constructor(private val raw: String) {

    private val uri: URI? = runCatching { URI(raw) }.getOrNull()
    private val authorityFallback: ParsedAuthority? = runCatching { parseAuthorityFallback() }.getOrNull()
    private val queryParams: Map<String, String> by lazy { parseQuery(uri?.rawQuery ?: rawQueryFallback()) }
    private val parsedPort: Int get() = uri?.port ?: -1
    private val parsedHost: String? get() = uri?.host
    private val parsedUserInfo: String? get() = uri?.userInfo

    val host: String? get() = parsedHost ?: authorityFallback?.host
    val port: Int get() = if (parsedPort > 0) parsedPort else authorityFallback?.port ?: -1
    val userInfo: String? get() = parsedUserInfo ?: authorityFallback?.userInfo
    val fragment: String? get() = uri?.fragment ?: rawFragmentFallback()

    fun getQueryParameter(key: String): String? = queryParams[key]

    companion object {
        fun parse(uriString: String): UriCompat = UriCompat(uriString)

        private fun UriCompat.parseAuthorityFallback(): ParsedAuthority? {
            val schemeEnd = raw.indexOf("://")
            if (schemeEnd < 0) return null
            val queryStart = raw.indexOf('?', startIndex = schemeEnd + 3).let { if (it < 0) Int.MAX_VALUE else it }
            val fragmentStart = raw.indexOf('#', startIndex = schemeEnd + 3).let { if (it < 0) Int.MAX_VALUE else it }
            val authorityEnd = minOf(queryStart, fragmentStart, raw.length)
            if (authorityEnd <= schemeEnd + 3) return null

            val authority = raw.substring(schemeEnd + 3, authorityEnd)
            val atIdx = authority.lastIndexOf('@')
            val userInfo = if (atIdx >= 0) authority.take(atIdx) else null
            var hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority

            if (hostPort.isBlank()) return null

            var host = hostPort
            var port = -1
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close > 0) {
                    host = hostPort.take(close + 1)
                    if (hostPort.length > close + 1 && hostPort[close + 1] == ':') {
                        val candidate = hostPort.substring(close + 2)
                        port = candidate.toIntOrNull() ?: -1
                    }
                }
            } else {
                val lastColon = hostPort.lastIndexOf(':')
                if (lastColon >= 0) {
                    val candidate = hostPort.substring(lastColon + 1)
                    if (candidate.isNotBlank() && candidate.all { it.isDigit() }) {
                        port = candidate.toIntOrNull() ?: -1
                        host = hostPort.substring(0, lastColon)
                    }
                }
            }

            return ParsedAuthority(
                userInfo = userInfo?.takeIf { it.isNotBlank() },
                host = host.takeIf { it.isNotBlank() },
                port = port,
            )
        }

        private fun UriCompat.rawQueryFallback(): String? {
            val queryStart = raw.indexOf('?')
            if (queryStart < 0) return null
            val fragmentStart = raw.indexOf('#', startIndex = queryStart + 1)
            return if (fragmentStart < 0) {
                raw.substring(queryStart + 1)
            } else {
                raw.substring(queryStart + 1, fragmentStart)
            }
        }

        private fun UriCompat.rawFragmentFallback(): String? {
            val fragmentStart = raw.indexOf('#')
            if (fragmentStart < 0) return null
            return raw.substring(fragmentStart + 1)
        }

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

    private data class ParsedAuthority(
        val userInfo: String?,
        val host: String?,
        val port: Int,
    )
}
