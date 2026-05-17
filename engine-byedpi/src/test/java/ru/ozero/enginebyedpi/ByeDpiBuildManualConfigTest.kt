package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals

class ByeDpiBuildManualConfigTest {

    private val engine = ByeDpiEngine(proxy = FakeByeDpiProxy())

    @Test
    fun `null settings → default args + DISABLED hosts`() {
        val cfg = engine.buildManualConfig(null) as EngineConfig.ByeDpi
        assertEquals(EngineConfig.ByeDpi().args, cfg.args)
        assertEquals(HostsMode.DISABLED, cfg.hostsMode)
        assertEquals(emptyList(), cfg.hosts)
    }

    @Test
    fun `winningArgs из SettingsModel override`() {
        val custom = "-Y -Ar -s5"
        val cfg = engine.buildManualConfig(SettingsModel(byedpiWinningArgs = custom)) as EngineConfig.ByeDpi
        assertEquals(custom, cfg.args)
    }

    @Test
    fun `blank winningArgs → fallback default args`() {
        val cfg = engine.buildManualConfig(SettingsModel(byedpiWinningArgs = "   ")) as EngineConfig.ByeDpi
        assertEquals(EngineConfig.ByeDpi().args, cfg.args)
    }

    @Test
    fun `hostsMode и hosts прокидываются`() {
        val hosts = listOf("example.com", "blocked.ru")
        val cfg = engine.buildManualConfig(
            SettingsModel(hostsMode = HostsMode.WHITELIST, hosts = hosts),
        ) as EngineConfig.ByeDpi
        assertEquals(HostsMode.WHITELIST, cfg.hostsMode)
        assertEquals(hosts, cfg.hosts)
    }
}

private class FakeByeDpiProxy : ByeDpiProxyContract {
    override fun startProxy(args: Array<String>): Int = 0
    override fun stopProxy(): Int = 0
    override fun forceClose(): Int = 0
    override fun emergencyReset(): Int = 0
}
