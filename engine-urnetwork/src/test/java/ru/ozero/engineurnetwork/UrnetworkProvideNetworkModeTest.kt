package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrnetworkProvideNetworkModeTest {

    @Test
    fun `fromRaw возвращает WIFI для строки wifi`() {
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw("wifi"))
    }

    @Test
    fun `fromRaw возвращает ALL для строки all`() {
        assertEquals(UrnetworkProvideNetworkMode.ALL, UrnetworkProvideNetworkMode.fromRaw("all"))
    }

    @Test
    fun `fromRaw возвращает WIFI как дефолт для null`() {
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw(null))
    }

    @Test
    fun `fromRaw возвращает WIFI как дефолт для пустой строки`() {
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw(""))
    }

    @Test
    fun `fromRaw возвращает WIFI как дефолт для неизвестного значения`() {
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw("cellular"))
    }

    @Test
    fun `fromRaw чувствителен к регистру и возвращает дефолт для WIFI в верхнем регистре`() {
        assertEquals(UrnetworkProvideNetworkMode.WIFI, UrnetworkProvideNetworkMode.fromRaw("WIFI"))
    }

    @Test
    fun `rawValue WIFI равен строке wifi`() {
        assertEquals("wifi", UrnetworkProvideNetworkMode.WIFI.rawValue)
    }

    @Test
    fun `rawValue ALL равен строке all`() {
        assertEquals("all", UrnetworkProvideNetworkMode.ALL.rawValue)
    }

    @Test
    fun `entries содержит ровно две опции`() {
        assertEquals(2, UrnetworkProvideNetworkMode.entries.size)
    }
}
