package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineWatchdogCoordinatorContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/EngineWatchdogCoordinator.kt")
        assertTrue(f.exists(), "EngineWatchdogCoordinator.kt РЅРµ РЅР°Р№РґРµРЅ: $f")
        f.readText()
    }

    @Test
    fun `companion exposes PEER_WATCHDOG_POLL_MS, TIMEOUT_MS, RECOVER_GRACE_MS`() {
        assertEquals(5_000L, EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
        assertEquals(30_000L, EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS)
        assertEquals(30_000L, EngineWatchdogCoordinator.PEER_WATCHDOG_RECOVER_GRACE_MS)
    }

    @Test
    fun `handleEngineFailure fdAlive=false РІРµРґС‘С‚ Рє stopVpnRequest, РЅРµ lockdown (P33) - default path`() {
        val body = source.substringAfter("fun handleEngineFailure(")
        assertTrue(
            body.contains("killswitchProvider() && hasBlockingTunForKillswitch()"),
            "True-branch РѕР±СЏР·Р°РЅ С‚СЂРµР±РѕРІР°С‚СЊ killswitch=on Рё РѕР±С‰РёР№ blocking TUN check С‡РµСЂРµР· hasBlockingTunForKillswitch(). Body:\n$body",
        )
        assertTrue(
            body.contains("tunnelController.onEngineDied(engineId, reason)") && body.contains("stopVpnRequest()"),
            "False-branch РѕР±СЏР·Р°РЅ РѕСЃС‚Р°РІРёС‚СЊ graceful stop С‡РµСЂРµР· onEngineDied + stopVpnRequest. Body:\n$body",
        )
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
    fun `РїСѓР±Р»РёС‡РЅС‹Рµ РјРµС‚РѕРґС‹ СЃСѓС‰РµСЃС‚РІСѓСЋС‚`() {
        listOf(
            "fun startHealthKillswitchWatcher(",
            "fun startPeerWatchdog(",
            "fun startStagnationWatchdog(",
            "fun handleEngineFailure(",
            "fun cancelWatchers(",
        ).forEach { anchor ->
            assertTrue(source.contains(anchor), "Public API anchor РїРѕС‚РµСЂСЏРЅ: '$anchor'")
        }
    }

    @Test
    fun `enterKillswitchMode РѕСЃС‚Р°С‘С‚СЃСЏ private вЂ” РІРЅСѓС‚СЂРµРЅРЅРёР№ С…РµР»РїРµСЂ`() {
        assertTrue(
            source.contains("private fun enterKillswitchMode("),
            "enterKillswitchMode РѕР±СЏР·Р°РЅ Р±С‹С‚СЊ private вЂ” РІС‹Р·С‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РёР· health watcher Рё handleEngineFailure.",
        )
    }

    @Test
    fun `cancelWatchers РѕС‚РјРµРЅСЏРµС‚ РІСЃРµ watch jobs`() {
        val body = source.substringAfter("fun cancelWatchers(").substringBefore("fun handleEngineFailure")
        assertTrue(
            body.contains("healthWatchJobRef.getAndSet(null)") &&
                body.contains("peerWatchJobRef.getAndSet(null)") &&
                body.contains("stagnationWatchJobRef.getAndSet(null)"),
            "cancelWatchers РѕР±СЏР·Р°РЅ РѕС‚РјРµРЅРёС‚СЊ РІСЃРµ jobs вЂ” РёРЅР°С‡Рµ СѓС‚РµС‡РєР° watchers РїСЂРё stop. Body:\n$body",
        )
    }

    @Test
    fun `handleEngineFailure РІРµС‚РІРёС‚СЃСЏ РїРѕ killswitchProvider + fdAlive`() {
        val body = source.substringAfter("fun handleEngineFailure(").substringBefore("private fun enterKillswitchMode")
        assertTrue(body.contains("killswitchProvider()"), "РћР±СЏР·Р°РЅ С‡РёС‚Р°С‚СЊ killswitchProvider().")
        assertTrue(
            body.contains("hasBlockingTun()"),
            "РћР±СЏР·Р°РЅ РїСЂРѕРІРµСЂСЏС‚СЊ РѕР±С‰РёР№ blocking TUN, РІРєР»СЋС‡Р°СЏ startup lockdown fd.",
        )
        assertTrue(body.contains("enterKillswitchMode("), "True-branch в†’ killswitch.")
        assertTrue(
            !body.contains("restartInProgressProvider"),
            "restartInProgressProvider больше не должен участвовать в watchdog decision.",
        )
    }

    @Test
    fun `startHealthKillswitchWatcher cancels previous job РґРѕ Р·Р°РїСѓСЃРєР° РЅРѕРІРѕРіРѕ`() {
        val body = source.substringAfter("fun startHealthKillswitchWatcher(").substringBefore("fun startPeerWatchdog")
        assertTrue(
            body.indexOf("healthWatchJobRef.getAndSet(null)?.cancel()") >= 0,
            "Watcher РѕР±СЏР·Р°РЅ РѕС‚РјРµРЅРёС‚СЊ РїСЂРµРґС‹РґСѓС‰РёР№ job вЂ” РёРЅР°С‡Рµ double-fire РїСЂРё РїРµСЂРµР·Р°РїСѓСЃРєРµ. Body:\n$body",
        )
    }

    @Test
    fun `startPeerWatchdog cancels previous job + early return РµСЃР»Рё plugin РЅРµ РЅР°Р№РґРµРЅ`() {
        val body = source.substringAfter("fun startPeerWatchdog(").substringBefore("fun startStagnationWatchdog")
        assertTrue(
            body.contains("peerWatchJobRef.getAndSet(null)?.cancel()"),
            "РћР±СЏР·Р°РЅ РѕС‚РјРµРЅРёС‚СЊ РїСЂРµРґС‹РґСѓС‰РёР№ peer watch job.",
        )
        assertTrue(
            body.contains("enginePlugins.firstOrNull { it.id == engineId } ?: return"),
            "Plugin not found в†’ early return РґРѕ launch.",
        )
    }

    @Test
    fun `coordinator РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ OzeroVpnService вЂ” С‚РµСЃС‚РёСЂСѓРµРјРѕСЃС‚СЊ`() {
        assertTrue(
            !source.contains("OzeroVpnService"),
            "EngineWatchdogCoordinator РЅРµ РґРѕР»Р¶РµРЅ СЃСЃС‹Р»Р°С‚СЊСЃСЏ РЅР° OzeroVpnService вЂ” РЅР°СЂСѓС€РµРЅРёРµ РјРѕРґСѓР»СЊРЅРѕСЃС‚Рё.",
        )
        val classDecl = source.substringAfter("class EngineWatchdogCoordinator").substringBefore("{").trim()
        assertTrue(
            classDecl.contains("scope: CoroutineScope") &&
                classDecl.contains("killswitchProvider: () -> Boolean") &&
                classDecl.contains("stopVpnRequest: () -> Unit"),
            "Coordinator Р·Р°РІРёСЃРёС‚ РѕС‚ scope + callbacks, РЅРµ РѕС‚ service РЅР°РїСЂСЏРјСѓСЋ.",
        )
    }

    @Test
    fun `killswitch=false branch РІС‹Р·С‹РІР°РµС‚ onEngineDied + stopVpnRequest, РЅРµ enterKillswitchMode (P33)`() {
        val body = source.substringAfter("fun handleEngineFailure(")
            .substringBefore("private fun enterKillswitchMode")
        val elseBlock = body.substringAfter("else {").substringBefore("}")
        assertTrue(
            elseBlock.contains("tunnelController.onEngineDied(engineId"),
            "false-branch (killswitch=off РР›Р fdAlive=false) РѕР±СЏР·Р°РЅ РІС‹Р·РІР°С‚СЊ " +
                "tunnelController.onEngineDied вЂ” РёРЅР°С‡Рµ UI РЅРµ Р·РЅР°РµС‚ С‡С‚Рѕ РґРІРёР¶РѕРє СѓРјРµСЂ. Body:\n$elseBlock",
        )
        assertTrue(
            elseBlock.contains("stopVpnRequest()"),
            "false-branch РѕР±СЏР·Р°РЅ Р·РІР°С‚СЊ stopVpnRequest вЂ” graceful shutdown VPN, РЅРµ lockdown. Body:\n$elseBlock",
        )
        assertTrue(
            !elseBlock.contains("enterKillswitchMode"),
            "false-branch РќР• РґРѕР»Р¶РµРЅ Р·РІР°С‚СЊ enterKillswitchMode вЂ” killswitch=off Р·РЅР°С‡РёС‚ " +
                "РЅРµ Р±Р»РѕРєРёСЂСѓРµРј С‚СЂР°С„РёРє, Р° С€С‚Р°С‚РЅРѕ РѕСЃС‚Р°РЅР°РІР»РёРІР°РµРј VPN. Body:\n$elseBlock",
        )
    }

    @Test
    fun `health watcher killswitch=false branch РЅРµ РІС‹Р·С‹РІР°РµС‚ enterKillswitchMode (P33)`() {
        val body = source.substringAfter("fun startHealthKillswitchWatcher(")
            .substringBefore("fun startPeerWatchdog")
        val elseBlock = body.substringAfter("} else {").substringBefore("}")
        assertTrue(
            !elseBlock.contains("enterKillswitchMode"),
            "В health watcher else-ветке (killswitch=off ИЛИ fd=null ИЛИ stopping) " +
                "НЕ должен вызываться enterKillswitchMode — иначе lockdown триггерится " +
                "без согласия user. Body:\n$elseBlock",
        )
        assertTrue(
            elseBlock.contains("PersistentLoggers"),
            "false-branch обязан логировать факт degraded+killswitch-off — diagnostic visibility. " +
                "Body:\n$elseBlock",
        )
    }

    @Test
    fun `handleEngineFailure fdAlive=false ведёт к stopVpnRequest, не lockdown (P33) - blocking tun path`() {
        val body = source.substringAfter("fun handleEngineFailure(")
            .substringBefore("private fun enterKillswitchMode")
        assertTrue(
            body.contains("killswitchProvider() && hasBlockingTun()"),
            "True-branch обязана требовать killswitch=on + real blocking TUN. Body:\n$body",
        )
    }

    @Test
    fun `CancellationException пробырасывается, не глотается в watcher`() {
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
