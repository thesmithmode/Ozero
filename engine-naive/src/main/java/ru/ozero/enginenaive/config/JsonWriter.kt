package ru.ozero.enginenaive.config

/** Мини JSON-сериализатор без зависимостей. Дублирует engine-xray/engine-hysteria2 для изоляции. */
internal object JsonWriter {

    fun write(value: Any?): String {
        val sb = StringBuilder()
        append(sb, value)
        return sb.toString()
    }

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is Map<*, *> -> appendObject(sb, value)
            is List<*> -> appendArray(sb, value)
            is String -> appendString(sb, value)
            is Boolean -> sb.append(value.toString())
            is Number -> sb.append(value.toString())
            else -> error("Неподдерживаемый тип для JSON: ${value::class.java}")
        }
    }

    private fun appendObject(sb: StringBuilder, map: Map<*, *>) {
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            appendString(sb, k.toString())
            sb.append(':')
            append(sb, v)
        }
        sb.append('}')
    }

    private fun appendArray(sb: StringBuilder, list: List<*>) {
        sb.append('[')
        list.forEachIndexed { i, v ->
            if (i > 0) sb.append(',')
            append(sb, v)
        }
        sb.append(']')
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}
