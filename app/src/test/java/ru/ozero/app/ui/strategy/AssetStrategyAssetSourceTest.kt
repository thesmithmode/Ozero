package ru.ozero.app.ui.strategy

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AssetStrategyAssetSourceTest {

    @Test
    fun `loadSites trims blank lines and preserves order`() {
        val source = source(
            sites = """
                example.com

                  api.example.org
                cdn.example.net
            """.trimIndent(),
        )

        val sites = source.loadSites()

        assertEquals(listOf("example.com", "api.example.org", "cdn.example.net"), sites)
    }

    @Test
    fun `loadSites returns empty list for blank asset`() {
        val source = source(sites = " \n\t\n")

        val sites = source.loadSites()

        assertEquals(emptyList(), sites)
    }

    @Test
    fun `loadStrategies parses commands and replaces sni placeholder`() {
        val source = source(
            strategies = """
                -n {sni} -Qr -a1
                -d1 -a1
            """.trimIndent(),
            sni = "video.example.com",
        )

        val commands = source.loadStrategies()

        assertEquals(listOf("-n \"video.example.com\" -Qr -a1", "-d1 -a1"), commands)
    }

    @Test
    fun `loadStrategies skips comments and blank lines`() {
        val source = source(
            strategies = """
                # first
                -d1 -a1

                # second
                -o2 -a1
            """.trimIndent(),
        )

        val commands = source.loadStrategies()

        assertEquals(listOf("-d1 -a1", "-o2 -a1"), commands)
    }

    private fun source(
        strategies: String = "",
        sites: String = "",
        sni: String = "google.com",
    ): AssetStrategyAssetSource {
        val assets = mockk<AssetManager>()
        every { assets.open("proxytest_strategies.list") } answers { strategies.byteInputStream() }
        every { assets.open("proxytest_general.sites") } answers { sites.byteInputStream() }
        val context = mockk<Context>()
        every { context.assets } returns assets
        return AssetStrategyAssetSource(context, sni)
    }
}
