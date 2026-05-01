package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeHevTunnelGatewayTest {

    private val loader = object : TProxyLoader {
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
    }

    private fun pfd(fd: Int): ParcelFileDescriptor = mockk(relaxed = true) {
        every { this@mockk.fd } returns fd
    }

    @Test
    fun `start passes raw tunPfd_fd to native (no dup)`(@TempDir tmp: File) {
        val tun = pfd(42)
        var capturedFd = -1
        var capturedPath: String? = null
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { path, fd ->
                capturedPath = path
                capturedFd = fd
                0
            },
            nativeStop = {},
        )
        val cfg = HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080)

        val rc = gateway.start(cfg)

        assertEquals(0, rc)
        assertEquals(42, capturedFd, "native должен получить raw tunPfd.fd, без dup")
        val path = assertNotNull(capturedPath)
        assertTrue(File(path).readText().contains("address: 127.0.0.1"))
    }

    @Test
    fun `stop вызывает только nativeStop — fd закрывает OzeroVpnService, не gateway`(@TempDir tmp: File) {
        val tun = pfd(42)
        var stopCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalled = true },
        )
        gateway.start(HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()

        assertTrue(stopCalled, "nativeStop должен быть вызван")
    }

    @Test
    fun `start возвращает ненулевой код если nativeStart вернул ненулевой`(@TempDir tmp: File) {
        val tun = pfd(42)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 7 },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(7, rc)
    }

    @Test
    fun `start возвращает -1 если native бросил UnsatisfiedLinkError`(@TempDir tmp: File) {
        val tun = pfd(42)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> throw UnsatisfiedLinkError("libhev not found") },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc, "linkage error не пропагируется наружу — возвращаем -1")
    }

    @Test
    fun `start возвращает -1 если loader libraryLoaded=false`(@TempDir tmp: File) {
        val failedLoader = object : TProxyLoader {
            override fun loadOnce() = Unit
            override val libraryLoaded: Boolean = false
            override val loadError: String? = "library load failed: dlopen ENOENT"
        }
        val tun = pfd(42)
        var nativeCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = failedLoader,
            nativeStart = { _, _ ->
                nativeCalled = true
                0
            },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc)
        assertTrue(!nativeCalled, "nativeStart не должен вызываться если library не загружена")
    }

    @Test
    fun `повторный start с другим fd — оба вызова идут в native с raw fd`(@TempDir tmp: File) {
        val tun1 = pfd(11)
        val tun2 = pfd(22)
        val captured = mutableListOf<Int>()
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, fd ->
                captured.add(fd)
                0
            },
            nativeStop = {},
        )
        gateway.start(HevTunnelConfig(tunPfd = tun1, socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.start(HevTunnelConfig(tunPfd = tun2, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(listOf(11, 22), captured, "каждый start пробрасывает свой raw fd")
    }

    @Test
    fun `stop проглатывает throwable из нативки`(@TempDir tmp: File) {
        val tun = pfd(42)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { throw IllegalStateException("native stop failed") },
        )
        gateway.start(HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()
    }

    @Test
    fun `start создаёт cacheDir если он отсутствует`(@TempDir tmp: File) {
        val tun = pfd(1)
        val nested = File(tmp, "missing/cache")
        assertTrue(!nested.exists())
        val gateway = NativeHevTunnelGateway(
            cacheDir = nested,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
        )

        gateway.start(HevTunnelConfig(tunPfd = tun, socksAddress = "127.0.0.1", socksPort = 1080))

        assertTrue(nested.exists(), "cacheDir должен быть создан")
    }
}
