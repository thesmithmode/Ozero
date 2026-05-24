package ru.ozero.singboxsubscription.parser

import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.V2RayFmt

object RawShareLinksParser {
    fun parse(text: String): List<AbstractBean> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                runCatching {
                    when {
                        line.startsWith("vless://") -> V2RayFmt.parseVLESS(line)
                        else -> null
                    }
                }.getOrNull()
            }
}
