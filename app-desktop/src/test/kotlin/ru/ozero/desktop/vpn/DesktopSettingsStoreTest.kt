package ru.ozero.desktop.vpn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ru.ozero.desktop.model.AppMode
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.model.VpnMode

class DesktopSettingsStoreTest {

    @Nested
    inner class DefaultSettings {

        @Test
        fun `should have ipv6 disabled by default`() {
            assertFalse(SettingsModel.DEFAULT.ipv6Enabled)
        }

        @Test
        fun `should have autoStart disabled by default`() {
            assertFalse(SettingsModel.DEFAULT.autoStart)
        }

        @Test
        fun `should have null manualEngine by default`() {
            assertNull(SettingsModel.DEFAULT.manualEngine)
        }

        @Test
        fun `should have SIMPLE appMode by default`() {
            assertEquals(AppMode.SIMPLE, SettingsModel.DEFAULT.appMode)
        }

        @Test
        fun `should have killswitch disabled by default`() {
            assertFalse(SettingsModel.DEFAULT.killswitchEnabled)
        }

        @Test
        fun `should have TUN vpnMode by default`() {
            assertEquals(VpnMode.TUN, SettingsModel.DEFAULT.vpnMode)
        }

        @Test
        fun `should have default engine priority`() {
            val expected = listOf(
                EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK,
                EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
            )
            assertEquals(expected, SettingsModel.DEFAULT.engineAutoPriority)
        }
    }

    @Nested
    inner class SettingsModelCopy {

        @Test
        fun `should update vpnMode`() {
            val updated = SettingsModel.DEFAULT.copy(vpnMode = VpnMode.PROXY)
            assertEquals(VpnMode.PROXY, updated.vpnMode)
        }

        @Test
        fun `should update appMode`() {
            val updated = SettingsModel.DEFAULT.copy(appMode = AppMode.EXPERT)
            assertEquals(AppMode.EXPERT, updated.appMode)
        }

        @Test
        fun `should update manualEngine`() {
            val updated = SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI)
            assertEquals(EngineId.BYEDPI, updated.manualEngine)
        }

        @Test
        fun `should update killswitch`() {
            val updated = SettingsModel.DEFAULT.copy(killswitchEnabled = true)
            assertTrue(updated.killswitchEnabled)
        }
    }
}
