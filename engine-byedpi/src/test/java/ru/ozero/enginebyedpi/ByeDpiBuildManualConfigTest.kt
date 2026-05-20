package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `CMD mode winningArgs override сохраняет custom + дописывает -Ku если отсутствует`() {
        val custom = "-Y -Ar -s5"
        val cfg = engine.buildManualConfig(
            SettingsModel(byedpiUseUiMode = false, byedpiWinningArgs = custom),
        ) as EngineConfig.ByeDpi
        assertTrue(custom in cfg.args, "custom args обязаны быть сохранены, got '${cfg.args}'")
        assertTrue("-Ku" in cfg.args, "winning args без -Ku обязаны получить UDP desync, got '${cfg.args}'")
    }

    @Test
    fun `CMD mode winningArgs c уже существующим -Ku не дублирует UDP desync`() {
        val custom = "-Y -Ku -a2 -s5"
        val cfg = engine.buildManualConfig(
            SettingsModel(byedpiUseUiMode = false, byedpiWinningArgs = custom),
        ) as EngineConfig.ByeDpi
        assertEquals(custom, cfg.args, "winning args с уже -Ku не должны меняться, got '${cfg.args}'")
    }

    @Test
    fun `CMD mode blank winningArgs → fallback default args`() {
        val cfg = engine.buildManualConfig(
            SettingsModel(byedpiUseUiMode = false, byedpiWinningArgs = "   "),
        ) as EngineConfig.ByeDpi
        assertEquals(EngineConfig.ByeDpi().args, cfg.args)
    }

    @Test
    fun `UI mode игнорирует winningArgs и собирает args из ByeDpiUiSettings`() {
        val cfg = engine.buildManualConfig(
            SettingsModel(
                byedpiUseUiMode = true,
                byedpiWinningArgs = "-Y -Ar -s5",
                byedpiUiSettings = ByeDpiUiSettings.DEFAULT,
            ),
        ) as EngineConfig.ByeDpi
        assertTrue("-Ku" in cfg.args, "UI mode defaults включают desyncUdp -Ku, got '${cfg.args}'")
        assertTrue("-Y -Ar -s5" !in cfg.args)
    }

    @Test
    fun `UI mode передаёт desyncMethod из настроек`() {
        val cfg = engine.buildManualConfig(
            SettingsModel(
                byedpiUseUiMode = true,
                byedpiUiSettings = ByeDpiUiSettings.DEFAULT.copy(
                    desyncMethod = ByeDpiUiSettings.DesyncMethod.SPLIT,
                    splitPosition = 7,
                ),
            ),
        ) as EngineConfig.ByeDpi
        assertTrue("-s7" in cfg.args)
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
