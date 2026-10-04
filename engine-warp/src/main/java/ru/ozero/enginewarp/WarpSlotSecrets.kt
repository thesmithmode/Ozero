package ru.ozero.enginewarp

import org.json.JSONArray
import org.json.JSONObject
import ru.ozero.commoncrypto.PreferenceAtRest

internal fun sealWarpSlotSecrets(json: String): String? {
    val arr = try {
        JSONArray(json)
    } catch (_: Exception) {
        return null
    }
    var changed = false
    for (index in 0 until arr.length()) {
        val obj = arr.optJSONObject(index) ?: continue
        val rawIniSealed = sealJsonString(obj, "rawIni") ?: return null
        if (rawIniSealed) changed = true
        val config = obj.optJSONObject("config") ?: continue
        val privSealed = sealJsonString(config, "priv") ?: return null
        if (privSealed) changed = true
    }
    return if (changed) arr.toString() else null
}

private fun sealJsonString(obj: JSONObject, key: String): Boolean? {
    if (!obj.has(key) || obj.isNull(key)) return false
    val value = obj.optString(key, "")
    if (value.isEmpty() || PreferenceAtRest.isSealed(value)) return false
    val sealed = PreferenceAtRest.trySeal(value) ?: return null
    obj.put(key, sealed)
    return true
}
