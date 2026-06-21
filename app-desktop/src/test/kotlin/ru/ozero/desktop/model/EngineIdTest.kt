package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `engines should not be stubs`() {
        EngineId.entries.forEach { id -> assertTrue(!id.isStub) }
    }

    @ParameterizedTest
    @EnumSource(EngineId::class)
    fun `all engines should have non-empty display name`(id: EngineId) {
        assertTrue(id.displayName.isNotBlank())
    }
}
