package ru.ozero.commonvpn

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TunnelStatsLoggerCoverageTest {

    @AfterEach
    fun tearDown() {
        unmockkObject(TunInterfaceStats)
        unmockkObject(UidTrafficStats)
    }

    @Test
    fun `start reads iface stats updates controller and notifies`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val logger = logger(
            scope = scope,
            controller = controller,
            notification = notification,
            iface = "tun9",
        )
        mockkObject(TunInterfaceStats)
        mockkObject(UidTrafficStats)
        every { TunInterfaceStats.readTunStats("tun9") } returnsMany listOf(
            TunInterfaceStats.Snapshot(rxBytes = 101, txBytes = 202),
            TunInterfaceStats.Snapshot(rxBytes = 151, txBytes = 282),
        )
        every { UidTrafficStats.read() } returns UidTrafficStats.Snapshot(rxBytes = 1, txBytes = 2)

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertEquals(0, controller.stats.value?.rxBytes)
            assertEquals(0, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertEquals(50, controller.stats.value?.rxBytes)
            assertEquals(80, controller.stats.value?.txBytes)
            verify(exactly = 2) { notification.notifyStats(any()) }
            verify(exactly = 0) { UidTrafficStats.read() }
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start falls back to uid stats when iface is absent or unreadable`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val logger = logger(
            scope = scope,
            controller = controller,
            notification = notification,
            iface = null,
        )
        mockkObject(UidTrafficStats)
        every { UidTrafficStats.read() } returnsMany listOf(
            UidTrafficStats.Snapshot(rxBytes = 303, txBytes = 404),
            UidTrafficStats.Snapshot(rxBytes = 333, txBytes = 464),
        )

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertEquals(0, controller.stats.value?.rxBytes)
            assertEquals(0, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertEquals(30, controller.stats.value?.rxBytes)
            assertEquals(60, controller.stats.value?.txBytes)
            verify(exactly = 2) { notification.notifyStats(any()) }
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start falls back to uid stats when iface read returns null`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val logger = logger(
            scope = scope,
            controller = controller,
            notification = notification,
            iface = "tun0",
        )
        mockkObject(TunInterfaceStats)
        mockkObject(UidTrafficStats)
        every { TunInterfaceStats.readTunStats("tun0") } returns null
        every { UidTrafficStats.read() } returnsMany listOf(
            UidTrafficStats.Snapshot(rxBytes = 505, txBytes = 606),
            UidTrafficStats.Snapshot(rxBytes = 555, txBytes = 676),
        )

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertEquals(0, controller.stats.value?.rxBytes)
            assertEquals(0, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertEquals(50, controller.stats.value?.rxBytes)
            assertEquals(70, controller.stats.value?.txBytes)
            verify(exactly = 2) { TunInterfaceStats.readTunStats("tun0") }
            verify(exactly = 2) { UidTrafficStats.read() }
            verify(exactly = 2) { notification.notifyStats(any()) }
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start skips update and notification when both stats sources are absent`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val logger = logger(
            scope = scope,
            controller = controller,
            notification = notification,
            iface = "tun0",
        )
        mockkObject(TunInterfaceStats)
        mockkObject(UidTrafficStats)
        every { TunInterfaceStats.readTunStats("tun0") } returns null
        every { UidTrafficStats.read() } returns null

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS * 2)
            runCurrent()

            assertNull(controller.stats.value)
            verify(exactly = 2) { TunInterfaceStats.readTunStats("tun0") }
            verify(exactly = 2) { UidTrafficStats.read() }
            verify(exactly = 0) { notification.notifyStats(any()) }
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start rebases session baseline when raw counters move backwards`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val logger = logger(scope = scope, controller = controller, iface = "tun0")
        mockkObject(TunInterfaceStats)
        every { TunInterfaceStats.readTunStats("tun0") } returnsMany listOf(
            TunInterfaceStats.Snapshot(rxBytes = 1_000, txBytes = 2_000),
            TunInterfaceStats.Snapshot(rxBytes = 1_100, txBytes = 2_150),
            TunInterfaceStats.Snapshot(rxBytes = 40, txBytes = 70),
            TunInterfaceStats.Snapshot(rxBytes = 90, txBytes = 130),
        )

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(0, controller.stats.value?.rxBytes)
            assertEquals(0, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(100, controller.stats.value?.rxBytes)
            assertEquals(150, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(100, controller.stats.value?.rxBytes)
            assertEquals(150, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(150, controller.stats.value?.rxBytes)
            assertEquals(210, controller.stats.value?.txBytes)
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start rebases session baseline when stats source changes`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val logger = logger(scope = scope, controller = controller, iface = "tun0")
        mockkObject(TunInterfaceStats)
        mockkObject(UidTrafficStats)
        every { TunInterfaceStats.readTunStats("tun0") } returnsMany listOf(
            TunInterfaceStats.Snapshot(rxBytes = 1_000, txBytes = 2_000),
            TunInterfaceStats.Snapshot(rxBytes = 1_100, txBytes = 2_150),
            null,
            null,
        )
        every { UidTrafficStats.read() } returnsMany listOf(
            UidTrafficStats.Snapshot(rxBytes = 20, txBytes = 30),
            UidTrafficStats.Snapshot(rxBytes = 35, txBytes = 55),
        )

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(0, controller.stats.value?.rxBytes)
            assertEquals(0, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(100, controller.stats.value?.rxBytes)
            assertEquals(150, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(100, controller.stats.value?.rxBytes)
            assertEquals(150, controller.stats.value?.txBytes)
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()
            assertEquals(115, controller.stats.value?.rxBytes)
            assertEquals(175, controller.stats.value?.txBytes)
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start cancels previous stats job before replacing it`() = runTest {
        val scope = backgroundScope
        val ref = AtomicReference<Job?>()
        val logger = logger(scope = scope, statsJobRef = ref)

        try {
            logger.start()
            val first = ref.get()
            logger.start()
            val second = ref.get()

            assertFalse(first === second)
            assertFalse(first?.isActive == true)
            assertTrue(second?.isActive == true)
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `start catches non cancellation stats source exceptions`() = runTest {
        val scope = backgroundScope
        val controller = connectedController()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val ref = AtomicReference<Job?>()
        val logger = logger(
            scope = scope,
            controller = controller,
            notification = notification,
            statsJobRef = ref,
            iface = "tun0",
        )
        mockkObject(TunInterfaceStats)
        every { TunInterfaceStats.readTunStats("tun0") } throws IllegalStateException("stats failed")

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertNull(controller.stats.value)
            assertFalse(ref.get()?.isActive == true)
            verify(exactly = 0) { notification.notifyStats(any()) }
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `stop signal exits before stats read or notification`() = runTest {
        val scope = backgroundScope
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val stopSignal = AtomicBoolean(true)
        val controller = connectedController()
        val logger = logger(
            scope = scope,
            controller = controller,
            notification = notification,
            stopSignal = stopSignal,
            iface = "tun0",
        )
        mockkObject(TunInterfaceStats)
        every { TunInterfaceStats.readTunStats(any()) } returns TunInterfaceStats.Snapshot(1, 2)

        try {
            logger.start()
            advanceTimeBy(TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
            runCurrent()

            assertNull(controller.stats.value)
            verify(exactly = 0) { notification.notifyStats(any()) }
            verify(exactly = 0) { TunInterfaceStats.readTunStats(any()) }
        } finally {
            stopLogger(logger)
        }
    }

    @Test
    fun `cancel clears and cancels active stats job`() = runTest {
        val scope = backgroundScope
        val ref = AtomicReference<Job?>()
        val logger = logger(scope = scope, statsJobRef = ref)

        logger.start()
        val job = ref.get()
        assertTrue(job?.isActive == true)

        logger.cancel()
        runCurrent()

        assertNull(ref.get())
        assertFalse(job?.isActive == true)
    }

    private fun TestScope.stopLogger(logger: TunnelStatsLogger) {
        logger.cancel()
        runCurrent()
    }

    private fun logger(
        scope: CoroutineScope,
        controller: TunnelController = connectedController(),
        notification: OzeroNotificationFactory = mockk(relaxed = true),
        iface: String? = null,
        stopSignal: AtomicBoolean = AtomicBoolean(false),
        statsJobRef: AtomicReference<Job?> = AtomicReference(null),
    ): TunnelStatsLogger = TunnelStatsLogger(
        scope = scope,
        tunnelController = controller,
        notificationFactory = notification,
        tunIfaceNameRef = AtomicReference(iface),
        stopSignal = stopSignal,
        statsJobRef = statsJobRef,
        engineExtras = { "extra" },
    )

    private fun connectedController(): TunnelController = TunnelController().apply {
        onProbing(ru.ozero.enginescore.EngineId.BYEDPI)
        onConnecting(ru.ozero.enginescore.EngineId.BYEDPI)
        onEngineStarted(ru.ozero.enginescore.EngineId.BYEDPI, socksPort = 1080)
    }
}
