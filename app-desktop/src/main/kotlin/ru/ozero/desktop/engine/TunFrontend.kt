package ru.ozero.desktop.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.desktop.platform.PlatformDetector
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

class TunFrontend {

    private val log = Logger.getLogger("TunFrontend")
    private val processRef = AtomicReference<Process?>(null)
    private var configFile: File? = null

    fun isRunning(): Boolean = processRef.get()?.isAlive == true

    suspend fun start(socksPort: Int): Boolean = withContext(Dispatchers.IO) {
        stop()

        val binaryName = if (PlatformDetector.isWindows()) "sing-box.exe" else "sing-box"
        val binaryPath = findBinary(binaryName)
        if (binaryPath == null) {
            log.warning("sing-box binary not found for TUN frontend")
            return@withContext false
        }

        val json = SingboxDesktopEngine.buildTunForwardConfig(socksPort)
        val tempFile = File.createTempFile("tun-frontend-", ".json")
        tempFile.writeText(json)
        tempFile.deleteOnExit()
        configFile = tempFile

        val command = listOf(binaryPath, "run", "-c", tempFile.absolutePath)
        log.info("starting TUN frontend: ${command.joinToString(" ")}")

        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            log.warning("TUN frontend start failed: ${e.message}")
            tempFile.delete()
            configFile = null
            return@withContext false
        }

        processRef.set(process)

        val ready = withTimeoutOrNull(STARTUP_TIMEOUT_MS) {
            delay(300)
            if (!process.isAlive) return@withTimeoutOrNull false

            val reader = process.inputStream.bufferedReader()
            while (process.isAlive) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    log.fine(line)
                    if (line.contains("started") || line.contains("tun-in")) {
                        return@withTimeoutOrNull true
                    }
                } else {
                    delay(100)
                }
            }
            false
        } ?: false

        if (!ready || !process.isAlive) {
            log.warning("TUN frontend failed to start")
            process.destroyForcibly()
            processRef.set(null)
            tempFile.delete()
            configFile = null
            return@withContext false
        }

        log.info("TUN frontend started (pid=${process.pid()})")
        true
    }

    suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val process = processRef.getAndSet(null) ?: return@withContext
        log.info("stopping TUN frontend (pid=${process.pid()})")

        process.destroy()
        val exited = withTimeoutOrNull(3_000L) {
            while (process.isAlive) delay(100)
        }
        if (exited == null && process.isAlive) {
            log.warning("force killing TUN frontend")
            process.destroyForcibly()
        }

        configFile?.delete()
        configFile = null
    }

    private fun findBinary(name: String): String? {
        val candidates = listOf(
            File(SubprocessEngine.appBinariesDir(), name),
            File(System.getProperty("user.dir"), name),
            File(System.getProperty("user.dir"), "binaries/$name"),
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    companion object {
        private const val STARTUP_TIMEOUT_MS = 10_000L
    }
}
