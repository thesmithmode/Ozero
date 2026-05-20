package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MasterDnsConfigWriterTest {

    @Test
    fun `writes toml and resolvers and overrides listen port`(@TempDir tmp: Path) {
        val writer = MasterDnsConfigWriter(File(tmp.toFile(), "masterdns"))
        val runtime = MasterDnsRuntimeConfig(
            configToml = "DOMAINS = [\"v.example.com\"]\n" +
                "ENCRYPTION_KEY = \"key\"\n" +
                "LISTEN_PORT = 18000\n" +
                "LISTEN_IP = \"0.0.0.0\"\n" +
                "LOCAL_DNS_ENABLED = true\n",
            resolvers = listOf("8.8.8.8", "1.1.1.1"),
            socksPort = 18733,
        )

        val files = writer.write(runtime)

        val toml = File(files.configPath).readText()
        assertTrue(toml.contains("LISTEN_IP = \"127.0.0.1\""))
        assertTrue(toml.contains("LISTEN_PORT = 18733"))
        assertTrue(toml.contains("LOCAL_DNS_ENABLED = false"))
        assertFalse(toml.contains("LISTEN_IP = \"0.0.0.0\""))
        assertFalse(toml.contains("LISTEN_PORT = 18000"))
        assertFalse(toml.contains("LOCAL_DNS_ENABLED = true"))
        assertTrue(toml.contains("ENCRYPTION_KEY = \"key\""))
        val resolversText = File(files.resolversPath).readText()
        assertEquals("8.8.8.8\n1.1.1.1\n", resolversText)
    }

    @Test
    fun `appends listen overrides when toml lacks them`(@TempDir tmp: Path) {
        val writer = MasterDnsConfigWriter(File(tmp.toFile(), "masterdns"))
        val runtime = MasterDnsRuntimeConfig(
            configToml = "DOMAINS = [\"v.example.com\"]\n",
            resolvers = listOf("8.8.8.8"),
            socksPort = 18800,
        )

        val files = writer.write(runtime)

        val toml = File(files.configPath).readText()
        assertTrue(toml.contains("LISTEN_IP = \"127.0.0.1\""))
        assertTrue(toml.contains("LISTEN_PORT = 18800"))
        assertTrue(toml.contains("LOCAL_DNS_ENABLED = false"))
    }

    @Test
    fun `creates parent directory`(@TempDir tmp: Path) {
        val target = File(tmp.toFile(), "masterdns/inner")
        val writer = MasterDnsConfigWriter(target)
        writer.write(MasterDnsRuntimeConfig("DOMAINS = [\"v\"]\n", listOf("8.8.8.8"), 18000))
        assertTrue(target.isDirectory)
    }

    @Test
    fun `empty resolvers writes empty file with newline-postfix only when non-empty`(@TempDir tmp: Path) {
        val writer = MasterDnsConfigWriter(File(tmp.toFile(), "masterdns"))
        val files = writer.write(
            MasterDnsRuntimeConfig("DOMAINS = []\n", emptyList(), 18000),
        )
        val resolversText = File(files.resolversPath).readText()
        assertEquals("", resolversText)
    }

    @Test
    fun `single resolver written with trailing newline`(@TempDir tmp: Path) {
        val writer = MasterDnsConfigWriter(File(tmp.toFile(), "masterdns"))
        val files = writer.write(
            MasterDnsRuntimeConfig("DOMAINS = []\n", listOf("9.9.9.9"), 18000),
        )
        val resolversText = File(files.resolversPath).readText()
        assertEquals("9.9.9.9\n", resolversText)
    }
}
