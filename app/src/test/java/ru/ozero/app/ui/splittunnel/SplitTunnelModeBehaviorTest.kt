package ru.ozero.app.ui.splittunnel

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SplitTunnelMode
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitTunnelModeBehaviorTest {

    @Test
    fun `requiresAppList false для ALL`() {
        assertFalse(SplitTunnelMode.ALL.requiresAppList())
    }

    @Test
    fun `requiresAppList false для BYPASS_LAN`() {
        assertFalse(SplitTunnelMode.BYPASS_LAN.requiresAppList())
    }

    @Test
    fun `requiresAppList true для ALLOWLIST`() {
        assertTrue(SplitTunnelMode.ALLOWLIST.requiresAppList())
    }

    @Test
    fun `requiresAppList true для BLOCKLIST`() {
        assertTrue(SplitTunnelMode.BLOCKLIST.requiresAppList())
    }

    @Test
    fun `SplitTunnelScreen рисует app list только когда requiresAppList`() {
        val source = File("src/main/java/ru/ozero/app/ui/splittunnel/SplitTunnelScreen.kt")
            .takeIf { it.exists() }
            ?.readText()
            ?: error("SplitTunnelScreen.kt not found at expected path")
        assertTrue(
            source.contains("requiresAppList()"),
            "SplitTunnelScreen.kt обязан использовать requiresAppList() как gate для рендера " +
                "AppsList — иначе при mode=ALL/BYPASS_LAN список приложений видим, что " +
                "противоречит UX (все идут через VPN, выбора нет).",
        )
    }

    @Test
    fun `SplitTunnelScreen refreshes apps on resume`() {
        val source = File("src/main/java/ru/ozero/app/ui/splittunnel/SplitTunnelScreen.kt")
            .takeIf { it.exists() }
            ?.readText()
            ?: error("SplitTunnelScreen.kt not found at expected path")
        assertTrue(
            source.contains("Lifecycle.Event.ON_RESUME") &&
                source.contains("viewModel.onResume()"),
            "SplitTunnelScreen обязан дергать VM refresh на ON_RESUME, иначе список приложений " +
                "не обновится после установки или удаления пакетов вне экрана.",
        )
    }
}
