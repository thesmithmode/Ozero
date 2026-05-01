package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class HevTunnelGatewayShutdownOrderTest {

    private val loader = object : TProxyLoader {
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
    }

    private fun pfd(fd: Int): ParcelFileDescriptor = mockk(relaxed = true) {
        every { this@mockk.fd } returns fd
    }

    @Test
    fun `stop вызывает только nativeStop — gateway не владеет fd`(@TempDir tmp: File) {
        val events = mutableListOf<String>()
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { events.add("nativeStop") },
        )
        gateway.start(HevTunnelConfig(tunPfd = pfd(42), socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()

        assertEquals(
            listOf("nativeStop"),
            events,
            "Gateway больше НЕ закрывает fd. Закрытие — ответственность OzeroVpnService.tunFdRef в " +
                "performShutdown ПОСЛЕ nativeStop. Это симметрия с upstream ByeDPIAndroid/ByeByeDPI: " +
                "raw fd передаётся в native, OzeroVpnService.tunFdRef.close() в shutdown finally.",
        )
    }

    @Test
    fun `stop без предшествующего start не ломается`(@TempDir tmp: File) {
        var stopCalled = 0
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalled++ },
        )

        gateway.stop()

        assertEquals(1, stopCalled, "nativeStop вызывается даже без start — гарантия teardown")
    }

    @Test
    fun `stop вызывается дважды подряд — обе ветки доходят до nativeStop`(@TempDir tmp: File) {
        var nativeStops = 0
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { nativeStops++ },
        )
        gateway.start(HevTunnelConfig(tunPfd = pfd(1), socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()
        gateway.stop()

        assertEquals(2, nativeStops, "stop идемпотентен — каждый вызов уходит в native")
    }
}
