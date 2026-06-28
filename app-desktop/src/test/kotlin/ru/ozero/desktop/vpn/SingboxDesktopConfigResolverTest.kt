package ru.ozero.desktop.vpn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.model.VpnMode
import java.io.File

class SingboxDesktopConfigResolverTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `uses imported JSON when present`() {
        val config = tempDir.resolve("singbox-custom.json")
        config.writeText("""{"outbounds":[{"type":"socks","tag":"proxy"}]}""")

        val result = SingboxDesktopConfigResolver.resolve(
            settings = SettingsModel.DEFAULT.copy(singboxCustomLink = "vless://ignored"),
            mode = VpnMode.TUN,
            importedConfigFile = config,
        )

        assertEquals(config.readText(), result.getOrThrow())
    }

    @Test
    fun `fails closed when proxy link cannot be converted to runtime config`() {
        val result = SingboxDesktopConfigResolver.resolve(
            settings = SettingsModel.DEFAULT.copy(singboxCustomLink = "vless://profile"),
            mode = VpnMode.TUN,
            importedConfigFile = tempDir.resolve("missing.json"),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("imported JSON") == true)
    }

    @Test
    fun `wraps outbound JSON for TUN mode`() {
        val outbound = """{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":1080}"""
        val result = SingboxDesktopConfigResolver.resolve(
            settings = SettingsModel.DEFAULT.copy(singboxCustomLink = outbound),
            mode = VpnMode.TUN,
            importedConfigFile = tempDir.resolve("missing.json"),
        ).getOrThrow()

        assertTrue(result.contains(outbound))
        assertTrue(result.contains(""""type":"tun""""))
        assertTrue(result.contains(""""final":"proxy""""))
    }

    @Test
    fun `keeps default direct config only when no user singbox config exists`() {
        val result = SingboxDesktopConfigResolver.resolve(
            settings = SettingsModel.DEFAULT,
            mode = VpnMode.PROXY,
            importedConfigFile = tempDir.resolve("missing.json"),
        ).getOrThrow()

        assertTrue(result.contains(""""type":"mixed""""))
        assertTrue(result.contains(""""final":"direct""""))
    }
}
