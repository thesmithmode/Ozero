package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CountryFlagTest {

    @Test
    fun `emoji returns flag for valid country code`() {
        val flag = CountryFlag.emoji("US")
        assertEquals("🇺🇸", flag)
    }

    @Test
    fun `emoji handles lowercase`() {
        val flag = CountryFlag.emoji("ru")
        assertEquals("🇷🇺", flag)
    }

    @Test
    fun `emoji returns white flag for null`() {
        assertEquals("🏳", CountryFlag.emoji(null))
    }

    @Test
    fun `emoji returns white flag for invalid length`() {
        assertEquals("🏳", CountryFlag.emoji("USA"))
    }

    @Test
    fun `emoji returns white flag for digits`() {
        assertEquals("🏳", CountryFlag.emoji("12"))
    }
}
