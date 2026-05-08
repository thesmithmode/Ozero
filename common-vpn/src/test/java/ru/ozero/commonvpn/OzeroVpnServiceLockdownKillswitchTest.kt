package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceLockdownKillswitchTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `applyLockdown существует и вызывает setUnderlyingNetworks null`() {
        assertTrue(
            source.contains("private fun applyLockdown"),
            "OzeroVpnService обязан иметь private fun applyLockdown — Amnezia/PORTAL_WG pattern: " +
                "setUnderlyingNetworks(null) запрещает OS использовать underlying network вне TUN. " +
                "Без него VPN не enforces lockdown — трафик утекает мимо туннеля при degraded engine.",
        )
        val body = source
            .substringAfter("private fun applyLockdown")
            .substringBefore("private fun blackholeIpv6")
        assertTrue(
            body.contains("setUnderlyingNetworks(null)"),
            "applyLockdown обязан вызывать setUnderlyingNetworks(null). Body:\n$body",
        )
    }

    @Test
    fun `applyEngineTunSpec вызывает applyLockdown`() {
        val body = source
            .substringAfter("internal fun applyEngineTunSpec")
            .substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("applyLockdown(builder"),
            "applyEngineTunSpec обязан вызывать applyLockdown — иначе TUN строится без " +
                "setUnderlyingNetworks(null) и не enforces lockdown. Body:\n$body",
        )
    }

    @Test
    fun `buildTunBuilder вызывает applyLockdown`() {
        val body = source
            .substringAfter("internal fun buildTunBuilder")
            .substringBefore("private fun buildNotification")
        assertTrue(
            body.contains("applyLockdown(builder"),
            "buildTunBuilder обязан вызывать applyLockdown — для ByeDPI/non-customTun engines.",
        )
    }

    @Test
    fun `runStartSequence запускает health killswitch watcher`() {
        val body = source
            .substringAfter("private suspend fun runStartSequence")
            .substringBefore("private fun startHealthKillswitchWatcher")
        assertTrue(
            body.contains("startHealthKillswitchWatcher("),
            "runStartSequence обязан стартовать health watcher — иначе HealthMonitor.DEGRADED " +
                "не триггерит killswitch и движок продолжает «бутафорное» состояние.",
        )
    }

    @Test
    fun `startHealthKillswitchWatcher триггерит enterKillswitchMode при DEGRADED + killswitchCached`() {
        val body = source
            .substringAfter("private fun startHealthKillswitchWatcher")
            .substringBefore("private fun enterKillswitchMode")
        assertTrue(
            body.contains("HealthMonitor.Status.DEGRADED"),
            "watcher обязан фильтровать DEGRADED. Body:\n$body",
        )
        assertTrue(
            body.contains("enterKillswitchMode(engineId"),
            "watcher обязан вызывать enterKillswitchMode при degraded. Body:\n$body",
        )
        assertTrue(
            body.contains("killswitchCached"),
            "watcher обязан проверять killswitchCached перед fire. Body:\n$body",
        )
    }
}
