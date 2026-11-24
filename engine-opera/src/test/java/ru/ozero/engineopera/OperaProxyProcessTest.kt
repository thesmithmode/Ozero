package ru.ozero.engineopera

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OperaProxyProcessTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun `missing executable is rejected`() {
        val launcher = SubprocessOperaProxyLauncher(File(temp, "missing"), File(temp, "work"))
        assertThrows(IllegalArgumentException::class.java) {
            launcher.launch(OperaProxyConfig(), 18080)
        }
    }

    @Test
    fun `working directory cannot supply implicit upstream configuration`() {
        val binary = executable()
        val work = File(temp, "work").apply { mkdir() }
        File(work, "proxy-bypass.txt").writeText("*")
        val launcher = SubprocessOperaProxyLauncher(binary, work)
        assertThrows(IllegalStateException::class.java) {
            launcher.launch(OperaProxyConfig(), 18080)
        }
    }

    @Test
    fun `launch and repeated stop terminate subprocess`() {
        val launcher = SubprocessOperaProxyLauncher(executable(), File(temp, "work"))
        val process = launcher.launch(OperaProxyConfig(), 18080)
        try {
            assertTrue(process.alive)
        } finally {
            process.stop()
        }
        assertFalse(process.alive)
        process.stop()
    }

    private fun executable(): File = File(temp, "opera").apply {
        writeText("#!/bin/sh\nexec sleep 60\n")
        check(setExecutable(true))
    }
}
