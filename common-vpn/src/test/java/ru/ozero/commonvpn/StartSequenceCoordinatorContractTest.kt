package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartSequenceCoordinatorContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `companion exposes timeout константы`() {
        assertEquals(1_500L, StartSequenceCoordinator.SETTINGS_READ_TIMEOUT_MS)
        assertEquals(30_000L, StartSequenceCoordinator.CHAIN_START_TIMEOUT_MS)
        assertEquals(7_000L, StartSequenceCoordinator.PREFLIGHT_HARD_TIMEOUT_MS)
    }

    @Test
    fun `public API — run + engineNeedsCustomTun`() {
        listOf("suspend fun run(", "suspend fun engineNeedsCustomTun(").forEach { anchor ->
            assertTrue(source.contains(anchor), "API anchor потерян: '$anchor'")
        }
    }

    @Test
    fun `bundle classes objявлены — StartSequenceState и StartSequenceCollaborators`() {
        listOf(
            "class StartSequenceState(",
            "class StartSequenceCollaborators(",
        ).forEach { anchor ->
            assertTrue(source.contains(anchor), "Bundle anchor потерян: '$anchor'")
        }
    }

    @Test
    fun `coordinator не зависит от OzeroVpnService — тестируемость`() {
        assertTrue(
            !source.contains("OzeroVpnService"),
            "StartSequenceCoordinator не должен ссылаться на OzeroVpnService — нарушение модульности.",
        )
        val classDecl = source.substringAfter("class StartSequenceCoordinator(").substringBefore("{").trim()
        assertTrue(
            classDecl.contains("killswitchSetter: (Boolean) -> Unit") &&
                classDecl.contains("stopVpnRequest: () -> Unit"),
            "Coordinator зависит от callbacks, не от service напрямую. Decl:\n$classDecl",
        )
    }

    @Test
    fun `OzeroVpnService строит StartSequenceCoordinator через by lazy и вызывает run в startVpn`() {
        assertTrue(
            serviceSource.contains("startSequence: StartSequenceCoordinator by lazy {") &&
                serviceSource.contains("StartSequenceCoordinator("),
            "OzeroVpnService обязан инжектить startSequence через by lazy.",
        )
        assertTrue(
            serviceSource.contains("startSequence.run()"),
            "startVpn обязан вызывать startSequence.run() вместо inline runStartSequence.",
        )
    }

    @Test
    fun `OzeroVpnService больше не имеет inline runStartSequence и related extracted методов`() {
        listOf(
            "private suspend fun runStartSequence",
            "private suspend fun autoCandidatesWithPreflight",
            "private suspend fun establishTunForEngine",
            "private fun establishTun(",
            "private fun pickActiveEngine",
            "private fun captureTunIfaceName",
            "private fun autoCandidates",
            "private fun buildEngineConfig",
            "private fun resolveTargetForUi",
            "private suspend fun startChain",
            "private suspend fun routeTrafficForEngine",
            "private suspend fun startNativeTunnel",
            "private suspend fun readSplitConfig",
            "private suspend fun awaitEngineReady",
            "private suspend fun engineNeedsCustomTun",
            "SETTINGS_READ_TIMEOUT_MS",
            "CHAIN_START_TIMEOUT_MS",
            "PREFLIGHT_HARD_TIMEOUT_MS",
        ).forEach { anchor ->
            assertTrue(
                !serviceSource.contains(anchor),
                "OzeroVpnService.kt не должен больше содержать '$anchor' — извлечено в StartSequenceCoordinator.",
            )
        }
    }

    @Test
    fun `run ветка killswitch строит instant lockdown TUN ДО pickAuto`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("suspend fun engineNeedsCustomTun(")
        val killswitchIdx = body.indexOf("if (trafficMode == TrafficMode.TUN && killswitch) {")
        val pickIdx = body.indexOf("autoCandidatesWithPreflight(")
        assertTrue(killswitchIdx in 0 until pickIdx, "Killswitch lockdown TUN строится ДО pickAuto.")
        assertTrue(
            body.contains("state.lockdownStartupFdRef.set(fd)"),
            "Killswitch ветка обязана сохранять lockdownStartupFdRef.",
        )
    }

    @Test
    fun `proxy mode игнорирует Android killswitch и не строит lockdown TUN`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("suspend fun engineNeedsCustomTun(")
        val tunKillswitchIdx = body.indexOf("if (trafficMode == TrafficMode.TUN && killswitch)")
        val setterIdx = body.indexOf("killswitchSetter(trafficMode == TrafficMode.TUN && killswitch)")
        assertTrue(
            setterIdx in 0 until tunKillswitchIdx,
            "Service-level killswitch должен получать effective значение до TUN lockdown.",
        )
        assertTrue(
            !body.contains("killswitchSetter(false)"),
            "Proxy mode не должен сбрасывать настройку killswitch; он только игнорирует её как effective flag.",
        )
    }

    @Test
    fun `run уважает stopping signal — early return перед каждой долгой операцией`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("suspend fun engineNeedsCustomTun(")
        val stoppingChecks = body.split("state.stopping.get()").size - 1
        assertTrue(
            stoppingChecks >= 2,
            "run обязан проверять state.stopping минимум 2 раза (вход + между establish и chain). " +
                "Найдено: $stoppingChecks",
        )
    }

    @Test
    fun `run вызывает awaitEngineReady ДО onEngineStarted — readiness gate`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("suspend fun engineNeedsCustomTun(")
        val awaitIdx = body.indexOf("awaitEngineReady(")
        val onStartedIdx = body.indexOf("onEngineStarted(")
        assertTrue(
            awaitIdx in 0 until onStartedIdx,
            "awaitEngineReady ОБЯЗАН быть ДО onEngineStarted — readiness gate. " +
                "awaitIdx=$awaitIdx, onStartedIdx=$onStartedIdx",
        )
    }

    @Test
    fun `autoCandidatesWithPreflight использует no-op SocketProtector`() {
        val body = source.substringAfter("private suspend fun autoCandidatesWithPreflight(")
            .substringBefore("private suspend fun establishTunForEngine(")
        assertTrue(
            body.contains("SocketProtector { _ -> true }"),
            "preflight обязан использовать no-op protector — TUN ещё не создан на момент preflight.",
        )
    }

    @Test
    fun `proxy mode фильтрует auto и manual кандидатов по standalone proxy capability`() {
        assertTrue(
            source.contains("engineAllowedForTrafficMode(manualEngine, trafficMode)"),
            "manual engine в PROXY mode должен проходить capability gate до start().",
        )
        val autoBody = source.substringAfter("private fun autoCandidates(")
            .substringBefore("private fun engineAllowedForTrafficMode(")
        assertTrue(
            autoBody.contains("engineAllowedForTrafficMode(id, trafficMode)"),
            "auto mode в PROXY mode обязан пропускать только движки с local proxy endpoint.",
        )
        val gateBody = source.substringAfter("private fun engineAllowedForTrafficMode(")
            .substringBefore("private suspend fun establishTunForEngine(")
        assertTrue(gateBody.contains("trafficMode == TrafficMode.TUN"))
        assertTrue(gateBody.contains("providesLocalSocksWithoutUpstream == true"))
    }

    @Test
    fun `establishTunForEngine не исключает self package`() {
        val body = source.substringAfter("private suspend fun establishTunForEngine(")
            .substringBefore("private fun captureTunIfaceName(")
        assertFalse(
            body.contains("excludeSelf") || body.contains("addDisallowedApplication(packageName)"),
            "self package нельзя исключать из VPN: app-originated HTTP/DNS должен оставаться в TUN.",
        )
    }

    @Test
    fun `routeTrafficForEngine — TunFdAcceptor ветка через attachTun, иначе native tunnel`() {
        val body = source.substringAfter("private suspend fun routeTrafficForEngine(")
            .substringBefore("private suspend fun startNativeTunnel(")
        assertTrue(body.contains("engine is TunFdAcceptor"), "TunFdAcceptor проверка обязательна.")
        assertTrue(body.contains("attachTun(rawDupFd)"), "attachTun вызов обязателен.")
        assertTrue(body.contains("startNativeTunnel("), "Fallback на startNativeTunnel обязателен.")
    }

    @Test
    fun `run — onProbing вызывается ДО onEngineDied при no engine reachable — UI не застревает в Idle`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("suspend fun engineNeedsCustomTun(")
        val failBlock = body.substringAfter("if (picks.isEmpty()) {").substringBefore("stopVpnRequest()")
        val probingIdx = failBlock.indexOf("tunnelController.onProbing(")
        val diedIdx = failBlock.indexOf("tunnelController.onEngineDied(")
        assertTrue(
            probingIdx >= 0,
            "run: при pick==null и targetForUi!=null обязан вызвать onProbing(targetForUi) " +
                "ДО onEngineDied — иначе TunnelController в Idle игнорирует Failed transition, " +
                "UI не показывает ошибку (invalid transition Idle→Failed).",
        )
        assertTrue(
            probingIdx < diedIdx,
            "onProbing обязан быть ДО onEngineDied. probingIdx=$probingIdx diedIdx=$diedIdx",
        )
    }

    @Test
    fun `auto-mode cascades to next candidate before terminal failure`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("private suspend fun runSingleProxy(")
        assertTrue(
            body.contains("picks.forEachIndexed"),
            "auto-mode обязан перебирать все preflight-valid candidates, а не падать на первом runtime fail.",
        )
        assertTrue(
            body.contains("notifyFailure = manualEngine != null || isLast"),
            "непоследний auto candidate не должен вызывать terminal handleEngineFailure/stopVpnRequest.",
        )
        assertTrue(
            body.contains("resetAfterAutoCandidateFailure"),
            "после runtime fail auto-mode обязан сбросить transient state перед следующим candidate.",
        )
    }

    @Test
    fun `killswitchSetter получает effective значение из settings и trafficMode`() {
        val body = source.substringAfter("suspend fun run(").substringBefore("suspend fun engineNeedsCustomTun(")
        assertTrue(
            body.contains("killswitchSetter(trafficMode == TrafficMode.TUN && killswitch)"),
            "run обязан проталкивать service-level effective killswitch, сохраняя пользовательский toggle для возврата в TUN.",
        )
    }
}
