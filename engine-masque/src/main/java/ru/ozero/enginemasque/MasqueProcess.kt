package ru.ozero.enginemasque

import android.os.Build
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

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
            .apply {
                environment()["OZERO_MASQUE_MANAGED"] = "1"
                environment()["OZERO_MASQUE_READY_FILE"] = File(config.parentFile, "ready").absolutePath
                environment().keys.removeAll { it.uppercase(java.util.Locale.ROOT) in PROXY_VARIABLES }
            }
            .start()
        discardOutput(process)
        return ManagedMasqueProcess(process)
    }

    private val PROXY_VARIABLES = setOf("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY")

    private fun discardOutput(process: Process) {
        thread(isDaemon = true, name = "masque-output") { drainMasqueOutput(process) }
    }
}

internal class ManagedMasqueProcess(
    private val process: Process,
    private val forceStop: (Process) -> Unit = { forceStopProcess(it, Build.VERSION.SDK_INT) },
    private val stopWaitMs: Long = 1_000,
) : MasqueProcess {
    override val alive: Boolean
        get() = try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }

    override fun exitCode(): Int = process.exitValue()

    override fun close() {
        try {
            process.outputStream.close()
        } finally {
            process.destroy()
            if (!waitForExit()) {
                forceStop(process)
                check(waitForExit()) { "MASQUE process did not stop" }
            }
        }
    }

    private fun waitForExit(): Boolean {
        val deadline = System.nanoTime() + stopWaitMs * 1_000_000
        while (alive && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        return !alive
    }
}

internal fun drainMasqueOutput(process: Process) {
    try {
        process.inputStream.use { stream ->
            val buffer = ByteArray(8192)
            while (stream.read(buffer) != -1) Unit
        }
    } catch (_: IOException) {
        Unit
    }
}

internal fun forceStopProcess(process: Process, sdkInt: Int) {
    if (sdkInt >= Build.VERSION_CODES.O) {
        process.destroyForcibly()
    } else {
        process.destroy()
    }
}
