package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VpnModeTest {

    @Test
    fun `should have TUN value`() {
        assertEquals("TUN", VpnMode.TUN.name)
    }

    @Test
    fun `should have PROXY value`() {
        assertEquals("PROXY", VpnMode.PROXY.name)
    }

    @Test
    fun `should have exactly two values`() {
        assertEquals(2, VpnMode.entries.size)
    }

    @Test
    fun `should parse from string`() {
        assertEquals(VpnMode.TUN, VpnMode.valueOf("TUN"))
        assertEquals(VpnMode.PROXY, VpnMode.valueOf("PROXY"))
    }
}
