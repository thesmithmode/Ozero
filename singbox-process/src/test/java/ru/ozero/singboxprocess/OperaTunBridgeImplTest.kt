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
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.ISingboxEngineProcess
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.fromFd(42) } returns f.fd
        every { f.remote.startWithConfig(any(), f.fd, "{}", any()) } answers {
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
            verify(exactly = 1) { f.context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) }
            verify(exactly = 1) { f.context.unbindService(any()) }

            unblock.countDown()
            withTimeout(5_000) { stopped.await() }
            verify(exactly = 1) { f.remote.stopAndWait(any(), 3000L) }
            verify(exactly = 1) { f.fd.close() }
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
