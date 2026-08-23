package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxSoakContractTest {

    @Test
    fun `soak uses arm64 app build and exercises real VPN traffic`() {
        val root = locateRepoRoot()
        val workflow = File(root, ".github/workflows/soak.yml").readText()
        val runner = File(root, ".github/soak/run-singbox-tun-soak.sh").readText()
        val soak = File(
            root,
            "app/src/androidTest/java/ru/ozero/app/soak/SoakTest.kt",
        ).readText()
        val manifest = File(root, "app/src/androidTest/AndroidManifest.xml").readText()

        assertTrue(workflow.contains("arch: arm64-v8a"))
        assertFalse(workflow.contains("arch: x86_64"))
        assertTrue(workflow.contains(":app:assembleDebugAndroidTest"))
        assertTrue(workflow.contains("OZERO_SOAK_VLESS"))
        assertTrue(workflow.contains("OZERO_SOAK_VMESS"))
        assertTrue(workflow.contains("OZERO_SOAK_TROJAN"))
        assertTrue(runner.contains("OK (1 test)"))
        assertTrue(runner.contains("successful_cycles"))
        assertTrue(runner.contains("inbound connection"))
        assertTrue(soak.contains("OzeroVpnService.ACTION_START"))
        assertTrue(soak.contains("TunnelState.Connected"))
        assertTrue(soak.contains("probeFromExternalUid"))
        assertTrue(soak.contains("vpnTransport"))
        assertTrue(soak.contains("markerMatch"))
        assertFalse(soak.contains("fun doHttpRequest"))
        assertTrue(manifest.contains("SoakExternalProbeService"))
        assertTrue(manifest.contains("android:process=\":soak_probe\""))
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
