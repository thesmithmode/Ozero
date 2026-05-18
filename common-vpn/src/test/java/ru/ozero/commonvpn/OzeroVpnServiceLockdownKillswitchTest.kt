package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceLockdownKillswitchTest {

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

    private val helperSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunBuilderHelper.kt")
        assertTrue(f.exists(), "TunBuilderHelper.kt не найден: $f")
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

    private val runBody by lazy {
        require("suspend fun run()" in startSequenceSource) {
            "anchor 'suspend fun run()' исчез — обнови sentinel"
        }
        require("suspend fun engineNeedsCustomTun" in startSequenceSource) {
            "anchor 'suspend fun engineNeedsCustomTun' исчез — обнови sentinel"
        }
        startSequenceSource.substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
    }

    @Test
    fun `anchors — функции-границы существуют`() {
        listOf(
            "private fun applyLockdown",
            "private fun blackholeIpv6",
            "fun applyEngineTunSpec(",
            "fun buildTunBuilder(",
        ).forEach { anchor ->
            assertTrue(helperSource.contains(anchor), "Anchor потерян в TunBuilderHelper.kt: '$anchor'")
        }
        listOf(
            "fun stopVpn(",
            "private fun recordSessionEnd(",
        ).forEach { anchor ->
            assertTrue(shutdownSource.contains(anchor), "Anchor потерян в ShutdownCoordinator.kt: '$anchor'")
        }
        listOf(
            "suspend fun run()",
        ).forEach { anchor ->
            assertTrue(
                startSequenceSource.contains(anchor),
                "Anchor потерян в StartSequenceCoordinator.kt: '$anchor'",
            )
        }
        listOf(
            "fun startHealthKillswitchWatcher(",
            "private fun enterKillswitchMode(",
        ).forEach { anchor ->
            assertTrue(
                watchdogSource.contains(anchor),
                "Anchor потерян в EngineWatchdogCoordinator.kt: '$anchor'",
            )
        }
    }

    @Test
    fun `applyLockdown существует и вызывает setUnderlyingNetworks null`() {
        assertTrue(
            helperSource.contains("private fun applyLockdown"),
            "TunBuilderHelper обязан иметь private fun applyLockdown — Amnezia/PORTAL_WG pattern: " +
                "setUnderlyingNetworks(null) запрещает OS использовать underlying network вне TUN. " +
                "Без него VPN не enforces lockdown — трафик утекает мимо туннеля при degraded engine.",
        )
        val body = helperSource
            .substringAfter("private fun applyLockdown")
            .substringBefore("private fun blackholeIpv6")
        assertTrue(
            body.contains("setUnderlyingNetworks(null)"),
            "applyLockdown обязан вызывать setUnderlyingNetworks(null). Body:\n$body",
        )
    }

    @Test
    fun `applyEngineTunSpec вызывает applyLockdown`() {
        val body = helperSource
            .substringAfter("fun applyEngineTunSpec(")
            .substringBefore("fun buildTunBuilder(")
        assertTrue(
            body.contains("applyLockdown(builder"),
            "applyEngineTunSpec обязан вызывать applyLockdown — иначе TUN строится без " +
                "setUnderlyingNetworks(null) и не enforces lockdown. Body:\n$body",
        )
    }

    @Test
    fun `buildTunBuilder вызывает applyLockdown`() {
        val body = helperSource
            .substringAfter("fun buildTunBuilder(")
            .substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("applyLockdown(builder"),
            "buildTunBuilder обязан вызывать applyLockdown — для ByeDPI/non-customTun engines.",
        )
    }

    @Test
    fun `StartSequenceCoordinator run запускает health killswitch watcher`() {
        assertTrue(
            runBody.contains("engineWatchdog.startHealthKillswitchWatcher("),
            "run обязан стартовать health watcher — иначе HealthMonitor.DEGRADED " +
                "не триггерит killswitch и движок продолжает «бутафорное» состояние.",
        )
    }

    @Test
    fun `startHealthKillswitchWatcher триггерит enterKillswitchMode при DEGRADED + killswitch`() {
        val body = watchdogSource
            .substringAfter("fun startHealthKillswitchWatcher")
            .substringBefore("fun startPeerWatchdog")
        assertTrue(
            body.contains("HealthMonitor.Status.DEGRADED"),
            "watcher обязан фильтровать DEGRADED. Body:\n$body",
        )
        assertTrue(
            body.contains("enterKillswitchMode(engineId"),
            "watcher обязан вызывать enterKillswitchMode при degraded. Body:\n$body",
        )
        assertTrue(
            body.contains("killswitchProvider()"),
            "watcher обязан проверять killswitchProvider() перед fire. Body:\n$body",
        )
    }

    @Test
    fun `lockdownStartupFdRef объявлен в сервисе`() {
        assertTrue(
            serviceSource.contains("lockdownStartupFdRef"),
            "OzeroVpnService обязан иметь lockdownStartupFdRef — instant lockdown TUN fd " +
                "для закрытия startup race gap при killswitch=ON.",
        )
    }

    @Test
    fun `lockdownStartupTun устанавливается до выбора движка в StartSequenceCoordinator run`() {
        val lockdownIdx = runBody.indexOf("lockdownStartupFdRef.set")
        val pickIdx = runBody.indexOf("pickAutoCandidateWithPreflight")
        assertTrue(lockdownIdx >= 0, "lockdownStartupFdRef.set не найден в run. Body:\n$runBody")
        assertTrue(pickIdx >= 0, "pickAutoCandidateWithPreflight не найден в run. Body:\n$runBody")
        assertTrue(
            lockdownIdx < pickIdx,
            "lockdownStartupFdRef.set обязан быть РАНЬШЕ pickAutoCandidateWithPreflight — " +
                "иначе startup gap не закрыт. lockdownIdx=$lockdownIdx pickIdx=$pickIdx",
        )
    }

    @Test
    fun `lockdownStartupFdRef закрывается после установки реального TUN в StartSequenceCoordinator run`() {
        val establishIdx = runBody.indexOf("establishTun")
        val clearIdx = runBody.indexOf("lockdownStartupFdRef.getAndSet(null)")
        assertTrue(establishIdx >= 0, "establishTun не найден в run. Body:\n$runBody")
        assertTrue(clearIdx >= 0, "lockdownStartupFdRef.getAndSet(null) не найден в run.")
        assertTrue(
            clearIdx > establishIdx,
            "lockdownStartupFdRef должен очищаться ПОСЛЕ establish реального TUN. " +
                "establishIdx=$establishIdx clearIdx=$clearIdx",
        )
    }

    @Test
    fun `establishTun выполняется ДО startChain в run — applyLockdown активен раньше engine start (P35)`() {
        val establishCustomIdx = runBody.indexOf("establishTunForEngine(activeEngineId")
        val establishRegularIdx = runBody.indexOf("establishTun(\n")
        val anyEstablishIdx = listOf(establishCustomIdx, establishRegularIdx)
            .filter { it >= 0 }
            .minOrNull() ?: -1
        val startChainIdx = runBody.indexOf("startChain(activeEngineId")
        assertTrue(
            anyEstablishIdx >= 0,
            "establishTun/establishTunForEngine не найден в run — anchor сломан. Body:\n$runBody",
        )
        assertTrue(
            startChainIdx >= 0,
            "startChain(activeEngineId не найден в run — anchor сломан. Body:\n$runBody",
        )
        assertTrue(
            anyEstablishIdx < startChainIdx,
            "establishTun (с applyLockdown внутри builder) обязан выполниться ДО startChain — " +
                "иначе chainOrchestrator.start запускает engine native loops при отсутствии TUN " +
                "с setUnderlyingNetworks(null) → трафик может выйти мимо туннеля в startup gap. " +
                "establishIdx=$anyEstablishIdx startChainIdx=$startChainIdx",
        )
    }

    @Test
    fun `routeTrafficForEngine вызывается ПОСЛЕ startChain — корректный порядок lockdown→engine→route (P35)`() {
        val startChainIdx = runBody.indexOf("startChain(activeEngineId")
        val routeIdx = runBody.indexOf("routeTrafficForEngine(")
        assertTrue(startChainIdx >= 0 && routeIdx >= 0, "anchors сломаны: $runBody")
        assertTrue(
            startChainIdx < routeIdx,
            "startChain (engine.start) обязан быть ДО routeTrafficForEngine (attachTun/hev) — " +
                "engine должен быть готов принять fd. startChainIdx=$startChainIdx routeIdx=$routeIdx",
        )
    }

    @Test
    fun `killswitch=on lockdownStartupTun.establish выполняется ДО pickAutoCandidate (P35)`() {
        val lockdownEstablishIdx = runBody.indexOf("lockdownStartupFdRef.set")
        val pickIdx = runBody.indexOf("pickAutoCandidateWithPreflight")
        val manualIdx = runBody.indexOf("settings?.manualEngine")
        val anyPickIdx = listOf(pickIdx, manualIdx).filter { it >= 0 }.minOrNull() ?: -1
        assertTrue(
            lockdownEstablishIdx >= 0 && anyPickIdx >= 0,
            "anchors сломаны: lockdown=$lockdownEstablishIdx pick=$anyPickIdx",
        )
        assertTrue(
            lockdownEstablishIdx < anyPickIdx,
            "lockdownStartupFdRef.set (instant lockdown TUN) обязан случаться ДО выбора движка — " +
                "иначе preflight probe (network call) при killswitch=on проходит без активного TUN " +
                "и трафик утекает на этапе probe. lockdownIdx=$lockdownEstablishIdx pickIdx=$anyPickIdx",
        )
    }

    @Test
    fun `lockdownStartupFdRef очищается в stopVpn`() {
        val body = shutdownSource
            .substringAfter("fun stopVpn(")
            .substringBefore("suspend fun performShutdown(")
        assertTrue(
            body.contains("lockdownStartupFdRef.getAndSet(null)"),
            "stopVpn обязан закрывать lockdownStartupFdRef — иначе fd утечёт при остановке VPN до старта движка.",
        )
    }
}
