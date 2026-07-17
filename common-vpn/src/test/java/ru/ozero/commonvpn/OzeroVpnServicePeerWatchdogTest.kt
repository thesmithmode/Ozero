package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServicePeerWatchdogTest {

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    private val startSequenceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    private val watchdogSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/EngineWatchdogCoordinator.kt")
        assertTrue(f.exists(), "EngineWatchdogCoordinator.kt не найден: $f")
        f.readText()
    }

    private val shutdownSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/ShutdownCoordinator.kt")
        assertTrue(f.exists(), "ShutdownCoordinator.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `anchors — все функции-границы существуют в источнике`() {
        listOf(
            "fun stopVpn(",
            "private fun recordSessionEnd(",
        ).forEach { anchor ->
            assertTrue(shutdownSource.contains(anchor), "Anchor потерян в ShutdownCoordinator.kt: '$anchor'")
        }
        listOf(
            "suspend fun run()",
            "private suspend fun readSplitConfig",
            "suspend fun engineNeedsCustomTun",
        ).forEach { anchor ->
            assertTrue(
                startSequenceSource.contains(anchor),
                "Anchor потерян в StartSequenceCoordinator.kt: '$anchor'",
            )
        }
        listOf(
            "fun startHealthKillswitchWatcher(",
            "fun startPeerWatchdog(",
            "private fun enterKillswitchMode(",
            "fun handleEngineFailure(",
            "fun cancelWatchers(",
        ).forEach { anchor ->
            assertTrue(
                watchdogSource.contains(anchor),
                "Anchor потерян в EngineWatchdogCoordinator.kt: '$anchor'",
            )
        }
    }

    @Test
    fun `engineWatchdog инжектится в сервис`() {
        assertTrue(
            serviceSource.contains("engineWatchdog"),
            "OzeroVpnService обязан иметь engineWatchdog — координатор watch jobs.",
        )
        assertTrue(
            serviceSource.contains("EngineWatchdogCoordinator("),
            "OzeroVpnService обязан строить EngineWatchdogCoordinator в by lazy.",
        )
    }

    @Test
    fun `startPeerWatchdog запускается только для usesCustomTun`() {
        val body = startSequenceSource
            .substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
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
    fun `enterKillswitchMode отменяет все watchJobRef в координаторе`() {
        val body = watchdogSource
            .substringAfter("private fun enterKillswitchMode")
            .substringBefore("companion object")
        assertTrue(
            body.contains("peerWatchJobRef.getAndSet(null)") &&
                body.contains("stagnationWatchJobRef.getAndSet(null)"),
            "enterKillswitchMode обязан отменять все watchdog jobs — иначе watchdog продолжает " +
                "работать после killswitch и может создать race condition. Body:\n$body",
        )
    }

    @Test
    fun `cancelWatchers вызывается из stopVpn`() {
        val body = shutdownSource
            .substringAfter("fun stopVpn(")
            .substringBefore("suspend fun performShutdown(")
        assertTrue(
            body.contains("deps.engineWatchdog.cancelWatchers()"),
            "stopVpn обязан вызвать engineWatchdog.cancelWatchers() — иначе watcher jobs утекут. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog имеет hadPeers grace period`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("hadPeers"),
            "startPeerWatchdog обязан иметь hadPeers флаг — grace period пока URnetwork не " +
                "установил первое соединение. Без него watchdog будет стрелять при каждом старте. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog calls handleEngineFailure after repeated Failed recover`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("peerWatchdogPolicy.timeoutMs"),
            "startPeerWatchdog must read timeout from engine peerWatchdogPolicy, not hardcode engine branches.",
        )
        assertTrue(
            body.contains("consecutiveRecoverFailures") &&
                body.contains("PEER_WATCHDOG_MAX_FAILED_RECOVERS") &&
                body.contains("handleEngineFailure"),
            "Persistent Failed recover must eventually reach handleEngineFailure so a dead custom-TUN " +
                "engine cannot stay active forever. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog delegates runtime peer grace to engine policy`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun startStagnationWatchdog")
        assertTrue(
            body.contains("plugin.peerWatchdogPolicy()") &&
                body.contains("peerWatchdogPolicy.timeoutMs"),
            "Runtime peer grace must come from EnginePlugin policy, not from common-vpn EngineId branches.",
        )
        assertTrue(
            body.contains("!hadPeers && !peerWatchdogPolicy.recoverBeforeFirstPeer"),
            "Initial zero-peer recovery behavior must be controlled by engine policy.",
        )
    }

    @Test
    fun `PEER_WATCHDOG_TIMEOUT_MS и PEER_WATCHDOG_POLL_MS константы объявлены`() {
        assertTrue(
            watchdogSource.contains("PEER_WATCHDOG_TIMEOUT_MS"),
            "EngineWatchdogCoordinator обязан иметь PEER_WATCHDOG_TIMEOUT_MS константу.",
        )
        assertTrue(
            watchdogSource.contains("PEER_WATCHDOG_POLL_MS"),
            "EngineWatchdogCoordinator обязан иметь PEER_WATCHDOG_POLL_MS константу.",
        )
    }

    @Test
    fun `peerWatchdog has bounded Failed recover attempts`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("PEER_WATCHDOG_MAX_FAILED_RECOVERS") && body.contains("consecutiveRecoverFailures"),
            "startPeerWatchdog must bound consecutive Failed recover attempts before terminal " +
                "failure handling. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog использует poll counter не System currentTimeMillis — virtual time совместимость`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            !body.contains("System.currentTimeMillis"),
            "startPeerWatchdog НЕ должен использовать System.currentTimeMillis — wall clock " +
                "несовместим с runTest virtual dispatcher (advanceTimeBy не двигает real time → " +
                "wall-clock delta остаётся ~0 → recover никогда не triggers в тесте → " +
                "EngineWatchdogInfiniteRetryTest падает). Использовать счётчик polls вместо. Body:\n$body",
        )
        assertTrue(
            body.contains("zeroPeersPolls"),
            "startPeerWatchdog обязан иметь zeroPeersPolls счётчик — количество подряд idle " +
                "опросов. Триггер recover когда zeroPeersPolls * POLL_MS >= TIMEOUT_MS. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog вызывает plugin recover при таймауте`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("plugin.recover()"),
            "startPeerWatchdog обязан вызывать plugin.recover() перед handleEngineFailure — " +
                "иначе watchdog убивает соединение без попытки восстановления. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog обрабатывает все три варианта RecoverResult`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("RecoverResult.Success") &&
                body.contains("RecoverResult.NotSupported") &&
                body.contains("RecoverResult.Failed"),
            "startPeerWatchdog обязан явно обрабатывать все три RecoverResult — иначе when " +
                "не exhaustive после расширения. Body:\n$body",
        )
    }

    @Test
    fun `peerWatchdog при NotSupported останавливает watchdog но НЕ убивает VPN`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        val notSupportedBlock = body.substringAfter("RecoverResult.NotSupported")
            .substringBefore("RecoverResult.Failed")
        assertTrue(
            !notSupportedBlock.contains("handleEngineFailure"),
            "NotSupported → НЕ handleEngineFailure. NotSupported = 'engine не поддерживает recover', " +
                "не 'engine сломан' — VPN продолжает работать. Регрессия 2026-05-20 (da4e2cda): " +
                "handleEngineFailure → юзер видел красную кнопку и должен дёргать заново. " +
                "Block:\n$notSupportedBlock",
        )
        assertTrue(
            notSupportedBlock.contains("return@launch"),
            "NotSupported → return@launch — watchdog stop, дальше VPN сам. Block:\n$notSupportedBlock",
        )
    }

    @Test
    fun `peerWatchdog после Success сбрасывает zeroPeersPolls в 0`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        val successBlock = body.substringAfter("RecoverResult.Success ->")
            .substringBefore("RecoverResult.NotSupported")
        assertTrue(
            successBlock.contains("zeroPeersPolls = 0"),
            "После Success обязан zeroPeersPolls=0 — без сброса следующая итерация после grace " +
                "сработает мгновенно (counter уже превысил TIMEOUT) и watchdog зашпарит retry " +
                "без реальной паузы. Block:\n$successBlock",
        )
    }

    @Test
    fun `peerWatchdog имеет zeroPeersPolls сброс после Failed — обеспечивает паузу между retry`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        val failedBlock = body.substringAfter("RecoverResult.Failed ->")
            .substringBefore("delay(PEER_WATCHDOG_RECOVER_GRACE_MS)")
        assertTrue(
            failedBlock.contains("zeroPeersPolls = 0"),
            "После Failed обязан zeroPeersPolls=0 — без сброса следующий poll сразу снова " +
                "trigger recover без ожидания PEER_WATCHDOG_TIMEOUT_MS / PEER_WATCHDOG_POLL_MS polls. " +
                "Block:\n$failedBlock",
        )
    }
}
