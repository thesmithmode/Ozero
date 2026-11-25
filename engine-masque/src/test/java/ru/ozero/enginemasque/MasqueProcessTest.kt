package ru.ozero.enginemasque

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

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
    fun `output is drained even when the process writes more than a pipe buffer`() {
        val stream = ByteArrayInputStream(ByteArray(128 * 1024))
        val process = mockk<Process>(relaxed = true)
        every { process.inputStream } returns stream
        drainMasqueOutput(process)
        assertEquals(0, stream.available())
    }

    @Test
    fun `closing the output pipe during process death is harmless`() {
        val stream = mockk<InputStream>(relaxed = true)
        every { stream.read(any<ByteArray>()) } throws IOException("pipe closed")
        val process = mockk<Process>(relaxed = true)
        every { process.inputStream } returns stream
        drainMasqueOutput(process)
        verify { stream.close() }
    }

    @Test
    fun `forced termination uses the API appropriate process method`() {
        val process = mockk<Process>(relaxed = true)
        forceStopProcess(process, 24)
        forceStopProcess(process, 25)
        forceStopProcess(process, 26)
        forceStopProcess(process, 35)
        verify(exactly = 2) { process.destroy() }
        verify(exactly = 2) { process.destroyForcibly() }
    }

    @Test
    fun `normal process exit is awaited without forcing termination`() {
        val process = mockk<Process>(relaxed = true)
        var polls = 0
        every { process.exitValue() } answers {
            if (++polls <= 2) throw IllegalThreadStateException() else 0
        }
        val child = ManagedMasqueProcess(process, forceStop = { error("force stop not needed") })
        child.close()
        assertFalse(child.alive)
        assertTrue(polls >= 3)
    }

    @Test
    fun `default forced termination closes a stubborn child`() {
        val process = mockk<Process>(relaxed = true)
        var exited = false
        var destroyCalls = 0
        every { process.exitValue() } answers { if (exited) 0 else throw IllegalThreadStateException() }
        every { process.destroy() } answers {
            if (++destroyCalls == 2) exited = true
        }
        every { process.destroyForcibly() } answers {
            exited = true
            process
        }
        val child = ManagedMasqueProcess(process, stopWaitMs = 0)
        child.close()
        assertFalse(child.alive)
        verify(atLeast = 1) { process.destroy() }
    }

    @Test
    fun `closing a broken stdin still destroys the child`() {
        val process = mockk<Process>(relaxed = true)
        val output = mockk<java.io.OutputStream>(relaxed = true)
        every { process.outputStream } returns output
        every { output.close() } throws IOException("pipe closed")
        every { process.exitValue() } returns 0
        assertThrows<IOException> { ManagedMasqueProcess(process).close() }
        verify(exactly = 1) { process.destroy() }
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
