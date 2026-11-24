package ru.ozero.enginemasque

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.json.JSONObject

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

    @Test
    fun `invalid endpoints and unexpected config keys are rejected`() {
        val json = """{"private_key":"test","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin()
        val base = JSONObject(json)
        val emptyEndpoints = JSONObject(json).put("endpoint_v6", "").put("endpoint_h2_v6", "")
        val emptyProfile = MasqueProfile.parse(emptyEndpoints.toString())
        assertEquals(false, emptyProfile.hasIpv6Endpoint(MasqueTransport.H2))
        assertEquals(false, emptyProfile.hasIpv6Endpoint(MasqueTransport.H3))
        val both = JSONObject(json).put("endpoint_h2_v4", "192.0.2.3").put("endpoint_v6", "2001:db8::4")
        assertEquals(true, MasqueProfile.parse(both.toString()).hasIpv6Endpoint(MasqueTransport.H3))
        val valid = MasqueProfile.parse(JSONObject(json).put("endpoint_h2_v6", "2001:db8::4").toString())
        assertEquals(true, valid.hasIpv6Endpoint(MasqueTransport.H2))
        assertEquals(false, valid.hasIpv6Endpoint(MasqueTransport.H3))
        val invalid = listOf(
            JSONObject(json).put("endpoint_v4", "example.invalid"),
            JSONObject(json).put("endpoint_v4", "192.0.2"),
            JSONObject(json).put("endpoint_v4", "192.0.02.1"),
            JSONObject(json).put("endpoint_v4", "192.0.2.-1"),
            JSONObject(json).put("endpoint_v4", "192.0.x.1"),
            JSONObject(json).put("private_key", 123),
            JSONObject(json).put("ipv4", "192.0.2.999"),
            JSONObject(json).put("ipv6", "not-an-ip"),
            JSONObject(json).put("ipv6", "bad::ip"),
            JSONObject(json).put("ipv6", "2001:db8:::1"),
            JSONObject(json).put("endpoint_h2_v4", "not-an-ip"),
            JSONObject(json).put("endpoint_v6", 123),
            JSONObject(json).put("endpoint_h2_v6", "not-an-ip"),
            JSONObject(json).put("PRIVATE_KEY", "alternate"),
            base.put("private_key", ""),
        )
        for (value in invalid) {
            assertThrows(IllegalArgumentException::class.java) { MasqueProfile.parse(value.toString()) }
        }
    }
}
