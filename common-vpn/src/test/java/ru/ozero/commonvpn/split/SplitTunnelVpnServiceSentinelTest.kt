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

    private val buildTunBlock by lazy {
        require("internal fun buildTunBuilder(" in source) {
            "anchor 'internal fun buildTunBuilder(' исчез — обнови sentinel"
        }
        require("private fun buildNotification(" in source) {
            "anchor 'private fun buildNotification(' исчез — обнови sentinel"
        }
        source.substringAfter("internal fun buildTunBuilder(")
            .substringBefore("private fun buildNotification(")
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
    fun `establishTunForEngine использует excludeSelf = true для всех движков`() {
        assertTrue(
            tunBlock.contains("excludeSelf = true"),
            "excludeSelf обязан быть true для всех движков без исключений — " +
                "без addDisallowedApplication Android не активирует per-app VPN mode " +
                "и трафик самого приложения попадает в TUN, ломая инициализацию движков " +
                "(особенно WARP AWG). Регрессия commit 5a8089dd: условный excludeSelf для WARP " +
                "ради IP-probe сломал split tunnel ALL mode.",
        )
    }

    @Test
    fun `common-vpn не знает про конкретные движки в split tunnel логике`() {
        assertTrue(
            !tunBlock.contains("EngineId.WARP") &&
                !tunBlock.contains("EngineId.BYEDPI") &&
                !tunBlock.contains("EngineId.URNETWORK"),
            "establishTunForEngine не должен ссылаться на конкретные EngineId — " +
                "common-vpn — VPN core инфраструктура, не должен знать про детали движков " +
                "(нарушение модульности и low coupling). Engine-specific поведение " +
                "(IP-probe, capabilities) выражается через EnginePlugin contract.",
        )
    }

    @Test
    fun `buildTunBuilder не имеет параметра engineId — excludeSelf всегда true`() {
        val sig = source.substringAfter("internal fun buildTunBuilder(")
            .substringBefore("): Builder")
        assertTrue(
            !sig.contains("engineId"),
            "buildTunBuilder не должен принимать engineId — вызывается только для не-TunFdAcceptor движков " +
                "и killswitch TUN; excludeSelf = true там постоянен и не зависит от движка",
        )
    }

    @Test
    fun `buildTunBuilder использует excludeSelf = true для не-WARP движков`() {
        assertTrue(
            buildTunBlock.contains("excludeSelf = true"),
            "buildTunBuilder обязан использовать excludeSelf = true — не-TunFdAcceptor движки " +
                "не вызывают protect() на outbound сокетах, иначе routing loop через TUN",
        )
    }
}
