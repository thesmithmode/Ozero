package ru.ozero.enginemasque

import org.json.JSONException
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress

class MasqueProfile private constructor(internal val json: String) {
    override fun toString(): String = "MasqueProfile(***)"

    fun exportJson(): String = json

    internal fun hasIpv6Endpoint(transport: MasqueTransport): Boolean {
        val key = if (transport == MasqueTransport.H2) "endpoint_h2_v6" else "endpoint_v6"
        return JSONObject(json).optString(key).isNotBlank()
    }

    companion object {
        const val MAX_BYTES = 64 * 1024

        fun parse(json: String): MasqueProfile {
            require(json.toByteArray().size in 1..MAX_BYTES) { "Invalid MASQUE profile size" }
            val fields = try {
                JSONObject(json)
            } catch (_: JSONException) {
                throw IllegalArgumentException("Invalid MASQUE profile JSON")
            }
            require(REQUIRED.all { fields.opt(it) is String && fields.optString(it).isNotBlank() }) {
                "Incomplete MASQUE profile"
            }
            require(fields.keys().asSequence().all { it in REQUIRED || it in OPTIONAL }) {
                "Unknown MASQUE profile fields"
            }
            require(OPTIONAL.all { !fields.has(it) || fields.opt(it) is String }) {
                "Invalid MASQUE endpoint type"
            }
            validateAddresses(fields)
            return MasqueProfile(json)
        }

        private fun validateAddresses(fields: JSONObject) {
            require(isIpv4(fields.getString("endpoint_v4")) && isIpv4(fields.getString("ipv4"))) {
                "Invalid MASQUE IPv4 address"
            }
            require(isIpv6(fields.getString("ipv6"))) { "Invalid MASQUE IPv6 address" }
            val h2v4 = fields.optString("endpoint_h2_v4")
            require(h2v4.isEmpty() || isIpv4(h2v4)) { "Invalid MASQUE HTTP/2 endpoint" }
            for (key in listOf("endpoint_v6", "endpoint_h2_v6")) {
                val address = fields.optString(key)
                require(address.isEmpty() || isIpv6(address)) { "Invalid MASQUE IPv6 endpoint" }
            }
        }

        private fun isIpv4(address: String): Boolean {
            val parts = address.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                part.toIntOrNull()?.let { it in 0..255 && it.toString() == part } == true
            }
        }

        private fun isIpv6(address: String): Boolean {
            if (!address.contains(':') || !IPV6_CHARS.matches(address)) return false
            return try {
                InetAddress.getByName(address) is Inet6Address
            } catch (_: java.net.UnknownHostException) {
                false
            }
        }

        private val REQUIRED = setOf(
            "private_key", "endpoint_pub_key", "endpoint_v4", "ipv4", "ipv6", "id", "access_token",
        )
        private val OPTIONAL = setOf("endpoint_v6", "endpoint_h2_v4", "endpoint_h2_v6")
        private val IPV6_CHARS = Regex("[0-9a-fA-F:.]+")
    }
}
