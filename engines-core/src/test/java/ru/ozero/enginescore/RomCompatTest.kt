package ru.ozero.enginescore

import android.os.Build
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RomCompatTest {

    private lateinit var manufacturerField: java.lang.reflect.Field
    private lateinit var modelField: java.lang.reflect.Field
    private lateinit var originalManufacturer: String
    private lateinit var originalModel: String

    @BeforeEach
    fun setUp() {
        manufacturerField = Build::class.java.getField("MANUFACTURER")
        modelField = Build::class.java.getField("MODEL")
        manufacturerField.isAccessible = true
        modelField.isAccessible = true
        val modifiers = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
        modifiers.isAccessible = true
        modifiers.setInt(manufacturerField, manufacturerField.modifiers and java.lang.reflect.Modifier.FINAL.inv())
        modifiers.setInt(modelField, modelField.modifiers and java.lang.reflect.Modifier.FINAL.inv())
        originalManufacturer = Build.MANUFACTURER
        originalModel = Build.MODEL
    }

    @AfterEach
    fun tearDown() {
        manufacturerField.set(null, originalManufacturer)
        modelField.set(null, originalModel)
    }

    private fun setBuild(manufacturer: String, model: String) {
        manufacturerField.set(null, manufacturer)
        modelField.set(null, model)
    }

    @Test
    fun nubia_manufacturer_returns_true() {
        setBuild("Nubia", "NX709J")
        assertTrue(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun zte_manufacturer_returns_true() {
        setBuild("ZTE", "ZTE A52 Lite")
        assertTrue(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun redmagic_model_returns_true() {
        setBuild("nubia", "RedMagic 9 Pro")
        assertTrue(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun nx7_model_returns_true() {
        setBuild("Nubia", "NX729J")
        assertTrue(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun nx7_model_prefix_returns_true() {
        setBuild("unknown", "NX7Pro")
        assertTrue(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun samsung_device_returns_false() {
        setBuild("samsung", "SM-G991B")
        assertFalse(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun pixel_device_returns_false() {
        setBuild("Google", "Pixel 8")
        assertFalse(RomCompat.isNubiaRedMagic())
    }

    @Test
    fun xiaomi_device_returns_false() {
        setBuild("Xiaomi", "Redmi Note 12")
        assertFalse(RomCompat.isNubiaRedMagic())
    }
}
