package ru.ozero.commonnet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CountryFlagTest {

    private val whiteFlag = "🏳"

    @Test
    fun `US returns US emoji`() {
        assertEquals("🇺🇸", CountryFlag.emoji("US"))
    }

    @Test
    fun `RU returns RU emoji`() {
        assertEquals("🇷🇺", CountryFlag.emoji("RU"))
    }

    @Test
    fun `lowercase nl uppercased to NL emoji`() {
        assertEquals("🇳🇱", CountryFlag.emoji("nl"))
    }

    @Test
    fun `null input returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji(null))
    }

    @Test
    fun `empty input returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji(""))
    }

    @Test
    fun `single char input returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji("U"))
    }

    @Test
    fun `three chars input returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji("USA"))
    }

    @Test
    fun `digits input returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji("12"))
    }

    @Test
    fun `mixed alphanumeric input returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji("U1"))
    }

    @Test
    fun `non letter first char returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji("1U"))
    }

    @Test
    fun `punctuation second char returns white flag`() {
        assertEquals(whiteFlag, CountryFlag.emoji("U-"))
    }

    @Test
    fun `whitespace stripped — gb-with-spaces resolves to GB`() {
        assertEquals("🇬🇧", CountryFlag.emoji("  gb  "))
    }

    @Test
    fun `unassigned ZZ still produces 2-char regional indicator pair`() {
        val zz = CountryFlag.emoji("ZZ")
        assertEquals(2, zz.codePointCount(0, zz.length))
    }
}
