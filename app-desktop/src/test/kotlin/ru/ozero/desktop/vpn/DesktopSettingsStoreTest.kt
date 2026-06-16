package ru.ozero.desktop.vpn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.desktop.model.AppMode
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.model.SplitTunnelMode
import ru.ozero.desktop.model.VpnMode
import java.io.File

class DesktopSettingsStoreTest {

    @TempDir
    lateinit var tempDir: File

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
                EngineId.WARP, EngineId.URNETWORK, EngineId.BYEDPI,
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

    @Nested
    inner class Persistence {

        @Test
        fun `update saves settings and next store loads them`() {
            System.setProperty("user.home", tempDir.absolutePath)
            val store = DesktopSettingsStore()

            store.update {
                copy(
                    ipv6Enabled = true,
                    autoStart = true,
                    manualEngine = EngineId.WARP,
                    appMode = AppMode.EXPERT,
                    killswitchEnabled = true,
                    vpnMode = VpnMode.PROXY,
                    byedpiArgs = "-s1 -a1",
                    byedpiDns = "1.1.1.1",
                    singboxSubscriptionUrl = "https://example.test/sub",
                    singboxCustomLink = "vless://example",
                    splitTunnelMode = SplitTunnelMode.BLOCKLIST,
                    splitTunnelApps = listOf("app.one", "app.two"),
                )
            }

            val loaded = DesktopSettingsStore().settings.value
            assertTrue(loaded.ipv6Enabled)
            assertTrue(loaded.autoStart)
            assertEquals(EngineId.WARP, loaded.manualEngine)
            assertEquals(AppMode.EXPERT, loaded.appMode)
            assertTrue(loaded.killswitchEnabled)
            assertEquals(VpnMode.PROXY, loaded.vpnMode)
            assertEquals("-s1 -a1", loaded.byedpiArgs)
            assertEquals("1.1.1.1", loaded.byedpiDns)
            assertEquals("https://example.test/sub", loaded.singboxSubscriptionUrl)
            assertEquals("vless://example", loaded.singboxCustomLink)
            assertEquals(SplitTunnelMode.BLOCKLIST, loaded.splitTunnelMode)
            assertEquals(listOf("app.one", "app.two"), loaded.splitTunnelApps)
        }

        @Test
        fun `load falls back for invalid enum values and keeps blank split apps out`() {
            System.setProperty("user.home", tempDir.absolutePath)
            val settingsFile = tempDir.resolve(".ozero/settings.properties")
            settingsFile.parentFile.mkdirs()
            settingsFile.writeText(
                """
                manualEngine=UNKNOWN
                appMode=UNKNOWN
                vpnMode=UNKNOWN
                splitTunnelMode=UNKNOWN
                splitTunnelApps=one||two|
                ipv6=true
                """.trimIndent(),
            )

            val loaded = DesktopSettingsStore().settings.value

            assertNull(loaded.manualEngine)
            assertEquals(AppMode.EXPERT, loaded.appMode)
            assertEquals(VpnMode.TUN, loaded.vpnMode)
            assertEquals(SplitTunnelMode.DISABLED, loaded.splitTunnelMode)
            assertEquals(listOf("one", "two"), loaded.splitTunnelApps)
            assertTrue(loaded.ipv6Enabled)
        }
    }
}
