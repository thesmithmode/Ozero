package ru.ozero.security.antifrida

import java.io.File

/**
 * Поставщик `/proc/self/maps` для тестируемости.
 * Callback-форма гарантирует true-streaming: line-by-line без материализации
 * всего содержимого в память (maps больших процессов — несколько МБ).
 */
fun interface ProcMapsReader {
    /** Передаёт ленивый Sequence<String> в block; ресурс закрывается после возврата. */
    fun <R> useLines(block: (Sequence<String>) -> R): R
}

private val DefaultReader = ProcMapsReader { block ->
    runCatching { File("/proc/self/maps").useLines(block) }.getOrElse { block(emptySequence()) }
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

    fun isHookFrameworkPresent(): Boolean = reader.useLines { lines ->
        for (line in lines) {
            for (sig in SIGNATURES) {
                if (line.contains(sig, ignoreCase = true)) return@useLines true
            }
        }
        false
    }

    fun firstSignatureMatch(): String? = reader.useLines { lines ->
        for (line in lines) {
            for (sig in SIGNATURES) {
                if (line.contains(sig, ignoreCase = true)) return@useLines sig
            }
        }
        null
    }

    private companion object {
        // "magisk" намеренно исключён: легитимные пользователи на Magisk-rooted девайсах
        // получали false-positive. Root-detection — отдельная политика, не часть Frida-чека.
        val SIGNATURES = listOf(
            "frida-agent",
            "frida-gadget",
            "gum-js-loop",
            "linjector",
            "re.frida.server",
            "XposedBridge",
            "LSPosed",
            "EdXposed",
            "substrate",
        )
    }
}
