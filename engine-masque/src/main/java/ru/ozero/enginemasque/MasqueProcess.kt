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
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(File("/dev/null"))
            .start()
        try {
            process.outputStream.close()
            return object : MasqueProcess {
                override val alive: Boolean get() = process.isAlive

                override fun exitCode(): Int = process.exitValue()

                override fun close() {
                    process.destroy()
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        check(process.waitFor(1, TimeUnit.SECONDS)) { "MASQUE process did not stop" }
                    }
                }
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }
    }
}
