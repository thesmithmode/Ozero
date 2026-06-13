package ru.ozero.enginefptn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ExitNodeStrategy
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FptnExitNodeStrategyTest {
    @Test
    fun `country label respects requested display locale`() {
        val english = fptnExitNodeStrategy(
            server = server().copy(countryCode = "de"),
            serverIp = "198.51.100.70",
            displayLocale = Locale.ENGLISH,
        ) as ExitNodeStrategy.ProviderLabel
        val spanish = fptnExitNodeStrategy(
            server = server().copy(countryCode = "de"),
            serverIp = "198.51.100.70",
            displayLocale = Locale("es"),
        ) as ExitNodeStrategy.ProviderLabel

        assertEquals("Germany", english.label)
        assertEquals("Alemania", spanish.label)
        assertEquals("DE", english.countryCode)
        assertEquals("198.51.100.70", english.ip)
    }

    @Test
    fun `invalid country code falls back to token server name`() {
        val strategy = fptnExitNodeStrategy(
            server = server().copy(name = "FPTN-1", countryCode = "??"),
            serverIp = null,
            displayLocale = Locale.ENGLISH,
        )

        val label = assertIs<ExitNodeStrategy.ProviderLabel>(strategy)
        assertEquals("FPTN-1", label.label)
        assertEquals(null, label.countryCode)
        assertEquals(null, label.ip)
    }

    private fun server(): FptnServer =
        FptnServer(
            name = "S",
            host = "s.example.com",
            port = 443,
            md5Fingerprint = "",
            countryCode = "",
        )
}
