package ru.ozero.commonnet

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NetworkProfileTest {

    @Test
    fun `NONE has stable id and transport`() {
        assertEquals("none", NetworkProfile.NONE.id)
        assertEquals(NetworkProfile.Transport.NONE, NetworkProfile.NONE.transport)
    }

    @Test
    fun `equality covers id transport label`() {
        val a = NetworkProfile(id = "abc", transport = NetworkProfile.Transport.WIFI, label = "Home")
        val b = NetworkProfile(id = "abc", transport = NetworkProfile.Transport.WIFI, label = "Home")
        assertEquals(a, b)
    }

    @Test
    fun `different id makes profiles unequal`() {
        val a = NetworkProfile(id = "x", transport = NetworkProfile.Transport.WIFI)
        val b = NetworkProfile(id = "y", transport = NetworkProfile.Transport.WIFI)
        assertNotEquals(a, b)
    }

    @Test
    fun `default label is null`() {
        val p = NetworkProfile(id = "x", transport = NetworkProfile.Transport.WIFI)
        assertEquals(null, p.label)
    }

    @Test
    fun `transport enum exposes all expected values`() {
        val values = NetworkProfile.Transport.entries.map { it.name }.toSet()
        assertEquals(setOf("WIFI", "MOBILE", "ETHERNET", "VPN", "OTHER", "NONE"), values)
    }
}
