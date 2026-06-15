package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrnetworkEnumMappingTest {

    @Test
    fun `window type maps every raw value and falls back to auto`() {
        UrnetworkWindowType.entries.forEach { value ->
            assertEquals(value, UrnetworkWindowType.fromRaw(value.rawValue))
        }
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw(null))
        assertEquals(UrnetworkWindowType.AUTO, UrnetworkWindowType.fromRaw("missing"))
    }

    @Test
    fun `provide control mode maps every raw value and falls back to always`() {
        UrnetworkProvideControlMode.entries.forEach { value ->
            assertEquals(value, UrnetworkProvideControlMode.fromRaw(value.rawValue))
        }
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw(null))
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw("missing"))
    }

    @Test
    fun `provide network mode maps every raw value and falls back to wifi`() {
        UrnetworkProvideNetworkMode.entries.forEach { value ->
            assertEquals(value, UrnetworkProvideNetworkMode.fromRaw(value.rawValue))
        }
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw(null))
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw("missing"))
    }
}
