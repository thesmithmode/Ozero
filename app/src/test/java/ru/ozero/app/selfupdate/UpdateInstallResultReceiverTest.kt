package ru.ozero.app.selfupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class UpdateInstallResultReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val receiver = UpdateInstallResultReceiver()

    @BeforeEach
    fun setUp() {
        UpdateInstallEventBus.reset()
    }

    @Test
    fun `STATUS_PENDING_USER_ACTION with EXTRA_INTENT emits PendingUserAction`() = runTest {
        val confirmIntent = Intent("android.content.pm.action.CONFIRM_INSTALL")
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 42)
            putExtra(Intent.EXTRA_INTENT, confirmIntent)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val pending = assertIs<UpdateInstallEvent.PendingUserAction>(event)
        assertSame(confirmIntent, pending.intent)
    }

    @Test
    fun `STATUS_PENDING_USER_ACTION without EXTRA_INTENT emits Failure`() = runTest {
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 7)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val failure = assertIs<UpdateInstallEvent.Failure>(event)
        assertEquals(7, failure.sessionId)
        assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, failure.statusCode)
    }

    @Test
    fun `STATUS_SUCCESS emits Success with session id`() = runTest {
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 99)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val success = assertIs<UpdateInstallEvent.Success>(event)
        assertEquals(99, success.sessionId)
    }

    @Test
    fun `STATUS_FAILURE emits Failure with message`() = runTest {
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 5)
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_INVALID_APK")
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val failure = assertIs<UpdateInstallEvent.Failure>(event)
        assertEquals(5, failure.sessionId)
        assertEquals(PackageInstaller.STATUS_FAILURE, failure.statusCode)
        assertEquals("INSTALL_FAILED_INVALID_APK", failure.message)
    }

    @Test
    fun `STATUS_FAILURE_ABORTED emits Failure with correct status code`() = runTest {
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_ABORTED)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 11)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val failure = assertIs<UpdateInstallEvent.Failure>(event)
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, failure.statusCode)
    }

    @Test
    fun `intent with foreign action is ignored`() = runTest {
        val intent = Intent("ru.ozero.app.OTHER_ACTION").apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 1)
        }

        receiver.onReceive(context, intent)

                assertEquals(0, UpdateInstallEventBus.events.replayCache.size)
    }

    private companion object {
        const val EVENT_TIMEOUT_MS = 1_000L
    }
}
