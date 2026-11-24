package ru.ozero.enginemasque

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MasqueProcessTest {
    @Test
    fun `native launcher sets managed lifetime and marker path`(@TempDir directory: File) = runBlocking {
        val script = File(directory, "fake-usque").apply {
            writeText(
                """#!/bin/sh
                |test "${'$'}OZERO_MASQUE_MANAGED" = "1" || exit 1
                |test "${'$'}OZERO_MASQUE_READY_FILE" = "${'$'}(dirname "${'$'}2")/ready" || exit 2
                |exit 0
                """.trimMargin(),
            )
            check(setExecutable(true))
        }
        val child = NativeMasqueProcessLauncher.launch(
            listOf(script.absolutePath, "--config", File(directory, "config.json").absolutePath),
        )
        try {
            withTimeout(5_000) { while (child.alive) delay(10) }
            assertFalse(child.alive)
            assertEquals(0, child.exitCode())
        } finally {
            child.close()
        }
    }

    @Test
    fun `stubborn child is forcibly terminated or stop fails`() {
        val process = mockk<Process>(relaxed = true)
        var exited = false
        every { process.exitValue() } answers { if (exited) 0 else throw IllegalThreadStateException() }
        val child = ManagedMasqueProcess(process, forceStop = { exited = true }, stopWaitMs = 0)
        child.close()
        assertFalse(child.alive)
        verify { process.destroy() }
        exited = false
        assertThrows<IllegalStateException> {
            ManagedMasqueProcess(process, forceStop = {}, stopWaitMs = 0).close()
        }
    }
}
