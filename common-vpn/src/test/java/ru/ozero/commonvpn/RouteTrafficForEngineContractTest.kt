package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class RouteTrafficForEngineContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    private val routeBody by lazy {
        val start = source.indexOf("private suspend fun routeTrafficForEngine")
        assertTrue(start >= 0, "routeTrafficForEngine не найден")
        val nextFn = source.indexOf("\n    private", start + 1)
        source.substring(start, if (nextFn > 0) nextFn else source.length)
    }

    @Test
    fun `routeTrafficForEngine ветвится по TunFdAcceptor типу`() {
        assertTrue(
            routeBody.contains("is ru.ozero.enginescore.TunFdAcceptor") ||
                routeBody.contains("is TunFdAcceptor"),
            "routeTrafficForEngine обязан проверять `engine is TunFdAcceptor` — без этой ветки " +
                "URnetwork (и любой TUN-acceptor engine) получает HEV TProxy вместо attachTun. " +
                "Регрессия = пакеты не доходят до движка.",
        )
    }

    @Test
    fun `TunFdAcceptor ветка использует detachFd не fd field`() {
        val acceptorBranch = routeBody.substringAfter("TunFdAcceptor").substringBefore("startNativeTunnel")
        assertTrue(
            acceptorBranch.contains("detachFd()"),
            "Acceptor branch обязан использовать ParcelFileDescriptor.detachFd() — иначе fd " +
                "закроется когда GC соберёт ParcelFileDescriptor, и движок получит invalid fd. " +
                "Использование `.fd` (без detach) = висячий fd регресс.",
        )
    }

    @Test
    fun `TunFdAcceptor ветка вызывает engine attachTun`() {
        val acceptorBranch = routeBody.substringAfter("TunFdAcceptor").substringBefore("startNativeTunnel")
        assertTrue(
            acceptorBranch.contains("engine.attachTun"),
            "Acceptor branch обязан вызывать engine.attachTun(rawFd) — это контракт TunFdAcceptor.",
        )
    }

    @Test
    fun `TunFdAcceptor failure path останавливает chain и VPN`() {
        val acceptorBranch = routeBody.substringAfter("TunFdAcceptor").substringBefore("startNativeTunnel")
        val hasFailureHandling = acceptorBranch.contains("TunAttachResult.Failure") ||
            acceptorBranch.contains("is ru.ozero.enginescore.TunAttachResult.Failure")
        assertTrue(
            hasFailureHandling,
            "Acceptor branch обязан обрабатывать TunAttachResult.Failure — иначе при сбое " +
                "engine.attachTun VPN остаётся в зомби-состоянии без трафика.",
        )
        assertTrue(
            acceptorBranch.contains("chainOrchestrator.stop()"),
            "Failure path обязан вызвать chainOrchestrator.stop() — иначе chain остаётся активным.",
        )
        assertTrue(
            acceptorBranch.contains("handleEngineFailure(") ||
                acceptorBranch.contains("stopVpn()"),
            "Failure path обязан вызвать handleEngineFailure() или stopVpn() — иначе утечка " +
                "ресурсов и зомби-tunnel notification.",
        )
        assertTrue(
            acceptorBranch.contains("tunnelController.onEngineDied") ||
                acceptorBranch.contains("handleEngineFailure("),
            "Failure path обязан уведомить о сбое через tunnelController.onEngineDied или handleEngineFailure.",
        )
    }

    @Test
    fun `non-TunFdAcceptor движок маршрутизируется через HEV`() {
        assertTrue(
            routeBody.contains("startNativeTunnel"),
            "Без TunFdAcceptor движок должен fallback на startNativeTunnel (HEV TProxy) — " +
                "это путь ByeDpi / любого SOCKS-only engine.",
        )
    }

    @Test
    fun `tunFdRef очищается после detachFd`() {
        val acceptorBranch = routeBody.substringAfter("TunFdAcceptor").substringBefore("startNativeTunnel")
        assertTrue(
            acceptorBranch.contains("tunFdRef.compareAndSet(fd, null)") ||
                acceptorBranch.contains("tunFdRef.getAndSet(null)") ||
                acceptorBranch.contains("tunFdRef.set(null)"),
            "После detachFd() tunFdRef обязан очиститься — иначе onDestroy попытается close уже " +
                "detached fd, double-close = native crash.",
        )
    }

    @Test
    fun `rawDupFd закрывается через adoptFd на failure paths attachTun`() {
        val acceptorBranch = routeBody.substringAfter("TunFdAcceptor").substringBefore("startNativeTunnel")
        val adoptCount = acceptorBranch.split("ParcelFileDescriptor.adoptFd(rawDupFd)").size - 1
        assertTrue(
            adoptCount >= 2,
            "rawDupFd = fd.dup().detachFd() — kernel-level fd без PFD-обёртки. На failure paths " +
                "(catch + TunAttachResult.Failure) обязан ParcelFileDescriptor.adoptFd(rawDupFd).close() — " +
                "иначе каждый неуспешный reconnect leakит kernel fd → RLIMIT_NOFILE → VPN start failures. " +
                "Найдено вызовов: $adoptCount, ожидается ≥2.",
        )
    }
}
