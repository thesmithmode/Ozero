package ru.ozero.app.selfupdate

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SilentPackageInstallerSessionTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var packageInstaller: PackageInstaller

    @Before
    fun setUp() {
        packageManager = mockk()
        every { packageManager.canRequestPackageInstalls() } returns true

        context = mockk(relaxed = true)
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "ru.ozero.app"

        packageInstaller = mockk()
    }

    @Test
    fun `install returns IoError when createSession throws IOException`() = runTest {
        val apk = readyApk()
        every { packageInstaller.createSession(any()) } throws IOException("create fail")

        val result = installer().install(apk)

        val ioError = assertIs<SilentPackageInstaller.Result.IoError>(result)
        assertEquals(-1, ioError.sessionId)
        assertEquals("create fail", ioError.reason)
        verify(exactly = 1) { packageInstaller.createSession(any()) }
        verify(exactly = 0) { packageInstaller.openSession(any()) }
        verify(exactly = 0) { packageInstaller.abandonSession(any()) }
    }

    @Test
    fun `install returns IoError when createSession throws SecurityException`() = runTest {
        val apk = readyApk()
        every { packageInstaller.createSession(any()) } throws SecurityException("create denied")

        val result = installer().install(apk)

        val ioError = assertIs<SilentPackageInstaller.Result.IoError>(result)
        assertEquals(-1, ioError.sessionId)
        assertEquals("create denied", ioError.reason)
        verify(exactly = 1) { packageInstaller.createSession(any()) }
        verify(exactly = 0) { packageInstaller.openSession(any()) }
        verify(exactly = 0) { packageInstaller.abandonSession(any()) }
    }

    @Test
    fun `install abandons session when openSession throws IOException`() = runTest {
        val apk = readyApk()
        every { packageInstaller.createSession(any()) } returns 17
        every { packageInstaller.openSession(17) } throws IOException("open fail")
        every { packageInstaller.abandonSession(17) } just runs

        val result = installer().install(apk)

        val ioError = assertIs<SilentPackageInstaller.Result.IoError>(result)
        assertEquals(17, ioError.sessionId)
        assertEquals("open fail", ioError.reason)
        verify(exactly = 1) { packageInstaller.createSession(any()) }
        verify(exactly = 1) { packageInstaller.openSession(17) }
        verify(exactly = 1) { packageInstaller.abandonSession(17) }
    }

    @Test
    fun `install abandons session when openSession throws SecurityException`() = runTest {
        val apk = readyApk()
        every { packageInstaller.createSession(any()) } returns 19
        every { packageInstaller.openSession(19) } throws SecurityException("open denied")
        every { packageInstaller.abandonSession(19) } just runs

        val result = installer().install(apk)

        val ioError = assertIs<SilentPackageInstaller.Result.IoError>(result)
        assertEquals(19, ioError.sessionId)
        assertEquals("open denied", ioError.reason)
        verify(exactly = 1) { packageInstaller.createSession(any()) }
        verify(exactly = 1) { packageInstaller.openSession(19) }
        verify(exactly = 1) { packageInstaller.abandonSession(19) }
    }

    @Test
    fun `install commits session and returns Submitted on happy path`() = runTest {
        val apk = readyApk("happy.apk")
        val session = mockk<PackageInstaller.Session>(relaxed = true)
        val pendingIntent = mockk<PendingIntent>()
        val sender = mockk<IntentSender>(relaxed = true)
        val intentSlot = slot<Intent>()
        val flagsSlot = slot<Int>()

        every { pendingIntent.intentSender } returns sender
        every { packageInstaller.createSession(any()) } returns 23
        every { packageInstaller.openSession(23) } returns session
        every { session.openWrite("payload.apk", 0, apk.length()) } returns ByteArrayOutputStream()
        every { session.fsync(any()) } just runs
        every { session.commit(sender) } just runs
        every { session.close() } just runs

        mockkStatic(PendingIntent::class)
        try {
            every {
                PendingIntent.getBroadcast(
                    context,
                    23,
                    capture(intentSlot),
                    capture(flagsSlot),
                )
            } returns pendingIntent

            val result = installer().install(
                apkFile = apk,
                sessionName = "payload.apk",
                resultIntentAction = "ru.ozero.app.UPDATE_INSTALL_RESULT",
            )

            val submitted = assertIs<SilentPackageInstaller.Result.Submitted>(result)
            assertEquals(23, submitted.sessionId)
            assertEquals("ru.ozero.app.UPDATE_INSTALL_RESULT", intentSlot.captured.action)
            assertEquals("ru.ozero.app", intentSlot.captured.`package`)
            assertTrue(flagsSlot.captured and PendingIntent.FLAG_UPDATE_CURRENT != 0)
            assertTrue(flagsSlot.captured and PendingIntent.FLAG_MUTABLE != 0)
            verify(exactly = 1) { packageInstaller.createSession(any()) }
            verify(exactly = 1) { packageInstaller.openSession(23) }
            verify(exactly = 0) { packageInstaller.abandonSession(any()) }
            verify(exactly = 1) { session.openWrite("payload.apk", 0, apk.length()) }
            verify(exactly = 1) { session.fsync(any()) }
            verify(exactly = 1) { session.commit(sender) }
        } finally {
            io.mockk.unmockkStatic(PendingIntent::class)
        }
    }

    @Test
    fun `install commits session below Android S without mutable pending intent`() = runTest {
        val apk = readyApk("legacy.apk")
        val session = mockk<PackageInstaller.Session>(relaxed = true)
        val pendingIntent = mockk<PendingIntent>()
        val sender = mockk<IntentSender>(relaxed = true)
        val intentSlot = slot<Intent>()
        val flagsSlot = slot<Int>()

        every { pendingIntent.intentSender } returns sender
        every { packageInstaller.createSession(any()) } returns 24
        every { packageInstaller.openSession(24) } returns session
        every { session.openWrite("payload.apk", 0, apk.length()) } returns ByteArrayOutputStream()
        every { session.fsync(any()) } just runs
        every { session.commit(sender) } just runs
        every { session.close() } just runs

        mockkStatic(PendingIntent::class)
        try {
            every {
                PendingIntent.getBroadcast(
                    context,
                    24,
                    capture(intentSlot),
                    capture(flagsSlot),
                )
            } returns pendingIntent

            val result = installer(sdkInt = Build.VERSION_CODES.N).install(
                apkFile = apk,
                sessionName = "payload.apk",
                resultIntentAction = "ru.ozero.app.UPDATE_INSTALL_RESULT",
            )

            val submitted = assertIs<SilentPackageInstaller.Result.Submitted>(result)
            assertEquals(24, submitted.sessionId)
            assertEquals("ru.ozero.app.UPDATE_INSTALL_RESULT", intentSlot.captured.action)
            assertEquals("ru.ozero.app", intentSlot.captured.`package`)
            assertTrue(flagsSlot.captured and PendingIntent.FLAG_UPDATE_CURRENT != 0)
            assertTrue(flagsSlot.captured and PendingIntent.FLAG_MUTABLE == 0)
            verify(exactly = 1) { packageInstaller.createSession(any()) }
            verify(exactly = 1) { packageInstaller.openSession(24) }
            verify(exactly = 0) { packageInstaller.abandonSession(any()) }
            verify(exactly = 1) { session.openWrite("payload.apk", 0, apk.length()) }
            verify(exactly = 1) { session.fsync(any()) }
            verify(exactly = 1) { session.commit(sender) }
        } finally {
            io.mockk.unmockkStatic(PendingIntent::class)
        }
    }

    private fun installer(sdkInt: Int = Build.VERSION_CODES.S): SilentPackageInstaller =
        SilentPackageInstaller(
            context = context,
            installer = packageInstaller,
            sdkInt = { sdkInt },
        )

    private fun readyApk(name: String = "payload.apk"): File =
        File.createTempFile(name.removeSuffix(".apk"), ".apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
}
