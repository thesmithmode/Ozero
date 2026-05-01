package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StubWarpSdkBridgeTest {

    private val sample = WarpConfig(
        privateKey = "p",
        publicKey = "P",
        peerPublicKey = "PP",
        peerEndpoint = "h:1",
        interfaceAddressV4 = "1.2.3.4/32",
        interfaceAddressV6 = "::1/128",
        accountLicense = "L",
    )

    @Test
    fun `start всегда возвращает Failed с упоминанием AAR`() = runTest {
        val bridge = StubWarpSdkBridge()
        val result = bridge.start(sample)
        val failed = assertIs<WarpSdkBridge.StartResult.Failed>(result)
        assertTrue(failed.reason.contains("WireGuard", ignoreCase = true))
        assertTrue(failed.reason.contains("AAR", ignoreCase = true))
    }

    @Test
    fun `isRunning остаётся false`() = runTest {
        val bridge = StubWarpSdkBridge()
        bridge.start(sample)
        assertFalse(bridge.isRunning())
    }

    @Test
    fun `stop не бросает`() = runTest {
        val bridge = StubWarpSdkBridge()
        bridge.start(sample)
        bridge.stop()
        assertEquals(false, bridge.isRunning())
    }
}
