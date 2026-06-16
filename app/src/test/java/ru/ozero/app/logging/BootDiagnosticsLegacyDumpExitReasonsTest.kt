package ru.ozero.app.logging

import android.app.Application
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class BootDiagnosticsLegacyDumpExitReasonsTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `dumpExitReasons below Android R returns before touching system services`() {
        val context = mockContext(tempDir.newFolder("legacy"))

        BootDiagnostics.dumpExitReasons(context)

        io.mockk.verify(exactly = 0) { context.getSystemService(Context.ACTIVITY_SERVICE) }
    }

    private fun mockContext(filesDir: File): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns filesDir
        every { ctx.packageName } returns "ru.ozero.app"
        return ctx
    }
}
