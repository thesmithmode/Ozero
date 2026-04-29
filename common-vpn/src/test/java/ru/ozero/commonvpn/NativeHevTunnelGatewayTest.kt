package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeHevTunnelGatewayTest {

    private fun pair(originalFd: Int, dupedFd: Int): Pair<ParcelFileDescriptor, ParcelFileDescriptor> {
        val duped: ParcelFileDescriptor = mockk(relaxed = true) {
            every { this@mockk.fd } returns dupedFd
        }
        val original: ParcelFileDescriptor = mockk {
            every { this@mockk.fd } returns originalFd
            every { dup() } returns duped
        }
        return original to duped
    }

    @Test
    fun `start dups pfd and passes duped fd to native, не original`(@TempDir tmp: File) {
        val (original, duped) = pair(originalFd = 42, dupedFd = 4242)
        var capturedFd = -1
        var capturedPath: String? = null
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { path, fd ->
                capturedPath = path
                capturedFd = fd
                0
            },
            nativeStop = {},
        )
        val cfg = HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080)

        val rc = gateway.start(cfg)

        assertEquals(0, rc)
        assertEquals(4242, capturedFd, "native должен получить duped fd")
        assertNotEquals(42, capturedFd, "native НЕ должен получить original fd")
        val path = assertNotNull(capturedPath)
        assertTrue(File(path).readText().contains("address: 127.0.0.1"))
        verify(exactly = 1) { original.dup() }
        verify(exactly = 0) { duped.close() }
    }

    @Test
    fun `stop closes duped pfd after nativeStop`(@TempDir tmp: File) {
        val (original, duped) = pair(originalFd = 42, dupedFd = 4242)
        var stopCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalled = true },
        )
        gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()

        assertTrue(stopCalled, "nativeStop должен быть вызван")
        verify(exactly = 1) { duped.close() }
        confirmVerified(duped)
    }

    @Test
    fun `start closes duped on nonzero native code (failure path)`(@TempDir tmp: File) {
        val (original, duped) = pair(originalFd = 42, dupedFd = 4242)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 7 },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(7, rc)
        verify(exactly = 1) { duped.close() }
    }

    @Test
    fun `start closes duped when native throws linkage error`(@TempDir tmp: File) {
        val (original, duped) = pair(originalFd = 42, dupedFd = 4242)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> throw UnsatisfiedLinkError("libhev not found") },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc, "linkage error не пропагируется")
        verify(exactly = 1) { duped.close() }
    }

    @Test
    fun `start returns -1 if dup itself throws (без вызова nativeStart)`(@TempDir tmp: File) {
        val original: ParcelFileDescriptor = mockk {
            every { this@mockk.fd } returns 42
            every { dup() } throws java.io.IOException("dup failed: too many fds")
        }
        var nativeCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ ->
                nativeCalled = true
                0
            },
            nativeStop = {},
        )

        val rc = gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        assertEquals(-1, rc)
        assertTrue(!nativeCalled, "nativeStart не должен вызваться если dup упал")
    }

    @Test
    fun `повторный start закрывает прошлый duped перед dup нового`(@TempDir tmp: File) {
        val (original1, duped1) = pair(originalFd = 11, dupedFd = 111)
        val (original2, duped2) = pair(originalFd = 22, dupedFd = 222)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
        )
        gateway.start(HevTunnelConfig(tunPfd = original1, socksAddress = "127.0.0.1", socksPort = 1080))
        gateway.start(HevTunnelConfig(tunPfd = original2, socksAddress = "127.0.0.1", socksPort = 1080))

        verify(exactly = 1) { duped1.close() }
        verify(exactly = 0) { duped2.close() }
    }

    @Test
    fun `stop проглатывает throwable из нативки и всё равно закрывает duped`(@TempDir tmp: File) {
        val (original, duped) = pair(originalFd = 42, dupedFd = 4242)
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = { throw IllegalStateException("native stop failed") },
        )
        gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()

        verify(exactly = 1) { duped.close() }
    }

    @Test
    fun `start создаёт cacheDir если он отсутствует`(@TempDir tmp: File) {
        val (original, _) = pair(originalFd = 1, dupedFd = 2)
        val nested = File(tmp, "missing/cache")
        assertTrue(!nested.exists())
        val gateway = NativeHevTunnelGateway(
            cacheDir = nested,
            nativeStart = { _, _ -> 0 },
            nativeStop = {},
        )

        gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        assertTrue(nested.exists(), "cacheDir должен быть создан")
    }
}
