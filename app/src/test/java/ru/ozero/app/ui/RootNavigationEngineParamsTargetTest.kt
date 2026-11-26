package ru.ozero.app.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals

class RootNavigationEngineParamsTargetTest {

    @Test
    fun `engine params target routes every concrete engine to its settings screen`() {
        val target = engineParamsTarget()

        assertEquals(TopScreen.AutoModeSettings, target(null))
        assertEquals(TopScreen.WarpEngineSettings, target(EngineId.WARP))
        assertEquals(TopScreen.MasqueSettings, target(EngineId.MASQUE))
        assertEquals(TopScreen.UrnetworkEngineSettings, target(EngineId.URNETWORK))
        assertEquals(TopScreen.ByeDpiEngineSettings, target(EngineId.BYEDPI))
        assertEquals(TopScreen.MasterDnsSettings, target(EngineId.MASTERDNS))
        assertEquals(TopScreen.FptnSettings, target(EngineId.FPTN))
        assertEquals(TopScreen.SingboxSettings, target(EngineId.SINGBOX))
        assertEquals(TopScreen.TelegramWssSettings, target(EngineId.TGWSS))
        assertEquals(TopScreen.OperaSettings, target(EngineId.OPERA))
        assertEquals(TopScreen.GfVpnSettings, target(EngineId.GFVPN))
    }

    @Test
    fun `restoring navigation retains the complete path back from WARP to Telegram WSS`() {
        val screens = listOf(
            TopScreen.Main,
            TopScreen.Settings,
            TopScreen.TelegramWssSettings,
            TopScreen.WarpEngineSettings,
        )

        val saved = with(screenHistorySaver) { SaverScope { it is String }.save(screens) }
        val restored = screenHistorySaver.restore(checkNotNull(saved))

        assertEquals(screens, restored)
    }

    @Suppress("UNCHECKED_CAST")
    private fun engineParamsTarget(): (EngineId?) -> TopScreen {
        val method = Class.forName("ru.ozero.app.ui.RootNavigationKt")
            .declaredMethods
            .first { it.name == "engineParamsTarget" }
            .apply { isAccessible = true }
        return { engineId -> method.invoke(null, engineId) as TopScreen }
    }
}
