package ru.ozero.corebackup

import org.json.JSONObject

internal fun JSONObject.intOrNull(key: String): Int? = if (has(key)) getInt(key) else null

internal fun JSONObject.floatOrNull(key: String): Float? =
    if (has(key)) getDouble(key).toFloat() else null

internal fun JSONObject.booleanOrNull(key: String): Boolean? = if (has(key)) {
    getBoolean(key)
} else {
    null
}
