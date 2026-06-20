package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxProbeNetworkSecuritySentinelTest {

    @Test
    fun `network security allows only routed HTTP probe hosts`() {
        val root = locateRepoRoot()
        val config = File(root, "app/src/main/res/xml/network_security_config.xml")
        assertTrue(config.isFile, "network_security_config.xml must exist")
        val content = config.readText()

        assertTrue(
            content.contains("<base-config cleartextTrafficPermitted=\"false\">"),
            "base cleartext policy must stay disabled",
        )
        assertTrue(
            content.contains("<domain includeSubdomains=\"false\">connectivitycheck.gstatic.com</domain>"),
            "connectivitycheck.gstatic.com must allow HTTP 204 routed probe",
        )
        assertTrue(
            content.contains("<domain includeSubdomains=\"false\">cp.cloudflare.com</domain>"),
            "cp.cloudflare.com must allow HTTP 204 routed probe fallback",
        )
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
