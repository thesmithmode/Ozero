package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeHevTunnelGatewayTest {

    @Test
    fun `start пишет yaml и передаёт path плюс fd в нативку`(@TempDir tmp: File) {
        var capturedPath: String? = null
        var capturedFd: Int = -1
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { path, fd ->
                capturedPath = path
                capturedFd = fd
                0
            },
            nativeStop = {},
        )
        val config = HevTunnelConfig(tunFd = 42, socksAddress = "127.0.0.1", socksPort = 1080)

        val rc = gateway.start(config)

        assertEquals(0, rc)
        val path = assertNotNull(capturedPath, "path должен передаваться в нативку")
        assertEquals(42, capturedFd)
        val written = File(path).readText()
        assertTrue(written.contains("address: 127.0.0.1"), "YAML должен содержать socks address")
        assertTrue(written.contains("port: 1080"), "YAML должен содержать socks port")
    }

    @Test
    fun `start возвращает -1 если нативка кидает linkage error`(@TempDir tmp: File) {
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> throw UnsatisfiedLinkError("libhev not found") },
            nativeStop = {},
        )
        val config = HevTunnelConfig(tunFd = 1, socksAddress = "127.0.0.1", socksPort = 1080)

        val rc = gateway.start(config)

        assertEquals(-1, rc, "linkage error не должен пропагироваться — gateway возвращает -1")
    }

    @Test
    fun `stop проглатывает throwable из нативки`(@TempDir tmp: File) {
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = { throw IllegalStateException("native stop failed") },
        )
        gateway.stop()
    }

    @Test
    fun `start создаёт cacheDir если он отсутствует`(@TempDir tmp: File) {
        val nested = File(tmp, "missing/cache")
        assertTrue(!nested.exists())
        val gateway = NativeHevTunnelGateway(
            cacheDir = nested,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
        )

        gateway.start(HevTunnelConfig(tunFd = 1, socksAddress = "127.0.0.1", socksPort = 1080))

        assertTrue(nested.exists(), "cacheDir должен быть создан")
    }
}
