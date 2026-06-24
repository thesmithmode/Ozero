package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingboxProbeNetworkSecuritySentinelTest {

    @Test
    fun `network security allows only routed HTTP probe hosts`() {
        val root = locateRepoRoot()
        val config = File(root, "app/src/main/res/xml/network_security_config.xml")
        assertTrue(config.isFile, "network_security_config.xml must exist")

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config)
        val rootElement = document.documentElement
        val baseConfig = rootElement.elements("base-config").single()
        val cleartextDomainConfigs = rootElement.elements("domain-config")
            .filter { it.getAttribute("cleartextTrafficPermitted") == "true" }
        val cleartextDomains = cleartextDomainConfigs
            .flatMap { configElement -> configElement.elements("domain") }
            .map { domain -> domain.textContent.trim() to domain.getAttribute("includeSubdomains") }
            .toSet()

        val baseAnchors = baseConfig.elements("trust-anchors").single().elements("certificates")
            .map { it.getAttribute("src") }
            .toSet()

        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))
        assertEquals(setOf("system"), baseAnchors)
        assertEquals(
            setOf(
                "connectivitycheck.gstatic.com" to "false",
                "cp.cloudflare.com" to "false",
            ),
            cleartextDomains,
        )
    }

    private fun Element.elements(name: String): List<Element> = (0 until childNodes.length)
        .map { index -> childNodes.item(index) }
        .filterIsInstance<Element>()
        .filter { element -> element.tagName == name }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
