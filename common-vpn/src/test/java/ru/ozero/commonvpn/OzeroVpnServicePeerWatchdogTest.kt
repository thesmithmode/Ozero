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

    @Test
    fun `PEER_WATCHDOG_MAX_RECOVERS обязан быть 3 — не менять без оценки агрессивности retry`() {
        val match = Regex("PEER_WATCHDOG_MAX_RECOVERS\\s*=\\s*(\\d+)").find(source)
        assertTrue(match != null, "PEER_WATCHDOG_MAX_RECOVERS константа обязана быть в файле")
        val value = match!!.groupValues[1].toInt()
        assertTrue(
            value == 3,
            "PEER_WATCHDOG_MAX_RECOVERS обязан = 3 (текущий $value): больше — DoS на bridge при " +
                "затяжных сетевых проблемах; меньше — bridge не успеет recover до handleEngineFailure",
        )
    }

    @Test
    fun `peerWatchdog вызывает plugin recover при таймауте`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("plugin.recover()"),
            "startPeerWatchdog обязан вызывать plugin.recover() перед handleEngineFailure — " +
                "иначе watchdog убивает соединение без попытки восстановления. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog обрабатывает все три варианта RecoverResult`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("RecoverResult.Success") &&
                body.contains("RecoverResult.NotSupported") &&
                body.contains("RecoverResult.Failed"),
            "startPeerWatchdog обязан явно обрабатывать все три RecoverResult — иначе when " +
                "не exhaustive после расширения. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog при NotSupported делает handleEngineFailure без retry`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        val notSupportedBlock = body.substringAfter("RecoverResult.NotSupported")
            .substringBefore("RecoverResult.Failed")
        assertTrue(
            notSupportedBlock.contains("handleEngineFailure") && notSupportedBlock.contains("return@launch"),
            "NotSupported = recover не предусмотрен → сразу fail-fast handleEngineFailure + return@launch. " +
                "Block:\n$notSupportedBlock",
        )
    }

    @Test
    fun `peerWatchdog после Success сбрасывает zeroPeersSince в 0L`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        val successBlock = body.substringAfter("RecoverResult.Success ->")
            .substringBefore("RecoverResult.NotSupported")
        assertTrue(
            successBlock.contains("zeroPeersSince = 0L"),
            "После Success обязан zeroPeersSince=0L — без сброса следующая итерация после grace " +
                "сработает мгновенно (now - zeroPeersSince > TIMEOUT) и watchdog зашпарит retry " +
                "без реальной паузы. Block:\n$successBlock",
        )
    }

    @Test
    fun `peerWatchdog содержит recoverAttempts счётчик с ограничителем`() {
        val body = source
            .substringAfter("private fun startPeerWatchdog")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("recoverAttempts"),
            "startPeerWatchdog обязан иметь recoverAttempts — счётчик попыток recovery. Body:\n$body",
        )
        assertTrue(
            body.contains("recoverAttempts >= PEER_WATCHDOG_MAX_RECOVERS"),
            "Должна быть проверка recoverAttempts >= PEER_WATCHDOG_MAX_RECOVERS перед " +
                "handleEngineFailure — иначе retry бесконечен. Body:\n$body",
        )
    }
}
