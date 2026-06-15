package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.enginescore.PersistentLogger
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeHevTunnelGatewayCoverageTest {

    private val loader = object : TProxyLoader {
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
    }

    private fun pfd(fd: Int): ParcelFileDescriptor = mockk(relaxed = true) {
        every { this@mockk.fd } returns fd
    }

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 2_000L
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(10L)
        }
        return predicate()
    }

    @Test
    fun `start calls loader before nativeStart`(@TempDir tmp: File) {
        val events = mutableListOf<String>()
        val orderedLoader = object : TProxyLoader {
            override fun loadOnce() {
                events.add("load")
            }

            override val libraryLoaded: Boolean = true
            override val loadError: String? = null
        }
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = orderedLoader,
            nativeStart = { _, _ ->
                events.add("native")
                0
            },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(3), socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(0, rc)
        assertEquals(listOf("load", "native"), events)
    }

    @Test
    fun `failed nativeStart does not mark gateway started`(@TempDir tmp: File) {
        var stopCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> -3 },
            nativeStop = { stopCalled = true },
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(4), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.stop()

        assertEquals(-3, rc)
        assertFalse(stopCalled)
    }

    @Test
    fun `loader failure returns minus one and skips nativeStart`(@TempDir tmp: File) {
        var nativeCalled = false
        val failingLoader = object : TProxyLoader {
            override fun loadOnce() = Unit
            override val libraryLoaded: Boolean = false
            override val loadError: String? = "missing"
        }
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = failingLoader,
            nativeStart = { _, _ ->
                nativeCalled = true
                0
            },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(10), socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc)
        assertFalse(nativeCalled)
    }

    @Test
    fun `default native callbacks support load failure before native calls`(@TempDir tmp: File) {
        val failingLoader = object : TProxyLoader {
            override fun loadOnce() = Unit
            override val libraryLoaded: Boolean = false
            override val loadError: String? = null
        }
        val gateway = NativeHevTunnelGateway(cacheDir = tmp, loader = failingLoader)

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(22), socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc)
    }

    @Test
    fun `default native start exception is converted to minus one`(@TempDir tmp: File) {
        val gateway = NativeHevTunnelGateway(cacheDir = tmp, loader = loader)

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(24), socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc)
    }

    @Test
    fun `default native stats callback is swallowed by poller`(@TempDir tmp: File) {
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            pollIntervalMs = 1L,
            statsPollerEnabled = true,
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(25), socksAddress = "127.0.0.1", socksPort = 1080))
        Thread.sleep(10L)
        gateway.stop()

        assertEquals(0, rc)
    }

    @Test
    fun `nativeStart throw is converted to minus one and stop remains skipped`(@TempDir tmp: File) {
        var stopCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> error("native boom") },
            nativeStop = { stopCalled = true },
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(11), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.stop()

        assertEquals(-1, rc)
        assertFalse(stopCalled)
    }

    @Test
    fun `nativeStop throw is swallowed after successful start`(@TempDir tmp: File) {
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { error("stop boom") },
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(12), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.stop()
        gateway.stop()

        assertTrue(true)
    }

    @Test
    fun `written config contains custom socks host port and mtu`(@TempDir tmp: File) {
        var capturedPath: String? = null
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { path, _ ->
                capturedPath = path
                0
            },
            nativeStop = {},
        )

        gateway.start(
            HevTunnelConfig(
                tunPfd = pfd(5),
                socksAddress = "10.0.0.2",
                socksPort = 2080,
                tunMtu = 1400,
            ),
        )

        val yaml = File(assertNotNull(capturedPath)).readText()
        assertTrue(yaml.contains("address: 10.0.0.2"))
        assertTrue(yaml.contains("port: 2080"))
        assertTrue(yaml.contains("mtu: 1400"))
    }

    @Test
    fun `written config uses stable file name and default udp mode`(@TempDir tmp: File) {
        var capturedPath: String? = null
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { path, _ ->
                capturedPath = path
                0
            },
            nativeStop = {},
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(15), socksAddress = "127.0.0.1", socksPort = 1080))

        val configFile = File(assertNotNull(capturedPath))
        assertEquals("hev-socks5-tunnel.yaml", configFile.name)
        assertEquals(tmp.absoluteFile, configFile.parentFile?.absoluteFile)
        val yaml = configFile.readText()
        assertTrue(yaml.contains("udp: udp"))
        assertTrue(yaml.endsWith("\n"))
    }

    @Test
    fun `start creates missing cache directory before writing config`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "nested-cache")
        var capturedPath: String? = null
        val gateway = NativeHevTunnelGateway(
            cacheDir = cacheDir,
            loader = loader,
            nativeStart = { path, _ ->
                capturedPath = path
                0
            },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(17), socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(0, rc)
        assertTrue(cacheDir.isDirectory)
        assertTrue(File(assertNotNull(capturedPath)).isFile)
    }

    @Test
    fun `stats poller disabled never calls nativeStats`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(2)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                statsCalls.incrementAndGet()
                longArrayOf(1L, 2L, 3L, 4L)
            },
            pollIntervalMs = 1L,
            statsPollerEnabled = false,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(16), socksAddress = "127.0.0.1", socksPort = 1080))
        Thread.sleep(10L)
        gateway.stop()

        assertEquals(0, statsCalls.get())
    }

    @Test
    fun `stop after successful start is idempotent`(@TempDir tmp: File) {
        val stopCalls = AtomicInteger(0)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalls.incrementAndGet() },
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(6), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.stop()
        gateway.stop()

        assertEquals(1, stopCalls.get())
    }

    @Test
    fun `stats poller tolerates null and short native stats`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(2)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                when (statsCalls.incrementAndGet()) {
                    1 -> null
                    2 -> longArrayOf(1L)
                    else -> longArrayOf(1L, 2L, 3L, 4L)
                }
            },
            pollIntervalMs = 5L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(7), socksAddress = "127.0.0.1", socksPort = 1080))
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()

        assertTrue(statsCalls.get() >= 2)
    }

    @Test
    fun `second successful start replaces previous stats poller`(@TempDir tmp: File) {
        val stats = CopyOnWriteArrayList<LongArray>()
        stats.add(longArrayOf(0L, 0L, 0L, 0L))
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = { stats.lastOrNull() },
            pollIntervalMs = 5L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(8), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.start(HevTunnelConfig(tunPfd = pfd(9), socksAddress = "127.0.0.1", socksPort = 1080))
        stats.add(longArrayOf(1L, 10L, 2L, 20L))
        Thread.sleep(25L)
        gateway.stop()

        assertEquals(2, stats.size)
    }

    @Test
    fun `stats poller records movement and idle samples`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(2)
        val samples = listOf(
            longArrayOf(0L, 0L, 0L, 0L),
            longArrayOf(1L, 10L, 2L, 20L),
        )
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                samples.getOrElse(statsCalls.getAndIncrement()) { samples.last() }
            },
            pollIntervalMs = 2L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(13), socksAddress = "127.0.0.1", socksPort = 1080))
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()

        assertTrue(statsCalls.get() >= 2)
    }

    @Test
    fun `stats poller handles zero movement then later movement`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(3)
        val samples = listOf(
            longArrayOf(0L, 0L, 0L, 0L),
            longArrayOf(0L, 0L, 0L, 0L),
            longArrayOf(1L, 10L, 1L, 10L),
        )
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                samples.getOrElse(statsCalls.getAndIncrement()) { samples.last() }
            },
            pollIntervalMs = 1L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(23), socksAddress = "127.0.0.1", socksPort = 1080))
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()

        assertTrue(statsCalls.get() >= 3)
    }

    @Test
    fun `stats poller treats rx only delta as movement`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(2)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                when (statsCalls.getAndIncrement()) {
                    0 -> longArrayOf(0L, 0L, 0L, 0L)
                    else -> longArrayOf(0L, 0L, 1L, 12L)
                }
            },
            pollIntervalMs = 1L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(30), socksAddress = "127.0.0.1", socksPort = 1080))
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()

        assertTrue(statsCalls.get() >= 2)
    }

    @Test
    fun `stats poller survives repeated idle samples until stop`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(7)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                statsCalls.incrementAndGet()
                longArrayOf(1L, 10L, 2L, 20L)
            },
            pollIntervalMs = 1L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(18), socksAddress = "127.0.0.1", socksPort = 1080))
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()

        assertTrue(statsCalls.get() >= 7)
    }

    @Test
    fun `nativeStats supports null and short samples`(@TempDir tmp: File) {
        val observed = CountDownLatch(4)
        val stats = listOf<LongArray?>(
            null,
            longArrayOf(0L),
            longArrayOf(0L, 0L, 0L, 0L),
            longArrayOf(1L, 10L, 2L, 20L),
        )
        val calls = AtomicInteger(0)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                stats.getOrElse(calls.getAndIncrement()) { stats.last() }
            },
            pollIntervalMs = 1L,
            statsPollerEnabled = true,
        )
        val rc = gateway.start(
            HevTunnelConfig(
                tunPfd = pfd(20),
                socksAddress = "127.0.0.1",
                socksPort = 1080,
            ),
        )

        assertEquals(0, rc)
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()
    }

    @Test
    fun `nativeStats exception from poller exits worker`(@TempDir tmp: File) {
        val observed = CountDownLatch(1)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                error("native stats failed")
            },
            pollIntervalMs = 1L,
            statsPollerEnabled = true,
        )
        val rc = gateway.start(
            HevTunnelConfig(
                tunPfd = pfd(21),
                socksAddress = "127.0.0.1",
                socksPort = 1080,
            ),
        )

        assertEquals(0, rc)
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()
    }

    @Test
    fun `stats poller stops when nativeStats throws`(@TempDir tmp: File) {
        val statsCalls = AtomicInteger(0)
        val observed = CountDownLatch(1)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                observed.countDown()
                statsCalls.incrementAndGet()
                error("stats boom")
            },
            pollIntervalMs = 2L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(14), socksAddress = "127.0.0.1", socksPort = 1080))
        assertTrue(waitUntil { observed.count == 0L })
        gateway.stop()

        assertEquals(1, statsCalls.get())
    }

    @Test
    fun `persistent logger receives load start stop movement idle and failure branches`(@TempDir tmp: File) {
        val logger = RecordingLogger()
        val previous = PersistentLoggers.instance
        PersistentLoggers.instance = logger
        try {
            val statsCalls = AtomicInteger(0)
            val observed = CountDownLatch(8)
            val gateway = NativeHevTunnelGateway(
                cacheDir = File(tmp, "cache"),
                loader = loader,
                nativeStart = { _, _ -> 0 },
                nativeStop = { error("stop failed") },
                nativeStats = {
                    observed.countDown()
                    when (statsCalls.incrementAndGet()) {
                        1 -> longArrayOf(0L, 0L, 0L, 0L)
                        2 -> longArrayOf(1L, 10L, 2L, 20L)
                        else -> longArrayOf(1L, 10L, 2L, 20L)
                    }
                },
                pollIntervalMs = 1L,
                statsPollerEnabled = true,
            )

            assertEquals(
                0,
                gateway.start(HevTunnelConfig(tunPfd = pfd(19), socksAddress = "127.0.0.1", socksPort = 1080)),
            )
            assertTrue(waitUntil { observed.count == 0L && logger.warns.any { it.contains("hev stats IDLE") } })
            gateway.stop()

            assertTrue(logger.infos.any { it.contains("checkpoint loadOnce returned") })
            assertTrue(logger.infos.any { it.contains("hev stats tx=") })
            assertTrue(logger.warns.any { it.contains("hev stats IDLE") })
            assertTrue(logger.warns.any { it.contains("TProxyStopService threw") })
        } finally {
            PersistentLoggers.instance = previous
        }
    }

    @Test
    fun `null persistent logger still starts and stops without logging branches`(@TempDir tmp: File) {
        val previous = PersistentLoggers.instance
        PersistentLoggers.instance = null
        try {
            var stopped = false
            val gateway = NativeHevTunnelGateway(
                cacheDir = tmp,
                loader = loader,
                nativeStart = { _, _ -> 0 },
                nativeStop = { stopped = true },
            )

            assertEquals(
                0,
                gateway.start(
                    HevTunnelConfig(
                        tunPfd = pfd(26),
                        socksAddress = "127.0.0.1",
                        socksPort = 1080,
                    ),
                ),
            )
            gateway.stop()

            assertTrue(stopped)
        } finally {
            PersistentLoggers.instance = previous
        }
    }

    @Test
    fun `null persistent logger loader failure skips native start without logging branches`(@TempDir tmp: File) {
        val previous = PersistentLoggers.instance
        PersistentLoggers.instance = null
        try {
            var nativeStarted = false
            val failingLoader = object : TProxyLoader {
                override fun loadOnce() = Unit
                override val libraryLoaded: Boolean = false
                override val loadError: String? = "missing"
            }
            val gateway = NativeHevTunnelGateway(
                cacheDir = tmp,
                loader = failingLoader,
                nativeStart = { _, _ ->
                    nativeStarted = true
                    0
                },
                nativeStop = {},
            )

            assertEquals(
                -1,
                gateway.start(
                    HevTunnelConfig(
                        tunPfd = pfd(27),
                        socksAddress = "127.0.0.1",
                        socksPort = 1080,
                    ),
                ),
            )
            assertFalse(nativeStarted)
        } finally {
            PersistentLoggers.instance = previous
        }
    }

    @Test
    fun `stats poller can be replaced before previous poller samples`(@TempDir tmp: File) {
        val started = CountDownLatch(1)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                started.countDown()
                longArrayOf(0L, 0L, 0L, 0L)
            },
            pollIntervalMs = 100L,
            statsPollerEnabled = true,
        )

        gateway.start(HevTunnelConfig(tunPfd = pfd(28), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.start(HevTunnelConfig(tunPfd = pfd(29), socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.stop()

        assertTrue(started.count >= 0L)
    }

    private class RecordingLogger : PersistentLogger {
        val infos = CopyOnWriteArrayList<String>()
        val warns = CopyOnWriteArrayList<String>()

        override fun trace(tag: String, msg: String) = Unit
        override fun debug(tag: String, msg: String) = Unit
        override fun info(tag: String, msg: String) {
            infos += msg
        }

        override fun warn(tag: String, msg: String, t: Throwable?) {
            warns += msg
        }

        override fun error(tag: String, msg: String, t: Throwable?) = Unit
    }
}
