package ru.ozero.desktop.engine

import ru.ozero.desktop.model.EngineId
import java.io.File

class WarpDesktopEngine : SubprocessEngine() {

    override val id = EngineId.WARP
    override val binaryName = if (isWindows()) "amneziawg.exe" else "amneziawg"
    override val startupTimeoutMs = 10_000L

    private var configFile: File? = null

    override fun extractPort(config: EngineConfig): Int =
        if (config.socksPort > 0) config.socksPort else DEFAULT_SOCKS_PORT

    override fun buildCommand(config: EngineConfig, binaryPath: String): List<String> {
        val wgConfig = config.warpConfig ?: return emptyList()

        val tempFile = File.createTempFile("warp-", ".conf")
        tempFile.writeText(wgConfig)
        tempFile.deleteOnExit()
        configFile = tempFile

        return listOf(binaryPath, "-f", tempFile.absolutePath)
    }

    override fun detectReady(line: String): Boolean =
        line.contains("UAPI listener") || line.contains("device started")

    override suspend fun stop() {
        super.stop()
        configFile?.delete()
        configFile = null
    }

    companion object {
        const val DEFAULT_SOCKS_PORT = 7891

        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")
    }
}
