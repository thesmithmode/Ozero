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
                        line.startsWith("vmess://") -> V2RayFmt.parseVMess(line)
                        line.startsWith("trojan://") -> V2RayFmt.parseTrojan(line)
                        line.startsWith("ss://") -> V2RayFmt.parseShadowsocks(line)
                        else -> null
                    }
                }.getOrNull()
            }
}
