package ru.ozero.singboxprocess

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.ISingboxEngineProcess
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperaTunBridgeImplTest {
    private class Fixture {
        val context = mockk<Context>(relaxed = true)
        val remote = mockk<ISingboxEngineProcess>(relaxed = true)
        val binder = mockk<IBinder>()
        val name = mockk<ComponentName>()
        val fd = mockk<ParcelFileDescriptor>(relaxed = true)

        init {
            every { binder.queryLocalInterface(any()) } returns remote
            every { remote.runtimeRunning() } returns true
            every { remote.stopAndWait(any(), any()) } returns true
            every { context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } answers {
                secondArg<ServiceConnection>().onServiceConnected(name, binder)
                true
            }
        }
    }

    @Test
    fun `native session uses duplicated fd and owner scoped stop`() = runTest {
        val f = Fixture()
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        try {
            val bridge = OperaTunBridgeImpl(f.context)
            assertFalse(bridge.isRunning())
            assertTrue(bridge.start(42, "{}"))
            assertTrue(bridge.isRunning())
            verify(exactly = 1) { f.remote.startWithConfig(any(), f.fd, "{}", any()) }
            verify(exactly = 1) { f.fd.close() }
            every { f.remote.runtimeRunning() } throws IllegalStateException("disconnected")
            assertFalse(bridge.isRunning())
            bridge.stop()
            bridge.stop()
            verify(exactly = 1) { f.remote.stopAndWait(any(), 3000L) }
            verify(exactly = 1) { f.context.unbindService(any()) }
            assertFalse(bridge.isRunning())
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `rejected binding never starts the native runtime`() = runTest {
        val f = Fixture()
        every { f.context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } returns false
        val bridge = OperaTunBridgeImpl(f.context)
        assertFalse(bridge.start(42, "{}"))
        verify(exactly = 0) { f.remote.startWithConfig(any(), any(), any(), any()) }
        assertFalse(bridge.isRunning())
    }

    @Test
    fun `null binding and timeout release service connection`() = runTest {
        val f = Fixture()
        every { f.context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } answers {
            secondArg<ServiceConnection>().onNullBinding(f.name)
            true
        }
        val bridge = OperaTunBridgeImpl(f.context)
        assertFalse(bridge.start(42, "{}"))
        every { f.context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } returns true
        assertFalse(bridge.start(42, "{}"))
        verify(exactly = 2) { f.context.unbindService(any()) }
    }

    @Test
    fun `blocked startup returns failure without allowing a new session before late cleanup`() = runBlocking {
        val f = Fixture()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val stopped = CompletableDeferred<Unit>()
        val owner = slot<Long>()
        val connection = slot<ServiceConnection>()
        val nextFd = mockk<ParcelFileDescriptor>(relaxed = true)
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        every { ParcelFileDescriptor.fromFd(43) } returns nextFd
        every { f.remote.startWithConfig(capture(owner), f.fd, "{}", any()) } answers {
            entered.countDown()
            check(unblock.await(5, TimeUnit.SECONDS))
        }
        every { f.remote.stopAndWait(any(), any()) } answers {
            stopped.complete(Unit)
            true
        }
        try {
            val bridge = OperaTunBridgeImpl(f.context, startRpcTimeoutMs = 100, abandonedCleanupTimeoutMs = 100)
            assertFalse(bridge.start(42, "{}"))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(bridge.isRunning())
            assertFalse(bridge.start(42, "{}"))
            verify(exactly = 1) { f.context.bindService(any<Intent>(), capture(connection), any<Int>()) }
            verify(exactly = 1) { f.context.unbindService(connection.captured) }
            verify(exactly = 0) { f.fd.close() }

            unblock.countDown()
            withTimeout(5_000) { stopped.await() }
            withTimeout(5_000) {
                while (!bridge.start(43, "{}")) delay(10)
            }
            verify(exactly = 1) { f.remote.stopAndWait(owner.captured, 3000L) }
            verify(exactly = 1) { f.remote.stopAndWait(any(), any()) }
            verify(exactly = 1) { f.fd.close() }
            verify(exactly = 1) { f.context.unbindService(connection.captured) }
            verify(exactly = 1) { f.context.unbindService(any()) }
            bridge.stop()
        } finally {
            unblock.countDown()
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `blocked readiness releases its owner and permits a new start before the old call returns`() = runBlocking {
        val f = Fixture()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val calls = AtomicInteger()
        val owner = slot<Long>()
        val nextFd = mockk<ParcelFileDescriptor>(relaxed = true)
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        every { ParcelFileDescriptor.fromFd(43) } returns nextFd
        every { f.remote.startWithConfig(capture(owner), f.fd, "{}", any()) } returns Unit
        every { f.remote.runtimeRunning() } answers {
            if (calls.incrementAndGet() == 1) {
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS))
                returned.countDown()
            }
            true
        }
        try {
            val bridge = OperaTunBridgeImpl(
                f.context,
                startRpcTimeoutMs = 3_000,
                readinessRpcTimeoutMs = 500,
            )
            assertFalse(bridge.start(42, "{}"))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            verify(exactly = 1) { f.fd.close() }
            verify(exactly = 1) { f.remote.stopAndWait(owner.captured, 3000L) }
            verify(exactly = 1) { f.context.unbindService(any()) }

            assertTrue(bridge.start(43, "{}"))
            assertTrue(bridge.isRunning())
            assertTrue(returned.count == 1L)
            unblock.countDown()
            assertTrue(returned.await(1, TimeUnit.SECONDS))
            verify(exactly = 1) { f.remote.stopAndWait(any(), any()) }
            verify(exactly = 1) { f.context.unbindService(any()) }
            bridge.stop()
        } finally {
            unblock.countDown()
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `stalled health checks share one query and do not prevent stop`() = runBlocking {
        val f = Fixture()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val calls = AtomicInteger()
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        every { f.remote.runtimeRunning() } answers {
            if (calls.incrementAndGet() == 1) {
                true
            } else {
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS))
                returned.countDown()
                true
            }
        }
        try {
            val bridge = OperaTunBridgeImpl(f.context, readinessRpcTimeoutMs = 500)
            assertTrue(bridge.start(42, "{}"))
            assertFalse(bridge.isRunning())
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(bridge.isRunning())
            assertTrue(calls.get() == 2)

            bridge.stop()
            verify(exactly = 1) { f.remote.stopAndWait(any(), 3000L) }
            verify(exactly = 1) { f.context.unbindService(any()) }
            assertFalse(bridge.isRunning())
            unblock.countDown()
            assertTrue(returned.await(1, TimeUnit.SECONDS))
            assertFalse(bridge.isRunning())
        } finally {
            unblock.countDown()
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `stop does not wait for an in-flight health query and ignores its late result`() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>()
        val unblock = CountDownLatch(1)
        val calls = AtomicInteger()
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        every { f.remote.runtimeRunning() } answers {
            if (calls.incrementAndGet() > 1) {
                entered.complete(Unit)
                check(unblock.await(5, TimeUnit.SECONDS))
            }
            true
        }
        try {
            val bridge = OperaTunBridgeImpl(f.context, readinessRpcTimeoutMs = 4_000)
            assertTrue(bridge.start(42, "{}"))
            val health = async { bridge.isRunning() }
            withTimeout(2_000) { entered.await() }

            withTimeout(2_000) { bridge.stop() }
            verify(exactly = 1) { f.remote.stopAndWait(any(), 3000L) }
            verify(exactly = 1) { f.context.unbindService(any()) }
            assertFalse(bridge.isRunning())
            unblock.countDown()
            assertFalse(withTimeout(2_000) { health.await() })
        } finally {
            unblock.countDown()
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `failed native readiness stops owned session and unbinds`() = runTest {
        val f = Fixture()
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        every { f.remote.runtimeRunning() } returns false
        try {
            val bridge = OperaTunBridgeImpl(f.context)
            assertFalse(bridge.start(42, "{}"))
            verify(exactly = 1) { f.remote.stopAndWait(any(), 3000L) }
            verify(exactly = 1) { f.context.unbindService(any()) }
            verify(exactly = 1) { f.fd.close() }
            assertFalse(bridge.isRunning())
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }
}
