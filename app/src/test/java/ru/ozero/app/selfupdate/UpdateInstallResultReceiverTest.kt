package ru.ozero.app.selfupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateInstallResultReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val receiver = UpdateInstallResultReceiver()

    @Before
    fun setUp() {
        UpdateInstallEventBus.reset()
    }

    @Test
    fun pendingUserAction_withExtraIntent_emitsPendingUserAction() = runTest {
        val confirmIntent = Intent("android.content.pm.action.CONFIRM_INSTALL")
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 42)
            putExtra(Intent.EXTRA_INTENT, confirmIntent)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val pending = assertIs<UpdateInstallEvent.PendingUserAction>(event)
        assertEquals(confirmIntent.action, pending.intent.action)
    }

    @Test
    fun pendingUserAction_withoutExtraIntent_emitsFailure() = runTest {
        UpdateInstallEventBus.reset()
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
    fun statusSuccess_emitsSuccessWithSessionId() = runTest {
        UpdateInstallEventBus.reset()
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
    fun statusFailure_emitsFailureWithMessage() = runTest {
        UpdateInstallEventBus.reset()
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
    fun statusFailureAborted_emitsFailureWithCorrectStatusCode() = runTest {
        UpdateInstallEventBus.reset()
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
    fun foreignAction_isIgnored() = runTest {
        UpdateInstallEventBus.reset()
        val intent = Intent("ru.ozero.app.OTHER_ACTION").apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 1)
        }

        receiver.onReceive(context, intent)

        assertEquals(0, UpdateInstallEventBus.events.replayCache.size)
    }

    @Test
    fun statusFailureBlocked_emitsFailureWithStatusCode() = runTest {
        UpdateInstallEventBus.reset()
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_BLOCKED)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 22)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val failure = assertIs<UpdateInstallEvent.Failure>(event)
        assertEquals(22, failure.sessionId)
        assertEquals(PackageInstaller.STATUS_FAILURE_BLOCKED, failure.statusCode)
    }

    @Test
    fun statusFailureConflict_emitsFailureWithStatusCode() = runTest {
        UpdateInstallEventBus.reset()
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_CONFLICT)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 23)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val failure = assertIs<UpdateInstallEvent.Failure>(event)
        assertEquals(PackageInstaller.STATUS_FAILURE_CONFLICT, failure.statusCode)
    }

    @Test
    fun statusFailureStorage_emitsFailureWithStatusCode() = runTest {
        UpdateInstallEventBus.reset()
        val intent = Intent(UpdateInstallResultReceiver.ACTION).apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_STORAGE)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, 24)
        }

        receiver.onReceive(context, intent)

        val event = withTimeout(EVENT_TIMEOUT_MS) { UpdateInstallEventBus.events.first() }
        val failure = assertIs<UpdateInstallEvent.Failure>(event)
        assertEquals(PackageInstaller.STATUS_FAILURE_STORAGE, failure.statusCode)
    }

    @Test
    fun nullActionIntent_isIgnored() = runTest {
        UpdateInstallEventBus.reset()
        val intent = Intent().apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
        }

        receiver.onReceive(context, intent)

        assertEquals(0, UpdateInstallEventBus.events.replayCache.size)
    }

    private companion object {
        const val EVENT_TIMEOUT_MS = 1_000L
    }
}
