package ru.ozero.desktop.platform

import java.io.File
import java.util.logging.Logger

object PlatformDetector {

    private val log = Logger.getLogger("PlatformDetector")

    fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    fun isMac(): Boolean =
        System.getProperty("os.name").lowercase().contains("mac")

    fun isLinux(): Boolean =
        !isWindows() && !isMac()

    fun isAdmin(): Boolean = when {
        isWindows() -> checkWindowsAdmin()
        else -> System.getProperty("user.name") == "root"
    }

    fun hasWintun(): Boolean {
        if (!isWindows()) return false
        val candidates = listOf(
            File(appBinariesDir(), "wintun.dll"),
            File(System.getProperty("user.dir"), "wintun.dll"),
            File(System.getProperty("user.dir"), "binaries/wintun.dll"),
            File(System.getenv("SYSTEMROOT") ?: "C:\\Windows", "System32/wintun.dll"),
        )
        return candidates.any { it.exists() }
    }

    fun canUseTun(): Boolean = when {
        isWindows() -> isAdmin() && hasWintun()
        isMac() -> isAdmin()
        isLinux() -> isAdmin()
        else -> false
    }

    private fun checkWindowsAdmin(): Boolean = runCatching {
        val p = ProcessBuilder("net", "session")
            .redirectErrorStream(true)
            .start()
        val exitCode = p.waitFor()
        p.inputStream.close()
        exitCode == 0
    }.getOrElse {
        log.warning("admin check failed: ${it.message}")
        false
    }

    private fun appBinariesDir(): File {
        val appDir = System.getProperty("app.dir")
            ?: System.getProperty("compose.application.resources.dir")
            ?: "."
        return File(appDir, "binaries")
    }
}
