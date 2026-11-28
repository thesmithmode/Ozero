package ru.ozero.app.gfvpn

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.enginegfvpn.GfVpnBundledSdk
import ru.ozero.enginegfvpn.GfVpnRuntime
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Explicit real-SDK smoke test, not a mock and not a default CI assertion. */
@RunWith(AndroidJUnit4::class)
class GfVpnDeviceSmokeTest {
    @Test
    fun startsProbesAndStopsRealSdk() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("gfvpnSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("ARM64 device required", Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a"))
        assertTrue("Build must include the verified SDK", GfVpnBundledSdk.isAvailable(context))
        val runtime = GfVpnRuntime(context)
        try {
            val session = runtime.start("RU", UUID.randomUUID().toString()).get(310, TimeUnit.SECONDS)
            assertTrue(session.port in 1..65535)
            try {
                runtime.probeWithStats().get(20, TimeUnit.SECONDS)
                fail("TUN health probe succeeded without an attached descriptor")
            } catch (expected: ExecutionException) {
                assertTrue(expected.cause?.message.orEmpty().contains("probe-tun"))
            }
            assertTrue(runtime.probe().get(20, TimeUnit.SECONDS) >= 0)
            runtime.stop().get(20, TimeUnit.SECONDS)
        } finally {
            runtime.close()
        }
    }
}
