package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class EngineIdTest {

    @Test
    fun `should have expected display names`() {
        assertEquals("ByeDPI", EngineId.BYEDPI.displayName)
        assertEquals("URnetwork", EngineId.URNETWORK.displayName)
        assertEquals("WARP", EngineId.WARP.displayName)
        assertEquals("MasterDNS", EngineId.MASTERDNS.displayName)
        assertEquals("Sing-box", EngineId.SINGBOX.displayName)
        assertEquals("FPTN", EngineId.FPTN.displayName)
    }

    @Test
    fun `core engines should not be stubs`() {
        assertFalse(EngineId.BYEDPI.isStub)
        assertFalse(EngineId.URNETWORK.isStub)
        assertFalse(EngineId.WARP.isStub)
        assertFalse(EngineId.MASTERDNS.isStub)
        assertFalse(EngineId.SINGBOX.isStub)
        assertFalse(EngineId.FPTN.isStub)
    }

    @Test
    fun `future engines should be stubs`() {
        assertTrue(EngineId.XRAY.isStub)
        assertTrue(EngineId.HYSTERIA2.isStub)
        assertTrue(EngineId.NAIVE.isStub)
        assertTrue(EngineId.TOR.isStub)
    }

    @ParameterizedTest
    @EnumSource(EngineId::class)
    fun `all engines should have non-empty display name`(id: EngineId) {
        assertTrue(id.displayName.isNotBlank())
    }
}
