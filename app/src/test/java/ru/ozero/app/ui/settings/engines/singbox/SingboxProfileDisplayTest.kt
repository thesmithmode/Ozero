package ru.ozero.app.ui.settings.engines.singbox

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val HUGE_SEPARATOR_COUNT = 10_000
private const val HUGE_SINGLE_PART_LENGTH = 2_000
private const val EXPECTED_SINGLE_PART_DISPLAY_LENGTH = 510
private const val MAX_EXPECTED_SUBTITLE_LENGTH = 533

class SingboxProfileDisplayTest {

    @Test
    fun `profile display extracts SNI host and subtitle`() {
        val display = "Group | SNI: example.com ✅ | vless".toSingboxProfileDisplay()

        assertEquals("example.com", display.title)
        assertEquals("Group · vless", display.subtitle)
    }

    @Test
    fun `profile display bounds untrusted name length and separator processing`() {
        val display = buildString {
            append("SNI: example.com")
            repeat(HUGE_SEPARATOR_COUNT) { append("|part") }
        }.toSingboxProfileDisplay()

        assertEquals("example.com", display.title)
        assertTrue(display.subtitle.length <= MAX_EXPECTED_SUBTITLE_LENGTH)
    }

    @Test
    fun `profile display trims bounded single part names`() {
        val display = ("  " + "a".repeat(HUGE_SINGLE_PART_LENGTH)).toSingboxProfileDisplay()

        assertEquals("a".repeat(EXPECTED_SINGLE_PART_DISPLAY_LENGTH), display.title)
        assertEquals("", display.subtitle)
    }
}
