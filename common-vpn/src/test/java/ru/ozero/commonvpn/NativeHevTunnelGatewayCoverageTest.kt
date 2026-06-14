package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
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
        assertTrue(observed.await(1, TimeUnit.SECONDS))
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
        val observed = CountDownLatch(8)
        val samples = listOf(
            longArrayOf(0L, 0L, 0L, 0L),
            longArrayOf(1L, 10L, 2L, 20L),
            longArrayOf(1L, 10L, 2L, 20L),
            longArrayOf(1L, 10L, 2L, 20L),
            longArrayOf(1L, 10L, 2L, 20L),
            longArrayOf(1L, 10L, 2L, 20L),
            longArrayOf(1L, 10L, 2L, 20L),
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
        assertTrue(observed.await(1, TimeUnit.SECONDS))
        gateway.stop()

        assertTrue(statsCalls.get() >= samples.size)
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
        assertTrue(observed.await(1, TimeUnit.SECONDS))
        gateway.stop()

        assertEquals(1, statsCalls.get())
    }
}
