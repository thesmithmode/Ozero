package ru.ozero.singboxprocess

import android.net.DnsResolver
import android.system.ErrnoException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidLocalDnsTransportTest {
    @Test
    fun `DNS exchange waits for delayed callback and applies success before returning`() {
        val callbackReady = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val successApplied = AtomicBoolean(false)
        val returned = AtomicBoolean(false)
        val worker = thread {
            runBlocking {
                awaitDnsCallback(
                    start = { callback ->
                        callbackReady.countDown()
                        thread {
                            releaseCallback.await()
                            callback.onAnswer(byteArrayOf(1), 0)
                        }
                    },
                    onAnswer = { _, _ -> successApplied.set(true) },
                    onErrno = {},
                )
            }
            returned.set(true)
        }

        assertTrue(callbackReady.await(1, TimeUnit.SECONDS))
        assertFalse(returned.get())
        releaseCallback.countDown()
        worker.join(1_000)

        assertTrue(successApplied.get())
        assertTrue(returned.get())
    }

    @Test
    fun `DNS lookup waits for delayed callback and applies addresses before returning`() {
        val releaseCallback = CountDownLatch(1)
        var addresses = ""
        val returned = AtomicBoolean(false)
        val worker = thread {
            runBlocking {
                awaitDnsCallback(
                    start = { callback ->
                        thread {
                            releaseCallback.await()
                            callback.onAnswer(listOf("192.0.2.1"), 0)
                        }
                    },
                    onAnswer = { answer, _ -> addresses = answer.joinToString("\n") },
                    onErrno = {},
                )
            }
            returned.set(true)
        }

        assertFalse(returned.get())
        releaseCallback.countDown()
        worker.join(1_000)

        assertEquals("192.0.2.1", addresses)
        assertTrue(returned.get())
    }

    @Test
    fun `ErrnoException reports errno and completes request`() = runBlocking {
        val expectedErrno = 13

        val errnoCause = mockk<ErrnoException>()
        every { errnoCause.errno } returns expectedErrno

        val error = mockk<DnsResolver.DnsException>()
        every { error.cause } returns errnoCause

        var errno = 0

        awaitDnsCallback<ByteArray>(
            start = { it.onError(error) },
            onAnswer = { _, _ -> },
            onErrno = { errno = it },
        )

        assertEquals(expectedErrno, errno)
    }

    @Test
    fun `non errno DNS exception is propagated`() {
        val error = mockk<DnsResolver.DnsException>()
        every { error.cause } returns null

        assertFailsWith<DnsResolver.DnsException> {
            runBlocking {
                awaitDnsCallback<ByteArray>(
                    start = { it.onError(error) },
                    onAnswer = { _, _ -> },
                    onErrno = {},
                )
            }
        }
    }

    @Test
    fun `cancellation error race completes continuation once`() = runBlocking {
        val completions = AtomicInteger(0)
        val error = mockk<DnsResolver.DnsException>()
        every { error.cause } returns ErrnoException("cancel", 125)

        awaitDnsCallback<ByteArray>(
            start = { callback ->
                callback.onError(error)
                callback.onAnswer(byteArrayOf(2), 0)
            },
            onAnswer = { _, _ -> completions.incrementAndGet() },
            onErrno = { completions.incrementAndGet() },
        )

        assertEquals(1, completions.get())
    }
}
