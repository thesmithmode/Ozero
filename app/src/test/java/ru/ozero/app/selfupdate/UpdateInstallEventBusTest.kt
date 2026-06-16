package ru.ozero.app.selfupdate

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateInstallEventBusTest {

    @Before
    fun setUp() {
        UpdateInstallEventBus.reset()
    }

    @Test
    fun emit_replaysLatestEventToNewSubscriber() = runTest {
        UpdateInstallEventBus.emit(UpdateInstallEvent.Success(sessionId = 10))
        UpdateInstallEventBus.emit(
            UpdateInstallEvent.Failure(
                sessionId = 11,
                statusCode = -1,
                message = "failed",
            ),
        )

        val event = UpdateInstallEventBus.events.first()

        assertEquals(
            UpdateInstallEvent.Failure(sessionId = 11, statusCode = -1, message = "failed"),
            event,
        )
    }

    @Test
    fun reset_clearsReplayCache() {
        UpdateInstallEventBus.emit(UpdateInstallEvent.Success(sessionId = 20))

        UpdateInstallEventBus.reset()

        assertTrue(UpdateInstallEventBus.events.replayCache.isEmpty())
    }

    @Test
    fun emit_replacesReplayCacheWithLatestEvent() = runTest {
        UpdateInstallEventBus.emit(UpdateInstallEvent.Success(sessionId = 30))
        UpdateInstallEventBus.emit(UpdateInstallEvent.Success(sessionId = 31))

        assertEquals(listOf(UpdateInstallEvent.Success(sessionId = 31)), UpdateInstallEventBus.events.replayCache)
    }
}
