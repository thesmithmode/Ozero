package ru.ozero.singboxsubscription.parser

import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.V2RayFmt

object RawShareLinksParser {
    fun parse(text: String): List<AbstractBean> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { it.split(" ") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                runCatching {
                    when {
                        token.startsWith("vless://") -> V2RayFmt.parseVLESS(token)
                        token.startsWith("vmess://") -> V2RayFmt.parseVMess(token)
                        token.startsWith("trojan://") -> V2RayFmt.parseTrojan(token)
                        token.startsWith("ss://") -> V2RayFmt.parseShadowsocks(token)
                        else -> null
                    }
                }.getOrNull()
            }
}
