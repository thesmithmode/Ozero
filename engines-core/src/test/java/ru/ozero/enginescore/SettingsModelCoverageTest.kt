package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsModelCoverageTest {

    @Test
    fun `settings defaults expose product baseline`() {
        val model = SettingsModel.DEFAULT

        assertEquals(SplitTunnelMode.ALL, model.splitMode)
        assertFalse(model.ipv6Enabled)
        assertFalse(model.autoStart)
        assertEquals(TrafficMode.TUN, model.trafficMode)
        assertNull(model.manualEngine)
        assertEquals(
            listOf(
                EngineId.WARP,
                EngineId.URNETWORK,
                EngineId.BYEDPI,
                EngineId.MASTERDNS,
                EngineId.SINGBOX,
                EngineId.FPTN,
            ),
            model.engineAutoPriority,
        )
        assertFalse(model.urnetworkEnabled)
        assertTrue(model.byedpiUseUiMode)
        assertEquals(AppMode.SIMPLE, model.appMode)
        assertContains(SettingsModel.SUPPORTED_LOCALES, SettingsModel.LOCALE_RU)
        assertContains(SettingsModel.SUPPORTED_LOCALES, SettingsModel.LOCALE_EN)
        assertContains(SettingsModel.SUPPORTED_LOCALES, SettingsModel.LOCALE_PT)
    }

    @Test
    fun `settings copy and byedpi ui settings preserve fields`() {
        val ui = ByeDpiUiSettings.DEFAULT.copy(
            desyncMethod = ByeDpiUiSettings.DesyncMethod.FAKE,
            splitPosition = 2,
            tlsRecordSplitPosition = 3,
            fakeSni = "front.example.com",
        )
        val custom = SettingsModel.DEFAULT.copy(
            splitMode = SplitTunnelMode.ALLOWLIST,
            manualEngine = EngineId.BYEDPI,
            byedpiUiSettings = ui,
            customDnsServers = listOf("1.1.1.1"),
            killswitchEnabled = true,
        )

        assertNotEquals(SettingsModel.DEFAULT, custom)
        assertEquals(EngineId.BYEDPI, custom.manualEngine)
        assertEquals(listOf("1.1.1.1"), custom.customDnsServers)
        assertTrue(custom.killswitchEnabled)
        assertEquals("front.example.com", custom.byedpiUiSettings.fakeSni)
        assertContains(custom.toString(), "killswitchEnabled=true")
    }
}
