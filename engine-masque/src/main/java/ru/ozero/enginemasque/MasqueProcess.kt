package ru.ozero.enginemasque

import java.io.File
import java.util.concurrent.TimeUnit

internal interface MasqueProcess {
    val alive: Boolean
    fun exitCode(): Int
    fun close()
}

internal fun interface MasqueProcessLauncher {
    fun launch(command: List<String>): MasqueProcess
}

internal object NativeMasqueProcessLauncher : MasqueProcessLauncher {
    override fun launch(command: List<String>): MasqueProcess {
        val config = File(command[2])
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(File("/dev/null"))
            .apply {
                environment()["OZERO_MASQUE_MANAGED"] = "1"
                environment()["OZERO_MASQUE_READY_FILE"] = File(config.parentFile, "ready").absolutePath
            }
            .start()
        return ManagedMasqueProcess(process)
    }
}

internal class ManagedMasqueProcess(private val process: Process) : MasqueProcess {
    override val alive: Boolean get() = process.isAlive

    override fun exitCode(): Int = process.exitValue()

    override fun close() {
        try {
            process.outputStream.close()
        } finally {
            process.destroy()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                check(process.waitFor(1, TimeUnit.SECONDS)) { "MASQUE process did not stop" }
            }
        }
    }
}
