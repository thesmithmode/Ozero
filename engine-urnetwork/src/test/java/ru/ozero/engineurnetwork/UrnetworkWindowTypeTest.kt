package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrnetworkWindowTypeTest {

    @Test
    fun `fromRaw maps all persisted values`() {
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw("auto"))
        assertEquals(UrnetworkWindowType.QUALITY, UrnetworkWindowType.fromRaw("quality"))
        assertEquals(UrnetworkWindowType.SPEED, UrnetworkWindowType.fromRaw("speed"))
    }

    @Test
    fun `fromRaw falls back to auto for absent malformed and case mismatched values`() {
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw(null))
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw(""))
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw("QUALITY"))
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw("balanced"))
    }

    @Test
    fun `raw values remain stable for datastore persistence`() {
        assertEquals(listOf("auto", "quality", "speed"), UrnetworkWindowType.entries.map { it.rawValue })
    }
}
