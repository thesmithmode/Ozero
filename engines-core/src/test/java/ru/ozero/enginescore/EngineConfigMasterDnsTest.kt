package ru.ozero.enginescore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineConfigMasterDnsTest {

    @Test
    fun `engine id MASTERDNS not stub`() {
        val id = EngineId.MASTERDNS
        assertEquals("MasterDNS", id.displayName)
        assertFalse(id.isStub)
    }

    @Test
    fun `MasterDns config carries engine id`() {
        val config = EngineConfig.MasterDns(
            configToml = "DOMAINS=[\"v.example.com\"]\n",
            resolvers = listOf("8.8.8.8"),
            socksPort = 18000,
        )
        assertEquals(EngineId.MASTERDNS, config.engineId)
    }

    @Test
    fun `MasterDns default socks port`() {
        val config = EngineConfig.MasterDns(
            configToml = "x",
            resolvers = listOf("8.8.8.8"),
        )
        assertEquals(18000, config.socksPort)
    }

    @Test
    fun `MasterDns toString redacts configToml`() {
        val config = EngineConfig.MasterDns(
            configToml = "ENCRYPTION_KEY=\"deadbeef-secret\"",
            resolvers = listOf("1.1.1.1"),
            socksPort = 18000,
        )
        val s = config.toString()
        assertFalse(s.contains("deadbeef-secret")) { "secret leaked: $s" }
        assertTrue(s.contains("***"))
        assertTrue(s.contains("1 entries"))
    }
}
