package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class NativeHevTunnelGatewayStatsPollerTest {

    private val loader = object : TProxyLoader {
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
    }

    private fun pfd(fd: Int): ParcelFileDescriptor = mockk(relaxed = true) {
        every { this@mockk.fd } returns fd
    }

    @Test
    fun `stats poller tolerates null short idle and moving stats`(@TempDir tmp: File) {
        val calls = AtomicInteger(0)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                when (calls.getAndIncrement()) {
                    0 -> null
                    1 -> longArrayOf(1L, 2L)
                    2 -> longArrayOf(1L, 10L, 2L, 20L)
                    3 -> longArrayOf(1L, 10L, 2L, 20L)
                    else -> longArrayOf(3L, 30L, 4L, 40L)
                }
            },
            pollIntervalMs = 1,
            statsPollerEnabled = true,
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(42), socksAddress = "127.0.0.1", socksPort = 1080))
        Thread.sleep(50)
        gateway.stop()

        assertEquals(0, rc)
        assertEquals(true, calls.get() >= 1)
    }

    @Test
    fun `stats poller stops when native stats throws`(@TempDir tmp: File) {
        val calls = AtomicInteger(0)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
            nativeStats = {
                calls.incrementAndGet()
                throw IllegalStateException("stats failed")
            },
            pollIntervalMs = 1,
            statsPollerEnabled = true,
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = pfd(42), socksAddress = "127.0.0.1", socksPort = 1080))
        Thread.sleep(50)
        gateway.stop()

        assertEquals(0, rc)
        assertEquals(true, calls.get() >= 1)
    }
}
