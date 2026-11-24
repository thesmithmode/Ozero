package ru.ozero.enginemasque

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MasqueRuntimeTest {
    @TempDir
    lateinit var directory: File

    private class Child(override var alive: Boolean, private val code: Int = 1) : MasqueProcess {
        override fun exitCode(): Int = code
        override fun close() {
            alive = false
        }
    }

    private fun profile(): MasqueProfile = MasqueProfile.parse(
        """{"private_key":"test","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin(),
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
                File(File(command[2]).parentFile, "ready").writeText("ready")
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
            MasqueProcessLauncher { command ->
                File(File(command[2]).parentFile, "ready").writeText("ready")
                child
            },
            MasqueReadinessProbe { false },
            startupTimeoutMs = 10,
        )
        assertNull(runtime.start(profile(), 1080, MasqueTransport.H3))
        assertFalse(child.alive)
        assertFalse(runtime.isReady())
    }

    @Test
    fun `listener without child owned readiness marker cannot report success`() = runBlocking {
        val child = Child(true)
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { child },
            MasqueReadinessProbe { error("Unowned port must not be probed") },
            startupTimeoutMs = 10,
        )
        assertNull(runtime.start(profile(), 1080, MasqueTransport.H3))
        assertFalse(child.alive)
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }

    @Test
    fun `cancelling startup closes child and removes plaintext`() = runBlocking {
        val launched = CompletableDeferred<Unit>()
        val child = Child(true)
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher {
                launched.complete(Unit)
                child
            },
            MasqueReadinessProbe { false },
        )
        val job = launch { runtime.start(profile(), 1080, MasqueTransport.H3) }
        launched.await()
        yield()
        job.cancelAndJoin()
        assertFalse(child.alive)
        assertFalse(runtime.isReady())
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }
    @Test
    fun `registration requires explicit terms and deletes temporary profile`() = runBlocking {
        val commands = mutableListOf<List<String>>()
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { command ->
                commands.add(command)
                File(command[2]).writeText(profile().json)
                Child(false, 0)
            },
            MasqueReadinessProbe { false },
        )
        assertThrows<IllegalArgumentException> { runtime.register(false) }
        assertTrue(commands.isEmpty())
        assertEquals(profile().json, runtime.register(true).json)
        assertEquals(1, commands.size)
        assertTrue("--accept-tos" in commands.single())
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }

    @Test
    fun `registration rejects child failure and deletes temporary profile`() = runBlocking {
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { command ->
                File(command[2]).writeText(profile().json)
                Child(false)
            },
            MasqueReadinessProbe { false },
        )
        assertThrows<IllegalStateException> { runtime.register(true) }
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }
}
