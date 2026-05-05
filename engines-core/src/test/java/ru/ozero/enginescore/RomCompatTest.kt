package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RomCompatTest {

    private fun check(manufacturer: String, model: String) =
        RomCompat.isNubiaRedMagic(manufacturer = manufacturer, model = model)

    @Test
    fun nubia_manufacturer_returns_true() = assertTrue(check("Nubia", "NX709J"))

    @Test
    fun zte_manufacturer_returns_true() = assertTrue(check("ZTE", "ZTE A52 Lite"))

    @Test
    fun redmagic_model_returns_true() = assertTrue(check("nubia", "RedMagic 9 Pro"))

    @Test
    fun nx7_model_returns_true() = assertTrue(check("Nubia", "NX729J"))

    @Test
    fun nx7_model_prefix_returns_true() = assertTrue(check("unknown", "NX7Pro"))

    @Test
    fun samsung_device_returns_false() = assertFalse(check("samsung", "SM-G991B"))

    @Test
    fun pixel_device_returns_false() = assertFalse(check("Google", "Pixel 8"))

    @Test
    fun xiaomi_device_returns_false() = assertFalse(check("Xiaomi", "Redmi Note 12"))
}
