package ru.ozero.commonvpn.split

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SplitTunnelVpnServiceSentinelTest {

    private val source by lazy {
        val f = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt",
        )
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    private val readSplitConfigBlock by lazy {
        source.substringAfter("private suspend fun readSplitConfig(")
            .substringBefore("private fun startHealthKillswitchWatcher(")
    }

    @Test
    fun `readSplitConfig читает allowlist только в ALLOWLIST режиме`() {
        val allowlistBlock = readSplitConfigBlock.substringBefore("val blocklist")
        assertTrue(
            allowlistBlock.contains("mode == ") && allowlistBlock.contains("ALLOWLIST"),
            "allowlist должен читаться только при mode == ALLOWLIST",
        )
    }

    @Test
    fun `readSplitConfig читает blocklist только в BLOCKLIST режиме`() {
        val blocklistBlock = readSplitConfigBlock.substringAfter("val blocklist")
        assertTrue(
            blocklistBlock.contains("mode == ") && blocklistBlock.contains("BLOCKLIST"),
            "blocklist должен читаться только при mode == BLOCKLIST",
        )
    }

    @Test
    fun `readSplitConfig логирует warn при timeout allowlist`() {
        val allowlistBlock = readSplitConfigBlock.substringBefore("val blocklist")
        assertTrue(
            allowlistBlock.contains("allowlist read timeout"),
            "readSplitConfig обязан логировать warn при fallback allowlist",
        )
    }

    @Test
    fun `readSplitConfig логирует warn при timeout blocklist`() {
        val blocklistBlock = readSplitConfigBlock.substringAfter("val blocklist")
        assertTrue(
            blocklistBlock.contains("blocklist read timeout"),
            "readSplitConfig обязан логировать warn при fallback blocklist",
        )
    }

    @Test
    fun `excludeSelf всегда true — не зависит от engineId`() {
        val tunBlock = source.substringAfter("private suspend fun establishTunForEngine(")
            .substringBefore("private fun captureTunIfaceName(")
        assertTrue(
            tunBlock.contains("excludeSelf = true"),
            "excludeSelf обязан быть true для всех движков без исключений",
        )
        assertTrue(
            !tunBlock.contains("excludeSelf = (engineId"),
            "excludeSelf не должен зависеть от engineId — ранее WARP получал false (баг)",
        )
    }
}
