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
    fun `anchors — все функции-границы существуют в источнике`() {
        listOf(
            "private fun applyLockdown",
            "private fun blackholeIpv6",
            "internal fun applyEngineTunSpec",
            "internal fun buildTunBuilder",
            "override fun onRevoke()",
            "private suspend fun runStartSequence",
            "private fun startHealthKillswitchWatcher",
            "private fun enterKillswitchMode",
            "private fun stopVpn",
            "private fun recordSessionEnd",
        ).forEach { anchor ->
            assertTrue(source.contains(anchor), "Anchor потерян в OzeroVpnService.kt: '$anchor'")
        }
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
            .substringBefore("override fun onRevoke()")
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

    @Test
    fun `lockdownStartupFdRef объявлен в сервисе`() {
        assertTrue(
            source.contains("lockdownStartupFdRef"),
            "OzeroVpnService обязан иметь lockdownStartupFdRef — instant lockdown TUN fd " +
                "для закрытия startup race gap при killswitch=ON.",
        )
    }

    @Test
    fun `lockdownStartupTun устанавливается до выбора движка в runStartSequence`() {
        val body = source
            .substringAfter("private suspend fun runStartSequence")
            .substringBefore("private fun startHealthKillswitchWatcher")
        val lockdownIdx = body.indexOf("lockdownStartupFdRef.set")
        val pickIdx = body.indexOf("pickAutoCandidateWithPreflight")
        assertTrue(lockdownIdx >= 0, "lockdownStartupFdRef.set не найден в runStartSequence. Body:\n$body")
        assertTrue(pickIdx >= 0, "pickAutoCandidateWithPreflight не найден в runStartSequence. Body:\n$body")
        assertTrue(
            lockdownIdx < pickIdx,
            "lockdownStartupFdRef.set обязан быть РАНЬШЕ pickAutoCandidateWithPreflight — " +
                "иначе startup gap не закрыт. lockdownIdx=$lockdownIdx pickIdx=$pickIdx",
        )
    }

    @Test
    fun `lockdownStartupFdRef закрывается после установки реального TUN в runStartSequence`() {
        val body = source
            .substringAfter("private suspend fun runStartSequence")
            .substringBefore("private fun startHealthKillswitchWatcher")
        val establishIdx = body.indexOf("establishTun")
        val clearIdx = body.indexOf("lockdownStartupFdRef.getAndSet(null)")
        assertTrue(establishIdx >= 0, "establishTun не найден в runStartSequence. Body:\n$body")
        assertTrue(clearIdx >= 0, "lockdownStartupFdRef.getAndSet(null) не найден в runStartSequence.")
        assertTrue(
            clearIdx > establishIdx,
            "lockdownStartupFdRef должен очищаться ПОСЛЕ establish реального TUN. " +
                "establishIdx=$establishIdx clearIdx=$clearIdx",
        )
    }

    @Test
    fun `lockdownStartupFdRef очищается в stopVpn`() {
        val body = source
            .substringAfter("private fun stopVpn")
            .substringBefore("private fun recordSessionEnd")
        assertTrue(
            body.contains("lockdownStartupFdRef.getAndSet(null)"),
            "stopVpn обязан закрывать lockdownStartupFdRef — иначе fd утечёт при остановке VPN до старта движка.",
        )
    }
}
