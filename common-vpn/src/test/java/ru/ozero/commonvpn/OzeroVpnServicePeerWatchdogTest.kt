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
    fun `enterKillswitchMode отменяет peerWatchJobRef в координаторе`() {
        val body = watchdogSource
            .substringAfter("private fun enterKillswitchMode")
            .substringBefore("companion object")
        assertTrue(
            body.contains("peerWatchJobRef.getAndSet(null)"),
            "enterKillswitchMode обязан отменять peerWatchJobRef — иначе watchdog продолжает " +
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
    fun `peerWatchdog НЕ вызывает handleEngineFailure при Failed — бесконечный retry`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("PEER_WATCHDOG_TIMEOUT_MS"),
            "startPeerWatchdog обязан использовать PEER_WATCHDOG_TIMEOUT_MS константу — " +
                "не хардкодить таймаут. Body:\n$body",
        )
        assertTrue(
            !body.contains("handleEngineFailure"),
            "startPeerWatchdog body НЕ должен вызывать handleEngineFailure — юзер хочет бесконечный " +
                "retry с жёлтой кнопкой. Регрессия 2026-05-20 (da4e2cda): handleEngineFailure → красная " +
                "кнопка вместо жёлтой. handleEngineFailure живёт только в enterKillswitchMode/health-watcher. " +
                "Body:\n$body",
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
    fun `peerWatchdog не имеет жёсткого лимита recover-попыток — бесконечный retry до NotSupported`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        assertTrue(
            !body.contains("PEER_WATCHDOG_MAX_RECOVERS"),
            "startPeerWatchdog не должен иметь MAX_RECOVERS лимит — watchdog обязан бесконечно " +
                "переподключаться пока RecoverResult != NotSupported. Пауза между попытками " +
                "обеспечена zeroPeersSince=0L + PEER_WATCHDOG_TIMEOUT_MS. Body:\n$body",
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
    fun `peerWatchdog после Success сбрасывает zeroPeersSince в 0L`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
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
    fun `peerWatchdog имеет zeroPeersSince сброс после Failed — обеспечивает паузу между retry`() {
        val body = watchdogSource
            .substringAfter("fun startPeerWatchdog")
            .substringBefore("fun cancelWatchers")
        val failedBlock = body.substringAfter("RecoverResult.Failed ->")
            .substringBefore("delay(PEER_WATCHDOG_RECOVER_GRACE_MS)")
        assertTrue(
            failedBlock.contains("zeroPeersSince = 0L"),
            "После Failed обязан zeroPeersSince=0L — без сброса следующий poll сразу снова " +
                "trigger recover без ожидания PEER_WATCHDOG_TIMEOUT_MS. Block:\n$failedBlock",
        )
    }
}
