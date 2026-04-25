package ru.ozero.security.antifrida

import java.io.File

/** Поставщик `/proc/self/maps` для тестируемости. */
fun interface ProcMapsReader {
    fun read(): String?
}

private val DefaultReader = ProcMapsReader {
    runCatching { File("/proc/self/maps").readText() }.getOrNull()
}

/**
 * Детектит Frida / Xposed / Substrate hooks по `/proc/self/maps` —
 * frida-server и frida-gadget оставляют узнаваемые maps:
 *  - `frida-agent-<arch>.so`
 *  - `frida-gadget`
 *  - `gum-js-loop`
 *  - `gmain` (frida script thread)
 *  - `linjector`
 *  - `XposedBridge.jar`
 *  - `LSPosed`
 *  - `re.frida.server`
 */
class AntiFridaCheck(
    private val reader: ProcMapsReader = DefaultReader,
) {

    fun isHookFrameworkPresent(): Boolean {
        val maps = reader.read() ?: return false
        for (sig in SIGNATURES) {
            if (maps.contains(sig, ignoreCase = true)) return true
        }
        return false
    }

    fun firstSignatureMatch(): String? {
        val maps = reader.read() ?: return null
        return SIGNATURES.firstOrNull { maps.contains(it, ignoreCase = true) }
    }

    private companion object {
        val SIGNATURES = listOf(
            "frida-agent",
            "frida-gadget",
            "gum-js-loop",
            "linjector",
            "re.frida.server",
            "XposedBridge",
            "LSPosed",
            "EdXposed",
            "magisk",
            "substrate",
        )
    }
}
