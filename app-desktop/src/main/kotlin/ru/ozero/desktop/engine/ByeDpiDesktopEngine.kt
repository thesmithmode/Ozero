package ru.ozero.desktop.engine

import ru.ozero.desktop.model.EngineId

class ByeDpiDesktopEngine : SubprocessEngine() {

    override val id = EngineId.BYEDPI
    override val binaryName = if (isWindows()) "byedpi.exe" else "ciadpi"
    override val startupTimeoutMs = 3_000L
    override val startupCheckDelayMs = 500L

    override fun extractPort(config: EngineConfig): Int =
        if (config.socksPort > 0) config.socksPort else DEFAULT_SOCKS_PORT

    override fun buildCommand(config: EngineConfig, binaryPath: String): List<String> {
        val port = extractPort(config)
        val base = mutableListOf(
            binaryPath,
            "--ip", "127.0.0.1",
            "--port", port.toString(),
        )
        if (config.extraArgs.isNotEmpty()) {
            base.addAll(config.extraArgs)
        }
        return base
    }

    override fun detectReady(line: String): Boolean =
        line.contains("listen") || line.contains("started")

    companion object {
        const val DEFAULT_SOCKS_PORT = 1080

        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")
    }
}
