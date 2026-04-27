package ru.ozero.security.antidebug

import java.io.File

fun interface ProcStatusReader {
    fun read(): String?
}

private val DefaultReader = ProcStatusReader {
    runCatching { File("/proc/self/status").readText() }.getOrNull()
}

class AntiDebugCheck(
    private val reader: ProcStatusReader = DefaultReader,
    private val javaDebuggerAttached: () -> Boolean = { android.os.Debug.isDebuggerConnected() },
    private val isReleaseBuild: () -> Boolean = { android.os.Build.TYPE == "user" },
) {

    fun isDebuggerAttached(): Boolean {
        if (tracerPidNonZero()) return true
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
