package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StrategyScanServiceContractTest {

    @Test
    fun `ACTION_STOP has correct package prefix`() {
        assertTrue(
            StrategyScanService.ACTION_STOP.startsWith("ru.ozero.app."),
            "action must be scoped to app package: ${StrategyScanService.ACTION_STOP}",
        )
    }

    @Test
    fun `NOTIFICATION_ID does not clash with VPN notification`() {
        assertNotEquals(
            1,
            StrategyScanService.NOTIFICATION_ID,
            "scan NOTIFICATION_ID must not clash with VPN (id=1)",
        )
    }

    @Test
    fun `CHANNEL_ID is strategy_scan`() {
        assertEquals("strategy_scan", StrategyScanService.CHANNEL_ID)
    }

    @Test
    fun `CHANNEL_ID differs from VPN channel`() {
        assertNotEquals("ozero_vpn", StrategyScanService.CHANNEL_ID)
    }
}
