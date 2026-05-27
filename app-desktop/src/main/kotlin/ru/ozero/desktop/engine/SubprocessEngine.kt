package ru.ozero.desktop.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.desktop.logging.DesktopLogLevel
import ru.ozero.desktop.logging.DesktopLogStore
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import kotlin.concurrent.thread

abstract class SubprocessEngine : DesktopEngine {

    private val processRef = AtomicReference<Process?>(null)
    private val portRef = AtomicInteger(0)

    protected val log: Logger = Logger.getLogger(javaClass.simpleName)

    override val isAvailableOnPlatform: Boolean get() = true

    override fun isRunning(): Boolean = processRef.get()?.isAlive == true

    override fun listeningPort(): Int = portRef.get()

    internal abstract fun buildCommand(config: EngineConfig, binaryPath: String): List<String>

    internal abstract fun extractPort(config: EngineConfig): Int

    internal open fun detectReady(line: String): Boolean = false

    protected open val startupTimeoutMs: Long = 5_000L

    protected open val startupCheckDelayMs: Long = 300L

    override suspend fun start(config: EngineConfig): EngineStartResult = withContext(Dispatchers.IO) {
        stop()

        val binaryPath = findBinary()
            ?: return@withContext EngineStartResult.BinaryMissing(binaryName)

        val port = extractPort(config)
        val command = buildCommand(config, binaryPath)
        if (command.isEmpty()) {
            log.warning("engine '$binaryName' has empty start command")
            return@withContext EngineStartResult.Failed("no start args for $binaryName")
        }

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
        drainStdout(process)
        EngineStartResult.Success(port)
    }

    private suspend fun waitForReady(reader: BufferedReader, process: Process): Any? {
        val tag = javaClass.simpleName
        while (process.isAlive) {
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                log.fine(line)
                val level = when {
                    line.contains("error", ignoreCase = true) -> DesktopLogLevel.ERROR
                    line.contains("warn", ignoreCase = true) -> DesktopLogLevel.WARN
                    else -> DesktopLogLevel.INFO
                }
                DesktopLogStore.append(level, tag, line)
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

    private fun drainStdout(process: Process) {
        val tag = javaClass.simpleName
        thread(isDaemon = true, name = "$tag-stdout") {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val level = when {
                        line.contains("error", ignoreCase = true) -> DesktopLogLevel.ERROR
                        line.contains("warn", ignoreCase = true) -> DesktopLogLevel.WARN
                        else -> DesktopLogLevel.DEBUG
                    }
                    DesktopLogStore.append(level, tag, line)
                }
            }
        }
    }

    private fun findBinary(): String? {
        val candidates = binaryCandidates(binaryName)
        val found = candidates.firstOrNull { it.exists() && it.canExecute() }
        if (found == null) {
            log.warning("binary '$binaryName' not found. Checked: ${candidates.map { it.absolutePath }}")
        } else {
            log.info("resolved '$binaryName' → ${found.absolutePath}")
        }
        return found?.absolutePath
    }

    companion object {
        fun appBinariesDir(): File {
            val appDir = System.getProperty("app.dir")
                ?: System.getProperty("compose.application.resources.dir")
                ?: "."
            return File(appDir, "binaries")
        }

        private fun appResourcesDir(): String =
            System.getProperty("compose.application.resources.dir")
                ?: System.getProperty("app.dir")
                ?: "."

        fun binaryCandidates(name: String): List<File> = listOf(
            File(appBinariesDir(), name),
            File(appResourcesDir(), name),
            File(File(appResourcesDir()).parentFile ?: File("."), name),
            File(System.getProperty("user.dir"), name),
            File(System.getProperty("user.dir"), "binaries/$name"),
        )
    }
}
