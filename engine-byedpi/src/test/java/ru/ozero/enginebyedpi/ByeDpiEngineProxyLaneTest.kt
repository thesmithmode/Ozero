package ru.ozero.enginebyedpi

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ByeDpiEngineProxyLaneTest {
    @Test
    fun `start rotates proxy lane when previous native job keeps proxy dispatcher occupied`() =
        assertTimeoutPreemptively(Duration.ofSeconds(15)) {
            val firstNativeEntered = CountDownLatch(1)
            val firstNativeExit = CountDownLatch(1)
            val recoveryStartEntered = CountDownLatch(1)
            val retryStartEntered = CountDownLatch(1)
            val calls = AtomicInteger(0)
            val blockingProxy: ByeDpiProxy = mockk(relaxed = true)
            mockkObject(ByeDpiProxy.Companion)
            every { ByeDpiProxy.loadOnce() } just runs
            every { ByeDpiProxy.libraryLoaded } returns true
            every { blockingProxy.startProxy(any()) } answers {
                when (calls.getAndIncrement()) {
                    0 -> {
                        firstNativeEntered.countDown()
                        firstNativeExit.await(10, TimeUnit.SECONDS)
                        0
                    }
                    1 -> {
                        recoveryStartEntered.countDown()
                        ByeDpiEngine.JNI_GUARD_BUSY
                    }
                    else -> {
                        retryStartEntered.countDown()
                        0
                    }
                }
            }
            every { blockingProxy.stopProxy() } returns 0
            every { blockingProxy.forceClose() } returns 0
            every { blockingProxy.emergencyReset() } returns 1
            val eng = ByeDpiEngine(
                blockingProxy,
                socksProbe = { _, port, _ ->
                    if (port == 1080) {
                        assertTrue(firstNativeEntered.await(5, TimeUnit.SECONDS))
                    } else if (port == 1081) {
                        assertTrue(retryStartEntered.await(5, TimeUnit.SECONDS))
                    }
                    1L
                },
            )
            runBlocking {
                try {
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1080)))
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1081)))
                } finally {
                    firstNativeExit.countDown()
                    unmockkObject(ByeDpiProxy.Companion)
                }
            }
            assertTrue(recoveryStartEntered.await(5, TimeUnit.SECONDS))
            assertTrue(retryStartEntered.await(5, TimeUnit.SECONDS))
            verify(exactly = 1) { blockingProxy.emergencyReset() }
            verify(exactly = 3) { blockingProxy.startProxy(any()) }
        }

    @Test
    fun `start after wedged stop rotates proxy lane even when job ref was cleared`() =
        assertTimeoutPreemptively(Duration.ofSeconds(15)) {
            val firstNativeEntered = CountDownLatch(1)
            val firstNativeExit = CountDownLatch(1)
            val recoveryStartEntered = CountDownLatch(1)
            val retryStartEntered = CountDownLatch(1)
            val calls = AtomicInteger(0)
            val blockingProxy: ByeDpiProxy = mockk(relaxed = true)
            mockkObject(ByeDpiProxy.Companion)
            every { ByeDpiProxy.loadOnce() } just runs
            every { ByeDpiProxy.libraryLoaded } returns true
            every { blockingProxy.startProxy(any()) } answers {
                when (calls.getAndIncrement()) {
                    0 -> {
                        firstNativeEntered.countDown()
                        firstNativeExit.await(10, TimeUnit.SECONDS)
                        0
                    }
                    1 -> {
                        recoveryStartEntered.countDown()
                        ByeDpiEngine.JNI_GUARD_BUSY
                    }
                    else -> {
                        retryStartEntered.countDown()
                        0
                    }
                }
            }
            every { blockingProxy.stopProxy() } returns 0
            every { blockingProxy.forceClose() } returns 0
            every { blockingProxy.emergencyReset() } returns 1
            val eng = ByeDpiEngine(
                blockingProxy,
                socksProbe = { _, port, _ ->
                    if (port == 1080) {
                        assertTrue(firstNativeEntered.await(5, TimeUnit.SECONDS))
                    } else if (port == 1081) {
                        assertTrue(retryStartEntered.await(5, TimeUnit.SECONDS))
                    }
                    1L
                },
            )
            runBlocking {
                try {
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1080)))
                    eng.stop()
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1081)))
                } finally {
                    firstNativeExit.countDown()
                    unmockkObject(ByeDpiProxy.Companion)
                }
            }
            assertTrue(recoveryStartEntered.await(5, TimeUnit.SECONDS))
            assertTrue(retryStartEntered.await(5, TimeUnit.SECONDS))
            verify(exactly = 1) { blockingProxy.emergencyReset() }
            verify(exactly = 3) { blockingProxy.startProxy(any()) }
        }
}
