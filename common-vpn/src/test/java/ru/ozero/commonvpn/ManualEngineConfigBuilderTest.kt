package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualEngineConfigBuilderTest {

    @Test
    fun `BYEDPI с null settings строит default ByeDpi config`() {
        val cfg = ManualEngineConfigBuilder.build(EngineId.BYEDPI, null)
        assertNotNull(cfg)
        assertTrue(cfg is EngineConfig.ByeDpi)
        assertEquals(EngineId.BYEDPI, cfg.engineId)
    }

    @Test
    fun `BYEDPI с winningArgs использует их вместо дефолтных`() {
        val custom = "-Y -Ar -s5"
        val settings = SettingsModel(byedpiWinningArgs = custom)
        val cfg = ManualEngineConfigBuilder.build(EngineId.BYEDPI, settings) as EngineConfig.ByeDpi
        assertEquals(custom, cfg.args)
    }

    @Test
    fun `BYEDPI с blank winningArgs falls back на default`() {
        val settings = SettingsModel(byedpiWinningArgs = "   ")
        val cfg = ManualEngineConfigBuilder.build(EngineId.BYEDPI, settings) as EngineConfig.ByeDpi
        assertEquals(EngineConfig.ByeDpi().args, cfg.args)
    }

    @Test
    fun `BYEDPI прокидывает hostsMode и hosts из settings`() {
        val hosts = listOf("example.com", "blocked.ru")
        val settings = SettingsModel(hostsMode = HostsMode.WHITELIST, hosts = hosts)
        val cfg = ManualEngineConfigBuilder.build(EngineId.BYEDPI, settings) as EngineConfig.ByeDpi
        assertEquals(HostsMode.WHITELIST, cfg.hostsMode)
        assertEquals(hosts, cfg.hosts)
    }

    @Test
    fun `WARP всегда возвращает EngineConfig Warp`() {
        val cfg = ManualEngineConfigBuilder.build(EngineId.WARP, null)
        assertEquals(EngineConfig.Warp, cfg)
    }

    @Test
    fun `URNETWORK с null settings прокидывает пустой jwtToken`() {
        val cfg = ManualEngineConfigBuilder.build(EngineId.URNETWORK, null) as EngineConfig.Urnetwork
        assertEquals("", cfg.jwtToken)
    }

    @Test
    fun `URNETWORK с jwt из settings прокидывает токен`() {
        val settings = SettingsModel(urnetworkJwt = "TOKEN_VALUE")
        val cfg = ManualEngineConfigBuilder.build(EngineId.URNETWORK, settings) as EngineConfig.Urnetwork
        assertEquals("TOKEN_VALUE", cfg.jwtToken)
    }

    @Test
    fun `engineId конфига всегда совпадает с запрошенным engineId — sentinel`() {
        listOf(EngineId.BYEDPI, EngineId.WARP, EngineId.URNETWORK).forEach { id ->
            val cfg = ManualEngineConfigBuilder.build(id, null)
            assertNotNull(cfg, "engine $id должен иметь конфиг")
            assertEquals(
                id,
                cfg.engineId,
                "config.engineId должен совпадать с запрошенным engineId — иначе ChainOrchestrator " +
                    "получит mismatch и попытается стартовать не тот engine, что выбрал юзер.",
            )
        }
    }

    @Test
    fun `unsupported engines возвращают null без fallback на BYEDPI — sentinel`() {
        val unsupported = listOf(
            EngineId.XRAY, EngineId.HYSTERIA2, EngineId.AMNEZIA,
            EngineId.TOR, EngineId.NAIVE, EngineId.FPTN,
        )
        unsupported.forEach { id ->
            val cfg = ManualEngineConfigBuilder.build(id, null)
            assertNull(
                cfg,
                "engine $id не имеет реализации — builder обязан вернуть null, чтобы " +
                    "OzeroVpnService отказал юзеру с понятной ошибкой, а НЕ сфолбэчил на BYEDPI. " +
                    "Юзер выбрал manual mode → строго этот engine или fail.",
            )
        }
    }
}
