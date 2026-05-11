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
        require("private suspend fun readSplitConfig(" in source) {
            "anchor 'private suspend fun readSplitConfig(' исчез — обнови sentinel"
        }
        require("private fun startHealthKillswitchWatcher(" in source) {
            "anchor 'private fun startHealthKillswitchWatcher(' исчез — обнови sentinel"
        }
        source.substringAfter("private suspend fun readSplitConfig(")
            .substringBefore("private fun startHealthKillswitchWatcher(")
    }

    private val tunBlock by lazy {
        require("private suspend fun establishTunForEngine(" in source) {
            "anchor 'private suspend fun establishTunForEngine(' исчез — обнови sentinel"
        }
        require("private fun captureTunIfaceName(" in source) {
            "anchor 'private fun captureTunIfaceName(' исчез — обнови sentinel"
        }
        source.substringAfter("private suspend fun establishTunForEngine(")
            .substringBefore("private fun captureTunIfaceName(")
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
    fun `excludeSelf false для WARP — self-traffic через TUN для корректного IP-probe`() {
        assertTrue(
            tunBlock.contains("engineId != EngineId.WARP"),
            "excludeSelf обязан быть false для WARP: self-traffic должен идти через TUN " +
                "иначе IP-probe вернёт реальный IP устройства вместо WARP exit IP",
        )
    }

    @Test
    fun `excludeSelf не константа true — зависит от engineId`() {
        assertTrue(
            !tunBlock.contains("excludeSelf = true"),
            "excludeSelf не должен быть константой true — WARP требует false (IpProbeRoute.Default design)",
        )
    }
}
