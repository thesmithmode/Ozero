package ru.ozero.app.soak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MasqueNativeSmokeTest {

    @Test
    fun packagedExecutableStartsUnderAppUid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val binary = File(context.applicationInfo.nativeLibraryDir, "libusque.so")
        assertTrue(binary.isFile && binary.canExecute())
        val output = File.createTempFile("masque-smoke-", ".txt", context.cacheDir)
        try {
            val child = ProcessBuilder(binary.absolutePath, "socks", "--help")
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()
            try {
                assertTrue("MASQUE help timed out", child.waitFor(10, TimeUnit.SECONDS))
                assertEquals(0, child.exitValue())
                val help = output.readText()
                for (flag in listOf("--http2", "--ipv6", "--no-tunnel-ipv6", "--always-reconnect", "--dns")) {
                    assertTrue("Missing native option: $flag", help.contains(flag))
                }
            } finally {
                child.destroy()
                if (child.isAlive) child.destroyForcibly()
            }
        } finally {
            output.delete()
        }
    }
}
