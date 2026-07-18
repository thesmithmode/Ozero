package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnServiceLifecycleContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `runBlocking защищён timeout или отдельным потоком`() {
        val hasRunBlocking = source.contains("runBlocking")
        if (!hasRunBlocking) return
        val hasThreadJoin = source.contains("Thread(") && source.contains(".join(")
        val hasTimeoutGuard = source.contains("withTimeoutOrNull") || source.contains("withTimeout(")
        assertTrue(
            hasThreadJoin || hasTimeoutGuard,
            "runBlocking присутствует в OzeroVpnService — обязательно обернуть в Thread+join+timeout " +
                "ИЛИ withTimeoutOrNull внутри runBlocking, иначе ANR на Main thread (особенно в onDestroy " +
                "при kill-switch). См. предыдущий регресс.",
        )
    }

    @Test
    fun `tunFd close в onDestroy безусловный`() {
        val hasOnDestroy = source.contains("override fun onDestroy")
        assertTrue(hasOnDestroy, "onDestroy должен быть переопределён.")
        val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("\n}\n")
        assertTrue(
            onDestroyBody.contains("tunFdRef.getAndSet(null)?.close()") ||
                onDestroyBody.contains("tunFd?.close()") ||
                onDestroyBody.contains("tunFd!!.close()"),
            "В onDestroy должно закрываться TUN fd (через tunFdRef.getAndSet(null)?.close() или " +
                "tunFd?.close()) — kill-switch инвариант.",
        )
    }

    @Test
    fun `socketProtector unbind ПОСЛЕ performShutdown в onDestroy — socket leak guard`() {
        val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("\n}\n")
        val performShutdownIdx = onDestroyBody.indexOf("performShutdown(callStopSelf = false)")
        val unbindIdx = onDestroyBody.indexOf("VpnSocketProtectorHolder.unbind")
        assertTrue(
            performShutdownIdx > 0 && unbindIdx > 0,
            "onDestroy обязан вызывать performShutdown и unbind socketProtector",
        )
        assertTrue(
            unbindIdx > performShutdownIdx,
            "VpnSocketProtectorHolder.unbind обязан быть ПОСЛЕ performShutdown — иначе движки во время " +
                "graceful flush получают protect()=false на новые сокеты → leak в VPN-loop. " +
                "Текущие позиции: performShutdown@$performShutdownIdx unbind@$unbindIdx.",
        )
    }

    @Test
    fun `performShutdown cancels start job before engine stop pass`() {
        val coordinator = File(
            File(System.getProperty("user.dir") ?: "."),
            "src/main/java/ru/ozero/commonvpn/ShutdownCoordinator.kt",
        ).readText()
        val performShutdownBody = coordinator.substringAfter("suspend fun performShutdown")
            .substringBefore("    private fun recordSessionEnd")
        val cancelIdx = performShutdownBody.indexOf("cancelStartBeforeStopPass()")
        val stopIdx = performShutdownBody.indexOf("chainOrchestrator.stop()")
        assertTrue(
            cancelIdx > 0 && stopIdx > 0 && cancelIdx < stopIdx,
            "performShutdown обязан cancel/join startJobRef до chainOrchestrator.stop, иначе attachTun " +
                "может завершиться после единственного stop pass.",
        )
        assertTrue(
            coordinator.contains("state.startJobRef.getAndSet(null)") &&
                coordinator.contains("cancelAndJoin"),
            "shutdown path должен забирать startJobRef и ждать отмену старта до stop pass.",
        )
    }

    @Test
    fun `attachTun success during stopping rolls back custom tun engine`() {
        val coordinator = File(
            File(System.getProperty("user.dir") ?: "."),
            "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt",
        ).readText()
        val successBody = coordinator.substringAfter("TunAttachResult.Success ->")
            .substringBefore("is TunAttachResult.Failure")
        assertTrue(
            successBody.contains("state.stopping.get()") &&
                successBody.contains("deps.chainOrchestrator.stop()") &&
                successBody.contains("state.tunFdRef.getAndSet(null)?.close()") &&
                successBody.contains("false"),
            "attachTun Success обязан проверять stopping и откатывать custom TUN engine, " +
                "если shutdown начался во время attachTun.",
        )
    }

    @Test
    fun `logActiveExternalVpn пропускает собственный VPN через ownerUid guard — no self-false-positive`() {
        assertTrue(
            source.contains("isOwnVpnNetwork(caps, myUid)") && source.contains("caps.ownerUid"),
            "logActiveExternalVpn обязана пропускать собственный VPN через caps.ownerUid == myUid() " +
                "(API 29+). Без этого Ozero детектит свой же VPN как 'external' при engine-switch " +
                "→ лишние 750ms задержки при каждом перезапуске. Добавь: " +
                "if (Build.VERSION.SDK_INT >= 29 && caps.ownerUid == myUid) continue",
        )
    }

    @Test
    fun `pipeline_stop защищён timeout-ом в shutdown path`() {
        val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("\n}\n")
        assertFalse(
            onDestroyBody.contains("runBlocking { pipeline.stop()") ||
                Regex("runBlocking\\s*\\{\\s*pipeline\\.stop\\(\\)\\s*\\}").containsMatchIn(onDestroyBody),
            "Нельзя `runBlocking { pipeline.stop() }` без timeout — ANR на Main thread. " +
                "Используй withTimeoutOrNull или Thread+join+timeout.",
        )
    }
}
