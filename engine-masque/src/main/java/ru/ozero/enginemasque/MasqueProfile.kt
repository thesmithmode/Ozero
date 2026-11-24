package ru.ozero.enginemasque

import org.json.JSONObject

class MasqueProfile private constructor(internal val json: String) {
    override fun toString(): String = "MasqueProfile(***)"

    fun exportJson(): String = json

    companion object {
        const val MAX_BYTES = 64 * 1024

        fun parse(json: String): MasqueProfile {
            require(json.toByteArray().size in 1..MAX_BYTES) { "Invalid MASQUE profile size" }
            val fields = try {
                JSONObject(json)
            } catch (_: org.json.JSONException) {
                throw IllegalArgumentException("Invalid MASQUE profile JSON")
            }
            val required = listOf(
                "private_key",
                "endpoint_pub_key",
                "endpoint_v4",
                "ipv4",
                "ipv6",
                "id",
                "access_token",
            )
            require(required.all { fields.opt(it) is String && fields.optString(it).isNotBlank() }) {
                "Incomplete MASQUE profile"
            }
            return MasqueProfile(json)
        }
    }
}
