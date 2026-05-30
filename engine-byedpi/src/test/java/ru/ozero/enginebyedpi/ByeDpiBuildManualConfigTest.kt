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
    fun `CMD mode winningArgs передаются verbatim — никаких авто-suffix`() {
        val custom = "-s1 -q1 -a1 -Y -At -a1 -S -f-1 -r1+s -a1 -As -d1+s -O1 -s29+s -a1"
        val cfg = engine.buildManualConfig(
            SettingsModel(byedpiUseUiMode = false, byedpiWinningArgs = custom),
        ) as EngineConfig.ByeDpi
        assertEquals(custom, cfg.args, "CMD args обязаны идти в byedpi байт-в-байт, got '${cfg.args}'")
    }

    @Test
    fun `CMD mode без -Ku не получает UDP desync auto-suffix — user explicit control`() {
        val custom = "-Y -Ar -s5"
        val cfg = engine.buildManualConfig(
            SettingsModel(byedpiUseUiMode = false, byedpiWinningArgs = custom),
        ) as EngineConfig.ByeDpi
        assertEquals(custom, cfg.args, "CMD без -Ku не должен авто-добавлять -Ku -a1 -An, got '${cfg.args}'")
        assertTrue("-Ku" !in cfg.args, "-Ku не должен быть автоматически добавлен в CMD режиме")
        assertTrue("-An" !in cfg.args.removePrefix(custom), "hosts -An не должен appear-иться, got '${cfg.args}'")
    }

    @Test
    fun `CMD mode winningArgs с пробелами на краях trim`() {
        val cfg = engine.buildManualConfig(
            SettingsModel(byedpiUseUiMode = false, byedpiWinningArgs = "  -Y -Ar -s5  "),
        ) as EngineConfig.ByeDpi
        assertEquals("-Y -Ar -s5", cfg.args)
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
        assertTrue("-Ku" !in cfg.args, "UI mode defaults force TCP fallback for YouTube, got '${cfg.args}'")
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
