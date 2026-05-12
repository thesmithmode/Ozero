package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrnetworkProvideControlModeTest {

    @Test
    fun `fromRaw возвращает AUTO для строки auto`() {
        assertEquals(UrnetworkProvideControlMode.AUTO, UrnetworkProvideControlMode.fromRaw("auto"))
    }

    @Test
    fun `fromRaw возвращает ALWAYS для строки always`() {
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw("always"))
    }

    @Test
    fun `fromRaw возвращает ALWAYS как дефолт для null`() {
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw(null))
    }

    @Test
    fun `fromRaw возвращает ALWAYS как дефолт для пустой строки`() {
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw(""))
    }

    @Test
    fun `fromRaw возвращает ALWAYS как дефолт для неизвестного значения`() {
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw("xyz"))
    }

    @Test
    fun `fromRaw чувствителен к регистру и возвращает дефолт для AUTO в верхнем регистре`() {
        assertEquals(UrnetworkProvideControlMode.ALWAYS, UrnetworkProvideControlMode.fromRaw("AUTO"))
    }

    @Test
    fun `rawValue AUTO равен строке auto`() {
        assertEquals("auto", UrnetworkProvideControlMode.AUTO.rawValue)
    }

    @Test
    fun `rawValue ALWAYS равен строке always`() {
        assertEquals("always", UrnetworkProvideControlMode.ALWAYS.rawValue)
    }

    @Test
    fun `entries содержит ровно две опции`() {
        assertEquals(2, UrnetworkProvideControlMode.entries.size)
    }
}
