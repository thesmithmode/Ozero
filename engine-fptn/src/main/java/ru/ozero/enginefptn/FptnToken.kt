package ru.ozero.enginefptn

import android.util.Base64
import org.json.JSONObject

object FptnToken {

    fun parse(raw: String): FptnTokenData? {
        val encoded = when {
            raw.startsWith("fptn:") -> raw.removePrefix("fptn:")
            raw.startsWith("fptnb:") -> return null
            else -> return null
        }
        return try {
            val bytes = Base64.decode(encoded.trim(), Base64.DEFAULT)
            parseJson(bytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJson(json: String): FptnTokenData? {
        return try {
            val obj = JSONObject(json)
            val serversArr = obj.optJSONArray("servers") ?: return null
            val servers = (0 until serversArr.length()).map { i ->
                val s = serversArr.getJSONObject(i)
                FptnServer(
                    name = s.getString("name"),
                    host = s.getString("host"),
                    port = s.getInt("port"),
                    md5Fingerprint = s.optString("md5_fingerprint", ""),
                    countryCode = s.optString("country_code", "??").uppercase(),
                )
            }
            if (servers.isEmpty()) return null
            FptnTokenData(
                version = obj.optInt("version", 1),
                username = obj.getString("username"),
                password = obj.getString("password"),
                servers = servers,
            )
        } catch (_: Exception) {
            null
        }
    }
}
