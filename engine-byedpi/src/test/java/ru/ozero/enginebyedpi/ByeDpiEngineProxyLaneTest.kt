package ru.ozero.enginebyedpi

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ResourceLock("ByeDpiProxy.Companion")
class ByeDpiEngineProxyLaneTest {
    @Test
    fun `start rotates proxy lane when previous native job keeps proxy dispatcher occupied`() {
        val firstNativeEntered = CountDownLatch(1)
        val firstNativeExit = CountDownLatch(1)
        val recoveryStartEntered = CountDownLatch(1)
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
                else -> error("unexpected startProxy call")
            }
        }
        every { blockingProxy.stopProxy() } returns 0
        every { blockingProxy.forceClose() } returns 0
        val eng = ByeDpiEngine(
            blockingProxy,
            socksProbe = { _, port, _ ->
                if (port == 1080) {
                    assertTrue(firstNativeEntered.await(5, TimeUnit.SECONDS))
                    1L
                } else {
                    throw IOException("refused")
                }
            },
            readyTotalTimeoutMs = 300,
        )
        runBlocking {
            try {
                assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1080)))
                assertIs<StartResult.Failure>(eng.start(EngineConfig.ByeDpi(socksPort = 1081)))
            } finally {
                firstNativeExit.countDown()
                unmockkObject(ByeDpiProxy.Companion)
            }
        }
        assertTrue(recoveryStartEntered.await(5, TimeUnit.SECONDS))
        verify(exactly = 2) { blockingProxy.startProxy(any()) }
    }

    @Test
    fun `start after wedged stop rotates proxy lane even when job ref was cleared`() {
        val firstNativeEntered = CountDownLatch(1)
        val firstNativeExit = CountDownLatch(1)
        val recoveryStartEntered = CountDownLatch(1)
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
                else -> error("unexpected startProxy call")
            }
        }
        every { blockingProxy.stopProxy() } returns 0
        every { blockingProxy.forceClose() } returns 0
        val eng = ByeDpiEngine(
            blockingProxy,
            socksProbe = { _, port, _ ->
                if (port == 1080) {
                    assertTrue(firstNativeEntered.await(5, TimeUnit.SECONDS))
                    1L
                } else {
                    throw IOException("refused")
                }
            },
            readyTotalTimeoutMs = 300,
        )
        runBlocking {
            try {
                assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1080)))
                eng.stop()
                assertIs<StartResult.Failure>(eng.start(EngineConfig.ByeDpi(socksPort = 1081)))
            } finally {
                firstNativeExit.countDown()
                unmockkObject(ByeDpiProxy.Companion)
            }
        }
        assertTrue(recoveryStartEntered.await(5, TimeUnit.SECONDS))
        verify(exactly = 2) { blockingProxy.startProxy(any()) }
    }
}
