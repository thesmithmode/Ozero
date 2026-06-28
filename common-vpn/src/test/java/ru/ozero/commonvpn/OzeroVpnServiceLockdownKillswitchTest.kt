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

    private val establishTunAndChainBody by lazy {
        require("private suspend fun establishTunAndChain(" in startSequenceSource) {
            "anchor 'private suspend fun establishTunAndChain' исчез — обнови sentinel"
        }
        require("private suspend fun readSplitConfig" in startSequenceSource) {
            "anchor 'private suspend fun readSplitConfig' исчез — обнови sentinel"
        }
        startSequenceSource.substringAfter("private suspend fun establishTunAndChain(")
            .substringBefore("private suspend fun readSplitConfig")
    }

    private val customTunBranchBody by lazy {
        require("if (usesCustomTun) {" in establishTunAndChainBody) {
            "anchor 'if (usesCustomTun)' исчез в establishTunAndChain — обнови sentinel"
        }
        require("return tun to chain\n        }\n" in establishTunAndChainBody) {
            "anchor закрытия customTun branch исчез — обнови sentinel"
        }
        establishTunAndChainBody.substringAfter("if (usesCustomTun) {")
            .substringBefore("return tun to chain\n        }\n")
    }

    private val regularTunBranchBody by lazy {
        require("return tun to chain\n        }\n" in establishTunAndChainBody) {
            "anchor закрытия customTun branch исчез — обнови sentinel"
        }
        establishTunAndChainBody.substringAfter("return tun to chain\n        }\n")
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
    fun `applyLockdown существует и вызывает setUnderlyingNetworks null условно`() {
        assertTrue(
            helperSource.contains("private fun applyLockdown"),
            "TunBuilderHelper обязан иметь private fun applyLockdown — Amnezia/PORTAL_WG pattern.",
        )
        val body = helperSource
            .substringAfter("private fun applyLockdown")
            .substringBefore("private fun blackholeIpv6")
        assertTrue(
            body.contains("setUnderlyingNetworks(null)"),
            "applyLockdown обязан вызывать setUnderlyingNetworks(null) когда applyUnderlying=true. Body:\n$body",
        )
        assertTrue(
            body.contains("if (!applyUnderlying) return"),
            "applyLockdown обязан early-return при applyUnderlying=false — ByeDPI upstream " +
                "parity, иначе ломает QUIC routing (2026-05-20 investigation). Body:\n$body",
        )
    }

    @Test
    fun `applyEngineTunSpec вызывает applyLockdown с applyUnderlying=true`() {
        val body = helperSource
            .substringAfter("fun applyEngineTunSpec(")
            .substringBefore("fun buildTunBuilder(")
        assertTrue(
            body.contains("applyLockdown(builder"),
            "applyEngineTunSpec обязан вызывать applyLockdown — иначе TUN строится без " +
                "setUnderlyingNetworks(null) и не enforces lockdown. Body:\n$body",
        )
        assertTrue(
            body.contains("applyUnderlying = true"),
            "applyEngineTunSpec (WARP/URnetwork) обязан applyUnderlying=true — killswitch " +
                "invariant P37 (WiFi→Mobile transition). Body:\n$body",
        )
    }

    @Test
    fun `buildTunBuilder default applyUnderlying=false — ByeDPI upstream parity`() {
        val body = helperSource
            .substringAfter("fun buildTunBuilder(")
            .substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("applyLockdown(builder"),
            "buildTunBuilder обязан вызывать applyLockdown — для ByeDPI/legacy engines.",
        )
        assertTrue(
            body.contains("applyUnderlying: Boolean = false"),
            "buildTunBuilder сигнатура обязана иметь default applyUnderlying=false — ByeDPI engine " +
                "path через default-вызов получает upstream ByeByeDPI 1.7.5 parity. " +
                "setUnderlyingNetworks(null) ломает QUIC: outgoing UDP socket в byedpi process " +
                "теряет authoritative underlying network → kernel routes через wrong interface → " +
                "YouTube QUIC fail на ~10-15с после connect. См. byedpi-vpn-pipeline-upstream-divergence. " +
                "Body:\n$body",
        )
        assertTrue(
            body.contains("applyUnderlying = applyUnderlying"),
            "buildTunBuilder обязан пробрасывать applyUnderlying в applyLockdown — иначе " +
                "killswitch startup path не может выставить true для P37 invariant. Body:\n$body",
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
    fun `killswitch startup TUN использует buildTunBuilder с applyUnderlying=true`() {
        val block = runBody
            .substringAfter("if (trafficMode == TrafficMode.TUN && killswitch)")
            .substringBefore("val manualEngine")
        assertTrue(
            block.contains("buildTunBuilder"),
            "killswitch startup path обязан вызывать buildTunBuilder. Block:\n$block",
        )
        assertTrue(
            block.contains("applyUnderlying = true"),
            "killswitch startup TUN обязан applyUnderlying=true, чтобы сохранять lockdown routing " +
                "при смене underlying network до установки основного TUN. Block:\n$block",
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
        val pickIdx = runBody.indexOf("autoCandidatesWithPreflight")
        assertTrue(lockdownIdx >= 0, "lockdownStartupFdRef.set не найден в run. Body:\n$runBody")
        assertTrue(pickIdx >= 0, "autoCandidatesWithPreflight не найден в run. Body:\n$runBody")
        assertTrue(
            lockdownIdx < pickIdx,
            "lockdownStartupFdRef.set обязан быть РАНЬШЕ autoCandidatesWithPreflight — " +
                "иначе startup gap не закрыт. lockdownIdx=$lockdownIdx pickIdx=$pickIdx",
        )
    }

    @Test
    fun `lockdownStartupFdRef закрывается после установки реального TUN в establishTunAndChain`() {
        val establishIdx = establishTunAndChainBody.indexOf("establishTun")
        val clearIdx = establishTunAndChainBody.indexOf("lockdownStartupFdRef.getAndSet(null)")
        assertTrue(establishIdx >= 0, "establishTun не найден в establishTunAndChain. Body:\n$establishTunAndChainBody")
        assertTrue(clearIdx >= 0, "lockdownStartupFdRef.getAndSet(null) не найден в establishTunAndChain.")
        assertTrue(
            clearIdx > establishIdx,
            "lockdownStartupFdRef должен очищаться ПОСЛЕ establish реального TUN. " +
                "establishIdx=$establishIdx clearIdx=$clearIdx",
        )
    }

    @Test
    fun `usesCustomTun branch — establishTunForEngine выполняется ДО startChain (P35 — WARP, URnetwork)`() {
        val establishIdx = customTunBranchBody.indexOf("establishTunForEngine(activeEngineId")
        val startChainIdx = customTunBranchBody.indexOf("startChain(activeEngineId")
        assertTrue(
            establishIdx >= 0,
            "establishTunForEngine не найден в usesCustomTun branch — anchor сломан. Body:\n$customTunBranchBody",
        )
        assertTrue(
            startChainIdx >= 0,
            "startChain не найден в usesCustomTun branch — anchor сломан. Body:\n$customTunBranchBody",
        )
        assertTrue(
            establishIdx < startChainIdx,
            "Для usesCustomTun (WARP/URnetwork): establishTunForEngine обязан выполняться ДО " +
                "startChain — TUN с applyLockdown активен раньше engine.start, engine получает fd " +
                "и attachTun сразу. establishIdx=$establishIdx startChainIdx=$startChainIdx",
        )
    }

    @Test
    fun `not usesCustomTun branch — startChain выполняется ДО establishTun (ByeDPI, MasterDNS QUIC drop-window)`() {
        val startChainIdx = regularTunBranchBody.indexOf("startChain(activeEngineId")
        val establishIdx = regularTunBranchBody.indexOf("establishTun(")
        assertTrue(
            startChainIdx >= 0,
            "startChain не найден в !usesCustomTun branch — anchor сломан. Body:\n$regularTunBranchBody",
        )
        assertTrue(
            establishIdx >= 0,
            "establishTun не найден в !usesCustomTun branch — anchor сломан. Body:\n$regularTunBranchBody",
        )
        assertTrue(
            startChainIdx >= 0 && (startChainIdx < establishIdx || establishIdx < 0),
            "Для !usesCustomTun (ByeDPI, MasterDNS): startChain (запуск hev/byedpi proxy chain) " +
                "обязан выполняться ДО establishTun — иначе TUN установлен раньше, чем proxy готов " +
                "принимать, и QUIC пакеты дропаются в startup gap (YouTube hypothesis #2). " +
                "startChainIdx=$startChainIdx establishIdx=$establishIdx",
        )
    }

    @Test
    fun `routeTrafficForEngine вызывается ПОСЛЕ establishTunAndChain — порядок lockdown→engine→route (P35)`() {
        val helperCallIdx = runBody.indexOf("establishTunAndChain(")
        val routeIdx = runBody.indexOf("routeTrafficForEngine(")
        assertTrue(helperCallIdx >= 0 && routeIdx >= 0, "anchors сломаны: $runBody")
        assertTrue(
            helperCallIdx < routeIdx,
            "establishTunAndChain (engine.start + TUN) обязан быть ДО routeTrafficForEngine " +
                "(attachTun/hev). helperCallIdx=$helperCallIdx routeIdx=$routeIdx",
        )
    }

    @Test
    fun `killswitch=on lockdownStartupTun establish выполняется ДО pickAutoCandidate (P35)`() {
        val lockdownEstablishIdx = runBody.indexOf("lockdownStartupFdRef.set")
        val pickIdx = runBody.indexOf("autoCandidatesWithPreflight")
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
    fun `setUnderlyingNetworks вызывается ТОЛЬКО с null — иначе WiFi→Mobile разрывает TUN (P37)`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val allSources = moduleRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("${File.separator}src${File.separator}main${File.separator}") }
            .filter { !it.path.contains("${File.separator}build${File.separator}") }
            .toList()
        val callsAll = allSources.flatMap { file ->
            Regex("setUnderlyingNetworks\\s*\\(([^)]*)\\)")
                .findAll(file.readText())
                .map { file.name to it.groupValues[1].trim() }
                .toList()
        }
        assertTrue(callsAll.isNotEmpty(), "Не найдено ни одного setUnderlyingNetworks call — anchor сломан.")
        val nonNullCalls = callsAll.filter { (_, arg) -> arg != "null" }
        assertTrue(
            nonNullCalls.isEmpty(),
            "ВСЕ setUnderlyingNetworks() обязаны быть с аргументом null — иначе при WiFi→Mobile " +
                "транзиции Android освобождает старый underlying network и TUN теряет route → " +
                "lockdown breaks. Found non-null: $nonNullCalls",
        )
    }

    @Test
    fun `OzeroVpnService не регистрирует NetworkCallback который мог бы close TUN (P37)`() {
        val forbidden = listOf(
            "registerDefaultNetworkCallback",
            "registerNetworkCallback",
        )
        forbidden.forEach { api ->
            assertTrue(
                !serviceSource.contains(api),
                "OzeroVpnService НЕ должен регистрировать $api — VPNService не должен реагировать " +
                    "на network transitions сам. Lockdown держится через setUnderlyingNetworks(null), " +
                    "не через manual fd close в onLost.",
            )
        }
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
