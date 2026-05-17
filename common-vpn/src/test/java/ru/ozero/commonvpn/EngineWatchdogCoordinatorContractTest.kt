package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineWatchdogCoordinatorContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/EngineWatchdogCoordinator.kt")
        assertTrue(f.exists(), "EngineWatchdogCoordinator.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `companion exposes PEER_WATCHDOG_POLL_MS, TIMEOUT_MS, RECOVER_GRACE_MS`() {
        assertEquals(5_000L, EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
        assertEquals(30_000L, EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS)
        assertEquals(30_000L, EngineWatchdogCoordinator.PEER_WATCHDOG_RECOVER_GRACE_MS)
    }

    @Test
    fun `публичные методы существуют`() {
        listOf(
            "fun startHealthKillswitchWatcher(",
            "fun startPeerWatchdog(",
            "fun handleEngineFailure(",
            "fun cancelWatchers(",
        ).forEach { anchor ->
            assertTrue(source.contains(anchor), "Public API anchor потерян: '$anchor'")
        }
    }

    @Test
    fun `enterKillswitchMode остаётся private — внутренний хелпер`() {
        assertTrue(
            source.contains("private fun enterKillswitchMode("),
            "enterKillswitchMode обязан быть private — вызывается только из health watcher и handleEngineFailure.",
        )
    }

    @Test
    fun `cancelWatchers отменяет оба watch job`() {
        val body = source.substringAfter("fun cancelWatchers(").substringBefore("private fun handleEngineFailure")
        assertTrue(
            body.contains("healthWatchJobRef.getAndSet(null)") &&
                body.contains("peerWatchJobRef.getAndSet(null)"),
            "cancelWatchers обязан отменить оба job — иначе утечка watchers при stop. Body:\n$body",
        )
    }

    @Test
    fun `handleEngineFailure ветвится по killswitchProvider + fdAlive`() {
        val body = source.substringAfter("fun handleEngineFailure(").substringBefore("private fun enterKillswitchMode")
        assertTrue(body.contains("killswitchProvider()"), "Обязан читать killswitchProvider().")
        assertTrue(body.contains("tunFdRef.get() != null"), "Обязан проверять fdAlive.")
        assertTrue(body.contains("enterKillswitchMode("), "True-branch → killswitch.")
        assertTrue(body.contains("stopVpnRequest()"), "False-branch → stopVpnRequest callback.")
    }

    @Test
    fun `enterKillswitchMode останавливает chain и health monitor + меняет notification`() {
        val body = source.substringAfter("private fun enterKillswitchMode(").substringBefore("companion object")
        assertTrue(body.contains("tunnelController.onKillswitchEngaged"), "Уведомление UI обязательно.")
        assertTrue(body.contains("chainOrchestrator.stop()"), "Chain stop обязательно.")
        assertTrue(body.contains("healthMonitor.stop()"), "Health monitor stop обязательно.")
        assertTrue(body.contains("notificationFactory.notifyStats("), "Killswitch notification обязательна.")
        assertTrue(body.contains("starting.set(false)"), "starting сброс обязателен.")
    }

    @Test
    fun `startHealthKillswitchWatcher cancels previous job до запуска нового`() {
        val body = source.substringAfter("fun startHealthKillswitchWatcher(").substringBefore("fun startPeerWatchdog")
        assertTrue(
            body.indexOf("healthWatchJobRef.getAndSet(null)?.cancel()") >= 0,
            "Watcher обязан отменить предыдущий job — иначе double-fire при перезапуске. Body:\n$body",
        )
    }

    @Test
    fun `startPeerWatchdog cancels previous job + early return если plugin не найден`() {
        val body = source.substringAfter("fun startPeerWatchdog(").substringBefore("fun cancelWatchers")
        assertTrue(
            body.contains("peerWatchJobRef.getAndSet(null)?.cancel()"),
            "Обязан отменить предыдущий peer watch job.",
        )
        assertTrue(
            body.contains("enginePlugins.firstOrNull { it.id == engineId } ?: return"),
            "Plugin not found → early return до launch.",
        )
    }

    @Test
    fun `coordinator не зависит от OzeroVpnService — тестируемость`() {
        assertTrue(
            !source.contains("OzeroVpnService"),
            "EngineWatchdogCoordinator не должен ссылаться на OzeroVpnService — нарушение модульности.",
        )
        val classDecl = source.substringAfter("class EngineWatchdogCoordinator").substringBefore("{").trim()
        assertTrue(
            classDecl.contains("scope: CoroutineScope") &&
                classDecl.contains("killswitchProvider: () -> Boolean") &&
                classDecl.contains("stopVpnRequest: () -> Unit"),
            "Coordinator зависит от scope + callbacks, не от service напрямую.",
        )
    }

    @Test
    fun `CancellationException пробрасывается, не глотается в watcher`() {
        val healthBody = source.substringAfter("fun startHealthKillswitchWatcher(")
            .substringBefore("fun startPeerWatchdog")
        val peerBody = source.substringAfter("fun startPeerWatchdog(").substringBefore("fun cancelWatchers")
        assertTrue(
            healthBody.contains("CancellationException") && healthBody.contains("throw ce"),
            "Health watcher обязан re-throw CancellationException — иначе coroutine cancel ломается.",
        )
        assertTrue(
            peerBody.contains("CancellationException") && peerBody.contains("throw ce"),
            "Peer watchdog обязан re-throw CancellationException — иначе coroutine cancel ломается.",
        )
    }
}
