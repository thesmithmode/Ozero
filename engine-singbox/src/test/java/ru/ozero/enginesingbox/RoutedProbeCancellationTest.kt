package ru.ozero.enginesingbox

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.Socket
import java.net.SocketException
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoutedProbeCancellationTest {
    @Test
    fun `cancellation closes active probe socket`() {
        val socket = mockk<Socket>()
        every { socket.close() } returns Unit
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val cancellation = RoutedProbeCancellation()
        val worker = thread {
            cancellation.withSocket(socket) {
                entered.countDown()
                release.await()
            }
        }

        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        cancellation.cancel()
        release.countDown()
        worker.join(5_000)

        verify(atLeast = 1) { socket.close() }
        assertTrue(cancellation.isCancelled())
    }

    @Test
    fun `cancelled probe rejects new sockets`() {
        val socket = mockk<Socket>()
        every { socket.close() } returns Unit
        val cancellation = RoutedProbeCancellation().also { it.cancel() }

        assertFailsWith<SocketException> {
            cancellation.withSocket(socket) { }
        }

        verify(exactly = 1) { socket.close() }
    }
}
