package ru.ozero.app.logging

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BootDiagnosticsDumpExitReasonsTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Before
    fun setUp() {
        LogFileStore.resetForTest()
    }

    @After
    fun tearDown() {
        LogFileStore.resetForTest()
    }

    @Test
    fun `dumpExitReasons ignores empty exit reason list`() {
        val context = mockContext(tempDir.newFolder("empty"))
        val am = mockk<ActivityManager>()
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns am
        every { am.getHistoricalProcessExitReasons("ru.ozero.app", 0, 10) } returns emptyList()

        BootFileLogger.init(context)
        BootDiagnostics.dumpExitReasons(context)

        val log = BootFileLogger.read()
        assertFalse(log.contains("exitReasons count="))
    }

    @Test
    fun `dumpExitReasons logs crash trace tombstone and signaled branch`() {
        val filesDir = tempDir.newFolder("crash")
        val context = mockContext(filesDir)
        val am = mockk<ActivityManager>()
        val nativeBytes = byteArrayOf(0x01, 0x02, 0x03, 0x41, 0x42, 0x43)
        val crashNative = exitInfo(
            reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
            pid = 111,
            status = 9,
            importance = 100,
            timestamp = 2222L,
            description = "native",
            trace = ByteArrayInputStream(nativeBytes),
        )
        val crashJvm = exitInfo(
            reason = ApplicationExitInfo.REASON_CRASH,
            pid = 222,
            status = 0,
            importance = 200,
            timestamp = 3333L,
            description = "jvm",
            trace = ByteArrayInputStream("trace-line-1\ntrace-line-2".toByteArray()),
        )
        val signaled = exitInfo(
            reason = ApplicationExitInfo.REASON_SIGNALED,
            pid = 333,
            status = 9,
            importance = 300,
            timestamp = 4444L,
            description = "signal",
        )

        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns am
        every { am.getHistoricalProcessExitReasons("ru.ozero.app", 0, 10) } returns
            listOf(crashNative, crashJvm, signaled)

        BootFileLogger.init(context)
        BootDiagnostics.dumpExitReasons(context)

        val log = BootFileLogger.read()
        assertTrue(log.contains("exitReasons count=3"))
        assertTrue(log.contains("exit pid=111 reason=CRASH_NATIVE"))
        assertTrue(log.contains("tombstone saved pid=111"))
        assertTrue(log.contains("exit pid=222 reason=CRASH_JVM"))
        assertTrue(log.contains("trace pid=222"))
        assertTrue(log.contains("exit pid=333 reason=SIGNALED"))
        assertTrue(log.contains("signaled pid=333 signal=SIGKILL(9)"))

        val saved = File(File(filesDir, "debug"), "tombstone-111-2222.pb")
        assertTrue(saved.exists())
        assertTrue(saved.readBytes().contentEquals(nativeBytes))
    }

    private fun mockContext(filesDir: File): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns filesDir
        every { ctx.packageName } returns "ru.ozero.app"
        return ctx
    }

    private fun exitInfo(
        reason: Int,
        pid: Int,
        status: Int,
        importance: Int,
        timestamp: Long,
        description: String,
        trace: ByteArrayInputStream? = null,
    ): ApplicationExitInfo = mockk {
        every { this@mockk.reason } returns reason
        every { this@mockk.pid } returns pid
        every { this@mockk.status } returns status
        every { this@mockk.importance } returns importance
        every { this@mockk.timestamp } returns timestamp
        every { this@mockk.description } returns description
        every { this@mockk.traceInputStream } returns trace
    }
}
