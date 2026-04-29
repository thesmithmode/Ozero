package ru.ozero.enginetor.bridges

import org.json.JSONObject

object TorBridgesJsonParser {

    fun parse(json: String): List<TorBridge> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("bridges") ?: return emptyList()
        val result = mutableListOf<TorBridge>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val transport = obj.optString("transport").orEmpty()
            val address = obj.optString("address").orEmpty()
            val fingerprint = obj.optString("fingerprint").orEmpty()
            if (transport.isBlank() || address.isBlank() || fingerprint.isBlank()) continue
            val argsObj = obj.optJSONObject("args")
            val args = if (argsObj == null) {
                emptyMap()
            } else {
                buildMap<String, String> {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, argsObj.optString(k))
                    }
                }
            }
            val remark = obj.optString("remark").takeIf { it.isNotBlank() }
            result += runCatching {
                TorBridge(
                    transport = transport,
                    address = address,
                    fingerprint = fingerprint,
                    args = args,
                    remark = remark,
                )
            }.getOrNull() ?: continue
        }
        return result
    }
}
