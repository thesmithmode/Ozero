package ru.ozero.enginemasque

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MasqueProfileTest {
    @Test
    fun `profile keeps original data but redacts string representation`() {
        val json = """{"private_key":"test","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin()
        val profile = MasqueProfile.parse(json)
        assertEquals(json, profile.json)
        assertEquals("MasqueProfile(***)", profile.toString())
    }

    @Test
    fun `invalid json incomplete profiles and oversized payloads are rejected`() {
        for (json in listOf("", "invalid", "{}", "x".repeat(MasqueProfile.MAX_BYTES + 1))) {
            assertThrows(IllegalArgumentException::class.java) { MasqueProfile.parse(json) }
        }
    }
}
