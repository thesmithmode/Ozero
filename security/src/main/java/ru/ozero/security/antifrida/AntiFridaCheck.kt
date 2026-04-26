package ru.ozero.security.antifrida

import java.io.File

/** Поставщик `/proc/self/maps` для тестируемости. */
fun interface ProcMapsReader {
    fun read(): String?
}

private val DefaultReader = ProcMapsReader {
    // Стримим вместо readText() — /proc/self/maps больших процессов несколько МБ,
    // полная загрузка в память не нужна для substring-поиска.
    runCatching {
        File("/proc/self/maps").bufferedReader().use { it.readText() }
    }.getOrNull()
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
        // Ранний выход на первом совпадении — большие maps больше не сканируются полностью.
        for (line in maps.lineSequence()) {
            for (sig in SIGNATURES) {
                if (line.contains(sig, ignoreCase = true)) return true
            }
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
