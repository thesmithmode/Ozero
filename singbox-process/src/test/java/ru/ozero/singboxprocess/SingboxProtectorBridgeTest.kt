package ru.ozero.singboxprocess

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.ISingboxProtector
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxProtectorBridgeTest {

    @Test
    fun `protect returns aidl result`() {
        mockkStatic(ParcelFileDescriptor::class)
        val socket = mockk<ParcelFileDescriptor>(relaxed = true)
        val bridge = SingboxProtectorBridge(
            object : ISingboxProtector.Stub() {
                override fun protect(received: ParcelFileDescriptor): Boolean = received === socket
            },
        )
        every { ParcelFileDescriptor.fromFd(42) } returns socket

        try {
            assertTrue(bridge.protect(42))
            verify(exactly = 1) { socket.close() }
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `protect returns false when aidl throws`() {
        mockkStatic(ParcelFileDescriptor::class)
        val socket = mockk<ParcelFileDescriptor>(relaxed = true)
        val bridge = SingboxProtectorBridge(
            object : ISingboxProtector.Stub() {
                override fun protect(received: ParcelFileDescriptor): Boolean =
                    throw IllegalStateException("binder died")
            },
        )
        every { ParcelFileDescriptor.fromFd(42) } returns socket

        try {
            assertFalse(bridge.protect(42))
            verify(exactly = 1) { socket.close() }
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }
}
