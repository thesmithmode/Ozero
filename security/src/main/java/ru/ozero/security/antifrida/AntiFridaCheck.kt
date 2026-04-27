package ru.ozero.security.antifrida

import java.io.File

interface ProcMapsReader {
    fun <R> useLines(block: (Sequence<String>) -> R): R
}

private val DefaultReader = object : ProcMapsReader {
    override fun <R> useLines(block: (Sequence<String>) -> R): R =
        runCatching {
            File("/proc/self/maps").useLines { lines -> block(lines) }
        }.getOrElse { block(emptySequence()) }
}

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
