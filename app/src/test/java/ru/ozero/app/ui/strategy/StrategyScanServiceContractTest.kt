package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.Test
import java.io.File
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

    @Test
    fun `service is not sticky and does not self restart from task removal`() {
        val source = serviceSource()
        assertTrue(source.contains("return START_NOT_STICKY"))
        assertTrue(!source.contains("return START_STICKY"), "user-started strategy scans must not auto-restart")
        assertTrue(!source.contains("override fun onTaskRemoved"), "task removal must not self-restart scan service")
    }

    @Test
    fun `notification exposes explicit stop action`() {
        val source = serviceSource()
        assertTrue(source.contains("PendingIntent.getService"))
        assertTrue(source.contains("ACTION_STOP"))
        assertTrue(source.contains(".addAction("))
        assertTrue(source.contains("cancelRequests.tryEmit(Unit)"))
    }

    private fun serviceSource(): String =
        File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/app/ui/strategy/StrategyScanService.kt")
            .readText()
}
