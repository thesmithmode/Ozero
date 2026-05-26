package ru.ozero.desktop.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.logging.Logger

abstract class SubprocessEngine(
    private val binaryName: String,
) {
    private val log = Logger.getLogger(javaClass.simpleName)

    @Volatile
    protected var process: Process? = null

    protected abstract fun buildArgs(socksPort: Int): List<String>

    suspend fun start(socksPort: Int): Boolean = withContext(Dispatchers.IO) {
        val binaryPath = resolveBinary() ?: run {
            log.severe("$binaryName not found in binaries directory")
            return@withContext false
        }

        val args = listOf(binaryPath.absolutePath) + buildArgs(socksPort)
        log.info("Starting: ${args.joinToString(" ")}")

        val pb = ProcessBuilder(args)
            .redirectErrorStream(true)
            .directory(binaryPath.parentFile)

        process = pb.start()

        val ready = waitForSocksPort(socksPort, timeoutMs = 10_000L)
        if (!ready) {
            log.warning("$binaryName failed to bind SOCKS5 port $socksPort within timeout")
            stop()
        }
        ready
    }

    fun stop() {
        process?.let { p ->
            log.info("Stopping $binaryName (pid=${p.pid()})")
            p.destroy()
            if (p.isAlive) p.destroyForcibly()
        }
        process = null
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun resolveBinary(): File? {
        val binDir = File(System.getProperty("app.dir", "."), "binaries")
        val candidates = listOf(
            File(binDir, binaryName),
            File(binDir, "$binaryName.exe"),
            File(".", binaryName),
            File(".", "$binaryName.exe"),
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
    }

    private suspend fun waitForSocksPort(port: Int, timeoutMs: Long): Boolean {
        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (isTcpPortOpen(port)) return@withTimeoutOrNull true
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        return result == true
    }

    private fun isTcpPortOpen(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 200)
            true
        }
    }.getOrDefault(false)

    companion object {
        private const val POLL_INTERVAL_MS = 200L
    }
}
