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
    fun `stop без предшествующего start не вызывает nativeStop`(@TempDir tmp: File) {
        var stopCalled = 0
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalled++ },
        )

        gateway.stop()

        assertEquals(
            0,
            stopCalled,
            "TProxyStopService без предшествующего TProxyStartService блокирует libhev mutex " +
                "(следующий start висит). Sentinel против регрессии: chain failed at step 0 → " +
                "performShutdown.tunnelGateway.stop() → next BYEDPI start hung forever.",
        )
    }

    @Test
    fun `stop после failed start не вызывает nativeStop`(@TempDir tmp: File) {
        var stopCalled = 0
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> -1 },
            nativeStop = { stopCalled++ },
        )
        gateway.start(HevTunnelConfig(tunPfd = pfd(1), socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()

        assertEquals(0, stopCalled, "Failed start (code != 0) не помечает gateway как started")
    }

    @Test
    fun `stop после успешного start вызывает nativeStop ровно один раз`(@TempDir tmp: File) {
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

        assertEquals(1, nativeStops, "stop идемпотентен — повторный вызов skip'ается через started flag")
    }
}
