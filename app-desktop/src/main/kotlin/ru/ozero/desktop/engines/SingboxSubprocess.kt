package ru.ozero.desktop.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Logger

class SingboxSubprocess {

    private val log = Logger.getLogger("SingboxSubprocess")

    @Volatile
    private var process: Process? = null

    suspend fun startWithConfig(configJson: String): Boolean = withContext(Dispatchers.IO) {
        val binary = resolveSingboxBinary() ?: run {
            log.severe("sing-box binary not found")
            return@withContext false
        }

        val configFile = File(System.getProperty("java.io.tmpdir"), "ozero-singbox.json")
        configFile.writeText(configJson)

        val args = listOf(
            binary.absolutePath,
            "run",
            "-c", configFile.absolutePath,
        )

        log.info("Starting sing-box TUN: ${args.joinToString(" ")}")
        val pb = ProcessBuilder(args)
            .redirectErrorStream(true)

        process = pb.start()

        kotlinx.coroutines.delay(STARTUP_DELAY_MS)

        if (process?.isAlive != true) {
            log.severe("sing-box exited immediately")
            return@withContext false
        }

        log.info("sing-box TUN started successfully")
        true
    }

    fun stop() {
        process?.let { p ->
            log.info("Stopping sing-box (pid=${p.pid()})")
            p.destroy()
            if (p.isAlive) p.destroyForcibly()
        }
        process = null
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun resolveSingboxBinary(): File? {
        val binDir = File(System.getProperty("app.dir", "."), "binaries")
        val candidates = listOf(
            File(binDir, "sing-box.exe"),
            File(binDir, "sing-box"),
            File(".", "sing-box.exe"),
            File(".", "sing-box"),
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
    }

    companion object {
        private const val STARTUP_DELAY_MS = 1_500L
    }
}
