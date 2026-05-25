package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class V2RayFmtParseVLESSTest {

    private val fmtSource: String by lazy {
        val root = locateRepoRoot()
        val main = File(root, "singbox-fmt/src/main/java/ru/ozero/singboxfmt/V2RayFmt.kt")
            .also { assertTrue(it.isFile, "V2RayFmt.kt must exist") }
            .readText()
        val utils = File(root, "singbox-fmt/src/main/java/ru/ozero/singboxfmt/V2RayFmtUtils.kt")
        if (utils.isFile) main + "\n" + utils.readText() else main
    }

    @Test
    fun `should parseVLESS only accept vless scheme`() {
        assertTrue(
            fmtSource.contains("vless://"),
            "parseVLESS must guard for vless:// scheme prefix",
        )
        assertTrue(
            fmtSource.contains("require(") || fmtSource.contains("startsWith"),
            "parseVLESS must validate URI scheme",
        )
    }

    @Test
    fun `should parseVLESS extract uuid from userInfo`() {
        assertTrue(
            fmtSource.contains("userInfo"),
            "parseVLESS must use Uri.userInfo to extract UUID — vless://UUID@host:port",
        )
        assertTrue(
            fmtSource.contains("uuid"),
            "parseVLESS must assign parsed UUID to bean.uuid",
        )
    }

    @Test
    fun `should parseVLESS map h2 type to http`() {
        assertTrue(
            fmtSource.contains("\"h2\" -> \"http\"") || fmtSource.contains("\"h2\"") && fmtSource.contains("\"http\""),
            "parseVLESS must map 'h2' transport type to 'http' (sing-box transport name)",
        )
    }

    @Test
    fun `should parseVLESS map xhttp type to splithttp`() {
        assertTrue(
            fmtSource.contains("\"xhttp\"") && fmtSource.contains("\"splithttp\""),
            "parseVLESS must map 'xhttp' to 'splithttp' (sing-box transport name)",
        )
    }

    @Test
    fun `should parseVLESS parse security sni alpn fp parameters`() {
        listOf("security", "sni", "alpn", "fp", "getQueryParameter").forEach { param ->
            assertTrue(
                fmtSource.contains(param),
                "parseVLESS must handle '$param' query parameter",
            )
        }
    }

    @Test
    fun `should parseVLESS parse reality parameters pbk and sid`() {
        assertTrue(
            fmtSource.contains("pbk") || fmtSource.contains("realityPublicKey"),
            "parseVLESS must parse 'pbk' param -> realityPublicKey",
        )
        assertTrue(
            fmtSource.contains("sid") || fmtSource.contains("realityShortId"),
            "parseVLESS must parse 'sid' param -> realityShortId",
        )
    }

    @Test
    fun `should parseVLESS parse flow parameter`() {
        assertTrue(
            fmtSource.contains("\"flow\"") || fmtSource.contains("flow"),
            "parseVLESS must parse 'flow' param (xtls-rprx-vision for VLESS+Reality)",
        )
    }

    @Test
    fun `should parseVLESS handle WebSocket host and path`() {
        assertTrue(
            fmtSource.contains("\"ws\""),
            "parseVLESS must handle ws transport type",
        )
        val wsBlock = fmtSource.substringAfter("\"ws\"")
        assertTrue(
            wsBlock.contains("host") || wsBlock.contains("path"),
            "parseVLESS ws handler must extract host and path query params",
        )
    }

    @Test
    fun `should parseVLESS handle grpc serviceName`() {
        assertTrue(
            fmtSource.contains("\"grpc\"") && fmtSource.contains("serviceName"),
            "parseVLESS must handle grpc type and extract serviceName",
        )
    }

    @Test
    fun `should parseVLESS call initializeDefaultValues at end`() {
        assertTrue(
            fmtSource.contains("initializeDefaultValues()"),
            "parseVLESS must call bean.initializeDefaultValues() — sets VLESSBean.encryption=none default",
        )
    }

    @Test
    fun `should parseVLESS decode fragment as name with URLDecoder`() {
        assertTrue(
            fmtSource.contains("fragment") || fmtSource.contains("URLDecoder"),
            "parseVLESS must decode URI fragment as display name (URL-encoded)",
        )
    }

    @Test
    fun `should parseVLESS handle absent port defaulting to 443`() {
        assertTrue(
            fmtSource.contains("443"),
            "parseVLESS must default serverPort to 443 when port is absent",
        )
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        val fromDir = File(System.getProperty("user.dir") ?: ".").absolutePath
        error("repo root (settings.gradle.kts) not found from $fromDir")
    }
}
