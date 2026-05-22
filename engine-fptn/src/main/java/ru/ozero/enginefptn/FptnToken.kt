package ru.ozero.enginefptn

import android.os.Build
import android.util.Base64
import org.json.JSONObject

object FptnToken {

    fun parse(raw: String): FptnTokenData? {
        val (isBrotli, encoded) = when {
            raw.startsWith("fptn:") -> false to raw.removePrefix("fptn:")
            raw.startsWith("fptnb:") -> true to raw.removePrefix("fptnb:")
            else -> return null
        }
        return try {
            val bytes = Base64.decode(encoded.trim(), Base64.DEFAULT)
            val json = if (isBrotli) {
                if (Build.VERSION.SDK_INT < 31) return null
                android.util.BrotliInputStream(bytes.inputStream()).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            parseJson(json)
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
