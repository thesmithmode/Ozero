package ru.ozero.singboxprocess

import android.net.DnsResolver
import android.os.CancellationSignal
import android.system.ErrnoException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

        assertTrue(callbackReady.await(5, TimeUnit.SECONDS))
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
        val error = mockk<DnsResolver.DnsException>()
        every { error.cause } returns mockk<Throwable>()

        var errno = 0

        awaitDnsCallback<ByteArray>(
            start = { it.onError(error) },
            onAnswer = { _, _ -> },
            onErrno = { errno = it },
            errnoExtractor = { expectedErrno },
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
                    errnoExtractor = { null },
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

    @Test
    fun `cancellation signal releases every pending DNS wait`() = runBlocking {
        val started = CountDownLatch(100)
        val cancellations = ConcurrentLinkedQueue<() -> Unit>()
        val requests = List(100) {
            val cancellation = CancellationSignal()
            async(Dispatchers.Default) {
                assertFailsWith<CancellationException> {
                    awaitDnsCallback<ByteArray>(
                        start = {},
                        onAnswer = { _, _ -> },
                        onErrno = {},
                        cancellation = cancellation,
                        registerCancellation = { cancel ->
                            cancellations += cancel
                            started.countDown()
                        },
                    )
                }
                assertTrue(cancellation.isCanceled)
            }
        }

        assertTrue(started.await(5, TimeUnit.SECONDS))
        cancellations.forEach { it() }
        requests.awaitAll()
    }

    @Test
    fun `callback after cancellation is ignored`() = runBlocking {
        lateinit var callback: DnsResolver.Callback<ByteArray>
        lateinit var cancel: () -> Unit
        val answers = AtomicInteger(0)
        val registered = CountDownLatch(2)
        val request = async(Dispatchers.Default) {
            assertFailsWith<CancellationException> {
                awaitDnsCallback<ByteArray>(
                    start = {
                        callback = it
                        registered.countDown()
                    },
                    onAnswer = { _, _ -> answers.incrementAndGet() },
                    onErrno = {},
                    cancellation = CancellationSignal(),
                    registerCancellation = {
                        cancel = it
                        registered.countDown()
                    },
                )
            }
        }

        assertTrue(registered.await(5, TimeUnit.SECONDS))
        cancel()
        callback.onAnswer(byteArrayOf(1), 0)
        request.await()

        assertEquals(0, answers.get())
    }

    @Test
    fun `answer and cancellation race completes once`() = runBlocking {
        lateinit var callback: DnsResolver.Callback<ByteArray>
        lateinit var cancel: () -> Unit
        val answers = AtomicInteger(0)
        val registered = CountDownLatch(2)
        val request = async(Dispatchers.Default) {
            runCatching {
                awaitDnsCallback<ByteArray>(
                    start = {
                        callback = it
                        registered.countDown()
                    },
                    onAnswer = { _, _ -> answers.incrementAndGet() },
                    onErrno = {},
                    cancellation = CancellationSignal(),
                    registerCancellation = {
                        cancel = it
                        registered.countDown()
                    },
                )
            }
        }

        assertTrue(registered.await(5, TimeUnit.SECONDS))
        val ready = CountDownLatch(1)
        val finished = CountDownLatch(2)
        thread {
            ready.await()
            cancel()
            finished.countDown()
        }
        thread {
            ready.await()
            callback.onAnswer(byteArrayOf(1), 0)
            finished.countDown()
        }
        ready.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        request.await()

        assertTrue(answers.get() in 0..1)
    }

    @Test
    fun `error and cancellation race completes once`() = runBlocking {
        lateinit var callback: DnsResolver.Callback<ByteArray>
        lateinit var cancel: () -> Unit
        val errnoCalls = AtomicInteger(0)
        val error = mockk<DnsResolver.DnsException>()
        every { error.cause } returns ErrnoException("race", 125)
        val registered = CountDownLatch(2)
        val request = async(Dispatchers.Default) {
            runCatching {
                awaitDnsCallback<ByteArray>(
                    start = {
                        callback = it
                        registered.countDown()
                    },
                    onAnswer = { _, _ -> },
                    onErrno = { errnoCalls.incrementAndGet() },
                    cancellation = CancellationSignal(),
                    registerCancellation = {
                        cancel = it
                        registered.countDown()
                    },
                )
            }
        }

        assertTrue(registered.await(5, TimeUnit.SECONDS))
        val ready = CountDownLatch(1)
        val finished = CountDownLatch(2)
        thread {
            ready.await()
            cancel()
            finished.countDown()
        }
        thread {
            ready.await()
            callback.onError(error)
            finished.countDown()
        }
        ready.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        request.await()

        assertTrue(errnoCalls.get() in 0..1)
    }
}
