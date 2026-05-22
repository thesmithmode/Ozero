package ru.ozero.enginefptn

import android.util.Base64
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream

object FptnToken {

    fun parse(raw: String): FptnTokenData? {
        val (encoded, brotliCompressed) = when {
            raw.startsWith("fptn:") -> raw.removePrefix("fptn:") to false
            raw.startsWith("fptnb:") -> raw.removePrefix("fptnb:") to true
            else -> return null
        }
        return try {
            val base64Bytes = Base64.decode(encoded.trim(), Base64.DEFAULT)
            val jsonBytes = if (brotliCompressed) brotliDecompress(base64Bytes) else base64Bytes
            parseJson(jsonBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private const val MAX_DECOMPRESSED_BYTES = 1 * 1024 * 1024

    private fun brotliDecompress(bytes: ByteArray): ByteArray =
        BrotliInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val buffer = ByteArray(MAX_DECOMPRESSED_BYTES + 1)
            var total = 0
            while (true) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
                if (total > MAX_DECOMPRESSED_BYTES) {
                    error("brotli decompressed payload exceeds $MAX_DECOMPRESSED_BYTES bytes")
                }
            }
            buffer.copyOf(total)
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
