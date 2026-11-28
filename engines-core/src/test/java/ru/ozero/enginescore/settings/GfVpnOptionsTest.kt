package ru.ozero.enginescore.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GfVpnOptionsTest {
    @Test
    fun `settings round trip and invalid data fallback`() {
        val options = GfVpnOptions("performance", "H2", 3, 7, "false", false, false, true)
        assertEquals(options, GfVpnOptions.decode(options.encode()))
        assertEquals(GfVpnOptions.DEFAULT, GfVpnOptions.decode("invalid"))
        assertEquals(GfVpnOptions.DEFAULT, GfVpnOptions.decode("auto|H2|99|7||true|true|false"))
    }

    @Test
    fun `whitelist and VK TURN own concurrency settings`() {
        val options = GfVpnOptions("whitelist-ru", "H2", 3, 7, "true")
        assertEquals(GfVpnOptions("whitelist-ru"), options.effective())
        assertEquals(
            GfVpnOptions(transport = "VKT"),
            options.copy(serverSelection = "auto", transport = "VKT").effective(),
        )
    }

    @Test
    fun `reference country and server choices survive persistence`() {
        assertEquals(20, GfVpnServerCatalog.countries.size)
        assertEquals(57, GfVpnServerCatalog.countries.values.sumOf { it.size })
        assertEquals(13, GfVpnServerCatalog.countries.getValue("RU0").size)
        GfVpnServerCatalog.countries.forEach { (country, servers) ->
            (listOf(country) + servers).forEach { selection ->
                assertEquals(country, GfVpnServerCatalog.countryFor(selection))
                val options = GfVpnOptions(serverSelection = selection)
                assertEquals(options, GfVpnOptions.decode(options.encode()))
                assertEquals(selection, options.effective().serverSelection)
            }
        }
        assertEquals(GfVpnOptions.DEFAULT, GfVpnOptions.decode("ZZ|H2|0|0||true|true|false"))
    }
}
