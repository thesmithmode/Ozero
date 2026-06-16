package ru.ozero.enginefptn

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FptnNativeWebSocketTest {

    @Test
    fun `native callbacks delegate to configured lambdas`() {
        val socket = FptnNativeWebSocket()
        var opens = 0
        var failures = 0
        var message = byteArrayOf()
        socket.onOpen = { opens++ }
        socket.onFailure = { failures++ }
        socket.onMessage = { message = it }

        socket.onOpenImpl()
        socket.onMessageImpl(byteArrayOf(1, 2, 3))
        socket.onFailureImpl()

        assertEquals(1, opens)
        assertEquals(1, failures)
        assertContentEquals(byteArrayOf(1, 2, 3), message)
    }

    @Test
    fun `loadOnce records missing native library without throwing`() {
        FptnNativeWebSocket.loadOnce()
        val firstError = FptnNativeWebSocket.loadError
        FptnNativeWebSocket.loadOnce()

        assertFalse(FptnNativeWebSocket.libraryLoaded)
        assertNotNull(firstError)
        assertEquals(firstError, FptnNativeWebSocket.loadError)
    }
}
