package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals

class RootNavigationEngineParamsTargetTest {

    @Test
    fun `engine params target routes every concrete engine to its settings screen`() {
        val target = engineParamsTarget()

        assertEquals(TopScreen.AutoModeSettings, target(null))
        assertEquals(TopScreen.WarpEngineSettings, target(EngineId.WARP))
        assertEquals(TopScreen.UrnetworkEngineSettings, target(EngineId.URNETWORK))
        assertEquals(TopScreen.ByeDpiEngineSettings, target(EngineId.BYEDPI))
        assertEquals(TopScreen.MasterDnsSettings, target(EngineId.MASTERDNS))
        assertEquals(TopScreen.FptnSettings, target(EngineId.FPTN))
        assertEquals(TopScreen.SingboxSettings, target(EngineId.SINGBOX))
    }

    @Test
    fun `engine params target keeps stub engines on servers screen`() {
        val target = engineParamsTarget()

        assertEquals(TopScreen.Servers, target(EngineId.XRAY))
        assertEquals(TopScreen.Servers, target(EngineId.TOR))
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
