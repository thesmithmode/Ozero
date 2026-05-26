package ru.ozero.desktop.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

abstract class SubprocessEngine : DesktopEngine {

    private val processRef = AtomicReference<Process?>(null)
    private val portRef = AtomicInteger(0)

    protected val log: Logger = Logger.getLogger(javaClass.simpleName)

    override val isAvailableOnPlatform: Boolean get() = true

    override fun isRunning(): Boolean = processRef.get()?.isAlive == true

    override fun listeningPort(): Int = portRef.get()

    protected abstract fun buildCommand(config: EngineConfig, binaryPath: String): List<String>

    protected abstract fun extractPort(config: EngineConfig): Int

    protected open fun detectReady(line: String): Boolean = false

    protected open val startupTimeoutMs: Long = 5_000L

    protected open val startupCheckDelayMs: Long = 300L

    override suspend fun start(config: EngineConfig): EngineStartResult = withContext(Dispatchers.IO) {
        stop()

        val binaryPath = findBinary()
            ?: return@withContext EngineStartResult.BinaryMissing(binaryName)

        val port = extractPort(config)
        val command = buildCommand(config, binaryPath)

        log.info("starting: ${command.joinToString(" ")}")

        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return@withContext EngineStartResult.Failed("process start failed: ${e.message}", e)
        }

        processRef.set(process)

        val ready = withTimeoutOrNull(startupTimeoutMs) {
            delay(startupCheckDelayMs)
            if (!process.isAlive) {
                val exit = process.exitValue()
                val stderr = process.inputStream.bufferedReader().readText().take(500)
                return@withTimeoutOrNull "process exited immediately (code=$exit): $stderr"
            }

            val reader = process.inputStream.bufferedReader()
            waitForReady(reader, process)
        }

        if (ready is String) {
            processRef.set(null)
            return@withContext EngineStartResult.Failed(ready)
        }

        if (!process.isAlive) {
            processRef.set(null)
            return@withContext EngineStartResult.Failed("process died during startup")
        }

        portRef.set(port)
        log.info("started on port $port (pid=${process.pid()})")
        EngineStartResult.Success(port)
    }

    private suspend fun waitForReady(reader: BufferedReader, process: Process): Any? {
        while (process.isAlive) {
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                log.fine(line)
                if (detectReady(line)) return null
            } else {
                delay(100)
            }
        }
        return null
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val process = processRef.getAndSet(null) ?: return@withContext
        portRef.set(0)
        log.info("stopping (pid=${process.pid()})")

        process.destroy()
        val exited = withTimeoutOrNull(3_000L) {
            while (process.isAlive) delay(100)
        }
        if (exited == null && process.isAlive) {
            log.warning("force killing (pid=${process.pid()})")
            process.destroyForcibly()
        }
    }

    private fun findBinary(): String? {
        val candidates = listOf(
            File(appBinariesDir(), binaryName),
            File(System.getProperty("user.dir"), binaryName),
            File(System.getProperty("user.dir"), "binaries/$binaryName"),
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    companion object {
        fun appBinariesDir(): File {
            val appDir = System.getProperty("app.dir")
                ?: System.getProperty("compose.application.resources.dir")
                ?: "."
            return File(appDir, "binaries")
        }
    }
}
