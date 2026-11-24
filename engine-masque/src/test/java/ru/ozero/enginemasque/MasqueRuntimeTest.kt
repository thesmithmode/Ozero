package ru.ozero.enginemasque

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MasqueRuntimeTest {
    @TempDir
    lateinit var directory: File

    private class Child(override var alive: Boolean) : MasqueProcess {
        override fun exitCode(): Int = 1
        override fun close() {
            alive = false
        }
    }

    private fun profile(): MasqueProfile = MasqueProfile.parse(
        """{"private_key":"test","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1","ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}""",
    )

    private fun binary(): File = File(directory, "usque").apply {
        writeText("test")
        check(setExecutable(true))
    }

    @Test
    fun `auto cleans failed process and starts h2 only after readiness`() = runBlocking {
        val children = mutableListOf<Child>()
        val commands = mutableListOf<List<String>>()
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { command ->
                commands.add(command)
                Child("--http2" in command).also { children.add(it) }
            },
            MasqueReadinessProbe { true },
        )
        assertEquals(MasqueTransport.H2, runtime.start(profile(), 1080, MasqueTransport.AUTO))
        assertEquals(2, commands.size)
        assertFalse(children.first().alive)
        assertTrue(runtime.isReady())
        runtime.stop()
        assertFalse(children.last().alive)
        assertFalse(runtime.isReady())
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }

    @Test
    fun `manual mode returns null on early process failure`() = runBlocking {
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { Child(false) },
            MasqueReadinessProbe { error("Dead process cannot be ready") },
        )
        assertNull(runtime.start(profile(), 1080, MasqueTransport.H3))
        assertFalse(runtime.isReady())
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }

    @Test
    fun `failed readiness closes process without reporting success`() = runBlocking {
        val child = Child(true)
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { child },
            MasqueReadinessProbe { false },
            startupTimeoutMs = 10,
        )
        assertNull(runtime.start(profile(), 1080, MasqueTransport.H3))
        assertFalse(child.alive)
        assertFalse(runtime.isReady())
    }
}
