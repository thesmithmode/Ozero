package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineConfig
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrnetworkConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = EngineConfig.Urnetwork(jwtToken = "my-jwt")
        assertEquals("https://api.urnetwork.com", config.apiUrl)
        assertNull(config.region)
        assertEquals("consumer", config.mode)
        assertEquals(10810, config.socksPort)
    }

    @Test
    fun customValuesAreSet() {
        val config = EngineConfig.Urnetwork(
            jwtToken = "jwt123",
            apiUrl = "https://staging.api.urnetwork.com",
            region = "eu-west",
            mode = "provider",
            socksPort = 11111,
        )
        assertEquals("jwt123", config.jwtToken)
        assertEquals("https://staging.api.urnetwork.com", config.apiUrl)
        assertEquals("eu-west", config.region)
        assertEquals("provider", config.mode)
        assertEquals(11111, config.socksPort)
    }

    @Test
    fun dataClassEquality() {
        val a = EngineConfig.Urnetwork(jwtToken = "abc")
        val b = EngineConfig.Urnetwork(jwtToken = "abc")
        assertEquals(a, b)
    }

    @Test
    fun copyPreservesValues() {
        val original = EngineConfig.Urnetwork(jwtToken = "orig", region = "us-east")
        val copy = original.copy(region = "eu-west")
        assertEquals("orig", copy.jwtToken)
        assertEquals("eu-west", copy.region)
    }

    @Test
    fun isSubclassOfEngineConfig() {
        val config: EngineConfig = EngineConfig.Urnetwork(jwtToken = "jwt")
        assert(config is EngineConfig.Urnetwork)
    }
}
