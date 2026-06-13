package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class DomainListAssetsContractTest {

    private val projectRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun `youtube lite includes googlevideo media probe`() {
        val sites = assetLines("proxytest_youtube_lite.sites")
        assertTrue("manifest.googlevideo.com" in sites)
    }

    @Test
    fun `googlevideo list includes direct media cdn probes`() {
        val sites = assetLines("proxytest_googlevideo.sites")
        assertTrue("manifest.googlevideo.com" in sites)
        assertTrue(sites.any { it.endsWith(".googlevideo.com") && it != "manifest.googlevideo.com" })
    }

    private fun assetLines(name: String): List<String> =
        listOf(
            File(projectRoot, "src/main/assets/$name"),
            File(projectRoot, "app/src/main/assets/$name"),
        ).first { it.exists() }.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
