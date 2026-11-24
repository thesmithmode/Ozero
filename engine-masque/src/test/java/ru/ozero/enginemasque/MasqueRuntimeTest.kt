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
import java.io.IOException
import org.json.JSONObject

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

    @Test
    fun `IPv6 endpoint fallback is independent from inner IPv6`() = runBlocking {
        val commands = mutableListOf<List<String>>()
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { command ->
                commands.add(command)
                File(File(command[2]).parentFile, "ready").writeText("ready")
                Child("--ipv6" in command)
            },
            MasqueReadinessProbe { true },
        )
        val config = MasqueProfile.parse(JSONObject(profile().json).put("endpoint_v6", "2001:db8::3").toString())
        assertEquals(MasqueTransport.H3, runtime.start(config, 1080, MasqueTransport.AUTO))
        assertEquals(2, commands.size)
        assertFalse("--ipv6" in commands.first())
        assertTrue("--ipv6" in commands.last())
        assertTrue("--no-tunnel-ipv6" in commands.last())
        assertThrows<IllegalStateException> { runtime.register(true) }
        runtime.stop()
        runtime.stop()
    }

    @Test
    fun `launch failure cleans workspace and falls back`() = runBlocking {
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { command ->
                if ("--http2" !in command) throw IOException("fixture")
                File(File(command[2]).parentFile, "ready").writeText("ready")
                Child(true)
            },
            MasqueReadinessProbe { true },
        )
        assertEquals(MasqueTransport.H2, runtime.start(profile(), 1080, MasqueTransport.AUTO))
        runtime.stop()
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }

    @Test
    fun `registration timeout closes child and cleans workspace`() = runBlocking {
        val child = Child(true)
        val runtime = MasqueRuntime(
            binary(),
            directory,
            MasqueProcessLauncher { child },
            MasqueReadinessProbe { false },
            registrationTimeoutMs = 10,
        )
        assertThrows<IllegalStateException> { runtime.register(true) }
        assertFalse(child.alive)
        assertEquals(listOf("usque"), directory.list().orEmpty().toList())
    }

    @Test
    fun `invalid port binary and private directory fail before launch`() = runBlocking {
        val launcher = MasqueProcessLauncher { error("Must not launch") }
        val missing = File(directory, "missing")
        val runtime = MasqueRuntime(missing, directory, launcher, MasqueReadinessProbe { false })
        assertThrows<IllegalArgumentException> { runtime.start(profile(), 0, MasqueTransport.H3) }
        assertThrows<IllegalStateException> { runtime.start(profile(), 1080, MasqueTransport.H3) }
        val badDirectory = MasqueRuntime(binary(), missing, launcher, MasqueReadinessProbe { false })
        assertThrows<IllegalStateException> { badDirectory.register(true) }
    }
}
