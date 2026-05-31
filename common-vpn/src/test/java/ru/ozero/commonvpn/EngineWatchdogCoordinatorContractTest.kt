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
    fun `peer watchdog uses engine policy instead of engine-specific constants`() {
        val body = source.substringAfter("fun startPeerWatchdog(")
            .substringBefore("fun startStagnationWatchdog")
        assertTrue(
            body.contains("val peerWatchdogPolicy = plugin.peerWatchdogPolicy()"),
            "Peer watchdog must read policy from EnginePlugin, not from hardcoded EngineId branches.",
        )
        assertTrue(
            body.contains("peerWatchdogPolicy.timeoutMs"),
            "Peer watchdog timeout must come from engine policy.",
        )
        assertTrue(
            body.contains("!peerWatchdogPolicy.recoverBeforeFirstPeer"),
            "Initial zero-peer behavior must come from engine policy.",
        )
    }

    @Test
    fun `публичные методы существуют`() {
        listOf(
            "fun startHealthKillswitchWatcher(",
            "fun startPeerWatchdog(",
            "fun startStagnationWatchdog(",
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
    fun `cancelWatchers отменяет все watch jobs`() {
        val body = source.substringAfter("fun cancelWatchers(").substringBefore("fun handleEngineFailure")
        assertTrue(
            body.contains("healthWatchJobRef.getAndSet(null)") &&
                body.contains("peerWatchJobRef.getAndSet(null)") &&
                body.contains("stagnationWatchJobRef.getAndSet(null)"),
            "cancelWatchers обязан отменить все jobs — иначе утечка watchers при stop. Body:\n$body",
        )
    }

    @Test
    fun `handleEngineFailure ветвится по killswitchProvider + fdAlive`() {
        val body = source.substringAfter("fun handleEngineFailure(").substringBefore("private fun enterKillswitchMode")
        assertTrue(body.contains("killswitchProvider()"), "Обязан читать killswitchProvider().")
        assertTrue(body.contains("hasBlockingTun()"), "Обязан проверять общий blocking TUN, включая startup lockdown fd.")
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
        val body = source.substringAfter("fun startPeerWatchdog(").substringBefore("fun startStagnationWatchdog")
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
    fun `killswitch=false branch вызывает onEngineDied + stopVpnRequest, не enterKillswitchMode (P33)`() {
        val body = source.substringAfter("fun handleEngineFailure(")
            .substringBefore("private fun enterKillswitchMode")
        val elseBlock = body.substringAfter("else {").substringBefore("}")
        assertTrue(
            elseBlock.contains("tunnelController.onEngineDied(engineId"),
            "false-branch (killswitch=off ИЛИ fdAlive=false) обязан вызвать " +
                "tunnelController.onEngineDied — иначе UI не знает что движок умер. Body:\n$elseBlock",
        )
        assertTrue(
            elseBlock.contains("stopVpnRequest()"),
            "false-branch обязан звать stopVpnRequest — graceful shutdown VPN, не lockdown. Body:\n$elseBlock",
        )
        assertTrue(
            !elseBlock.contains("enterKillswitchMode"),
            "false-branch НЕ должен звать enterKillswitchMode — killswitch=off значит " +
                "не блокируем трафик, а штатно останавливаем VPN. Body:\n$elseBlock",
        )
    }

    @Test
    fun `health watcher killswitch=false branch не вызывает enterKillswitchMode (P33)`() {
        val body = source.substringAfter("fun startHealthKillswitchWatcher(")
            .substringBefore("fun startPeerWatchdog")
        val elseBlock = body.substringAfter("} else {").substringBefore("}")
        assertTrue(
            !elseBlock.contains("enterKillswitchMode"),
            "В health watcher else-ветке (killswitch=off ИЛИ fd=null ИЛИ stopping) " +
                "НЕ должен вызываться enterKillswitchMode — иначе lockdown триггерится без согласия юзера. " +
                "Body:\n$elseBlock",
        )
        assertTrue(
            elseBlock.contains("PersistentLoggers"),
            "false-branch обязан логировать факт degraded+killswitch-off — diagnostic visibility. " +
                "Body:\n$elseBlock",
        )
    }

    @Test
    fun `handleEngineFailure fdAlive=false ведёт к stopVpnRequest, не lockdown (P33)`() {
        val body = source.substringAfter("fun handleEngineFailure(")
            .substringBefore("private fun enterKillswitchMode")
        assertTrue(
            body.contains("killswitchProvider() && fdAlive") ||
                body.contains("killswitchProvider() && hasBlockingTun()") ||
                body.contains("fdAlive && killswitchProvider()") ||
                body.contains("hasBlockingTun() && killswitchProvider()"),
            "True-branch обязан требовать ОБА: killswitch=on И fdAlive — иначе " +
                "lockdown триггерится при уже мёртвом TUN (no-op + state inconsistency). Body:\n$body",
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
