package ru.ozero.enginefptn

import android.util.Base64
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

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
            readBounded(stream, MAX_DECOMPRESSED_BYTES)
        }

    internal fun readBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes + 1)
        var total = 0
        while (true) {
            val read = stream.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
            if (total > maxBytes) error("decompressed payload exceeds $maxBytes bytes")
        }
        return buffer.copyOf(total)
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
                    countryCode = s.optCountryCode(),
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

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optCountryCode(): String? =
        (optNullableString("country_code") ?: optNullableString("countryCode")).normalizedCountryCode()

    private fun String?.normalizedCountryCode(): String? =
        this?.trim()?.uppercase(Locale.ROOT)?.takeIf { code ->
            code.length == 2 && code.all { it in 'A'..'Z' }
        }
}
