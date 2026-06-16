package ru.ozero.singboxprocess

import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.ISingboxProtector
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxProtectorBridgeTest {

    @Test
    fun `protect returns aidl result`() {
        val bridge = SingboxProtectorBridge(
            object : ISingboxProtector.Stub() {
                override fun protect(fd: Int): Boolean = fd == 42
            },
        )

        assertTrue(bridge.protect(42))
        assertFalse(bridge.protect(41))
    }

    @Test
    fun `protect returns false when aidl throws`() {
        val bridge = SingboxProtectorBridge(
            object : ISingboxProtector.Stub() {
                override fun protect(fd: Int): Boolean = throw IllegalStateException("binder died")
            },
        )

        assertFalse(bridge.protect(42))
    }
}
