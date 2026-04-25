package ru.ozero.security.antidebug

import java.io.File

/**
 * Поставщик содержимого `/proc/self/status` для тестируемости.
 * В тестах подменяется лямбдой, в проде читает реальный файл.
 */
fun interface ProcStatusReader {
    fun read(): String?
}

private val DefaultReader = ProcStatusReader {
    runCatching { File("/proc/self/status").readText() }.getOrNull()
}

/**
 * Anti-debug check по `/proc/self/status` → строка `TracerPid: <N>`. N>0 = debugger attached.
 *
 * Также проверяет `Debug.isDebuggerConnected()` (Java debugger / DDMS) — это hooks
 * только в release сборке: в debug builds мы хотим ADB-debug всегда работать.
 */
class AntiDebugCheck(
    private val reader: ProcStatusReader = DefaultReader,
    private val javaDebuggerAttached: () -> Boolean = { android.os.Debug.isDebuggerConnected() },
    private val isReleaseBuild: () -> Boolean = { !android.os.Build.TYPE.equals("eng") },
) {

    fun isDebuggerAttached(): Boolean {
        if (tracerPidNonZero()) return true
        // Java debugger (DDMS / Android Studio) — проверяем только в release:
        // в debug-сборке это норма.
        if (isReleaseBuild() && javaDebuggerAttached()) return true
        return false
    }

    fun tracerPidNonZero(): Boolean {
        val status = reader.read() ?: return false
        for (line in status.lineSequence()) {
            if (!line.startsWith("TracerPid:")) continue
            val pid = line.substringAfter(':').trim().toIntOrNull() ?: return false
            return pid != 0
        }
        return false
    }
}
