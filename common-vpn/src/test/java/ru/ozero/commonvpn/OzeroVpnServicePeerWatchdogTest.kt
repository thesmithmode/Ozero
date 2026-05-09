package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServicePeerWatchdogTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `peerWatchJobRef объявлен в сервисе`() {
        assertTrue(
            source.contains("peerWatchJobRef"),
            "OzeroVpnService обязан иметь peerWatchJobRef — job для URnetwork peer watchdog.",
        )
    }

    @Test
    fun `startPeerWatchdog метод существует`() {
        assertTrue(
            source.contains("private fun startPeerWatchdog"),
            "OzeroVpnService обязан иметь private fun startPeerWatchdog — без него URnetwork " +
                "может тихо умереть без handleEngineFailure при killswitch=ON.",
        )
    }

    @Test
    fun `startPeerWatchdog запускается только для usesCustomTun`() {
        val body = source
            .substringAfter("private suspend fun runStartSequence")
            .substringBefore("private fun startHealthKillswitchWatcher")
        assertTrue(
            body.contains("usesCustomTun") && body.contains("startPeerWatchdog"),
            "startPeerWatchdog обязан вызываться условно по usesCustomTun — " +
                "иначе он сломает ByeDPI/WARP где healthMonitor уже работает. Body:\n$body",
        )
        val customTunIdx = body.indexOf("usesCustomTun")
        val watchdogIdx = body.indexOf("startPeerWatchdog")
        assertTrue(
            watchdogIdx > customTunIdx,
            "startPeerWatchdog должен идти ПОСЛЕ проверки usesCustomTun. " +
                "customTunIdx=$customTunIdx watchdogIdx=$watchdogIdx",
        )
    }

    @Test
    fun `peerWatchJobRef отменяется в enterKillswitchMode`() {
        val body = source
            .substringAfter("private fun enterKillswitchMode")
            .substringBefore("private fun stopVpn")
        assertTrue(
            body.contains("peerWatchJobRef.getAndSet(null)"),
            "enterKillswitchMode обязан отменять peerWatchJobRef — иначе watchdog продолжает " +
                "работать после killswitch и может создать race condition. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchJobRef отменяется в stopVpn`() {
        val body = source
            .substringAfter("private fun stopVpn")
            .substringBefore("private fun recordSessionEnd")
        assertTrue(
            body.contains("peerWatchJobRef.getAndSet(null)"),
            "stopVpn обязан отменять peerWatchJobRef — иначе job утечёт после остановки VPN. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog имеет hadPeers grace period`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("hadPeers"),
            "startPeerWatchdog обязан иметь hadPeers флаг — grace period пока URnetwork не " +
                "установил первое соединение. Без него watchdog будет стрелять при каждом старте. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog вызывает handleEngineFailure после таймаута`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("handleEngineFailure"),
            "startPeerWatchdog обязан вызывать handleEngineFailure при sustained 0 peers — " +
                "это входная точка в killswitch/stopVpn логику. Body:\n$body",
        )
        assertTrue(
            body.contains("PEER_WATCHDOG_TIMEOUT_MS"),
            "startPeerWatchdog обязан использовать PEER_WATCHDOG_TIMEOUT_MS константу — " +
                "не хардкодить таймаут. Body:\n$body",
        )
    }

    @Test
    fun `PEER_WATCHDOG_TIMEOUT_MS и PEER_WATCHDOG_POLL_MS константы объявлены`() {
        assertTrue(
            source.contains("PEER_WATCHDOG_TIMEOUT_MS"),
            "OzeroVpnService обязан иметь PEER_WATCHDOG_TIMEOUT_MS константу.",
        )
        assertTrue(
            source.contains("PEER_WATCHDOG_POLL_MS"),
            "OzeroVpnService обязан иметь PEER_WATCHDOG_POLL_MS константу.",
        )
    }
}
