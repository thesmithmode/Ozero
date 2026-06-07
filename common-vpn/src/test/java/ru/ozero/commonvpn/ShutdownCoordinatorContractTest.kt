package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShutdownCoordinatorContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/ShutdownCoordinator.kt")
        assertTrue(f.exists(), "ShutdownCoordinator.kt не найден: $f")
        f.readText()
    }

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `companion exposes PARALLEL_STOP_TIMEOUT_MS`() {
        assertEquals(4_000L, ShutdownCoordinator.PARALLEL_STOP_TIMEOUT_MS)
    }

    @Test
    fun `public API — stopVpn + performShutdown`() {
        listOf("fun stopVpn(", "suspend fun performShutdown(").forEach { anchor ->
            assertTrue(source.contains(anchor), "API anchor потерян: '$anchor'")
        }
    }

    @Test
    fun `bundle classes — ShutdownState и ShutdownCollaborators`() {
        listOf("class ShutdownState(", "class ShutdownCollaborators(").forEach { anchor ->
            assertTrue(source.contains(anchor), "Bundle anchor потерян: '$anchor'")
        }
    }

    @Test
    fun `coordinator не зависит от OzeroVpnService — тестируемость`() {
        assertTrue(
            !source.contains("OzeroVpnService"),
            "ShutdownCoordinator не должен ссылаться на OzeroVpnService.",
        )
        val classDecl = source.substringAfter("class ShutdownCoordinator(").substringBefore("{").trim()
        assertTrue(
            classDecl.contains("stopForegroundRequest: () -> Unit") &&
                classDecl.contains("stopSelfRequest: (Int) -> Unit") &&
                classDecl.contains("latestStartIdProvider: () -> Int"),
            "Coordinator зависит от callbacks (stopForeground/stopSelf/latestStartId), не от service.",
        )
    }

    @Test
    fun `performShutdown stop order — chainOrchestrator ПЕРЕД tunnelGateway`() {
        val body = source.substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
        val chainIdx = body.indexOf("chainOrchestrator.stop()")
        val nativeIdx = body.indexOf("tunnelGateway.stop()")
        assertTrue(
            chainIdx in 0 until nativeIdx,
            "chainOrchestrator.stop() обязан быть ПЕРЕД tunnelGateway.stop() — иначе libhev " +
                "ждёт outstanding connections к byedpi → reconnect deadlock.",
        )
    }

    @Test
    fun `performShutdown закрывает TUN fd ПОСЛЕ tunnelGateway stop`() {
        val body = source.substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
        val nativeIdx = body.indexOf("tunnelGateway.stop()")
        val closeIdx = body.indexOf("state.tunFdRef.getAndSet(null)?.close()")
        assertTrue(
            nativeIdx in 0 until closeIdx,
            "tunFd закрывается ПОСЛЕ native stop — libhev завершается на ECONNREFUSED.",
        )
    }

    @Test
    fun `performShutdown finally сбрасывает tunIfaceNameRef`() {
        val body = source.substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
        val finallyBlock = body.substringAfter("} finally {").substringBefore("companion object")
        assertTrue(
            finallyBlock.contains("state.tunIfaceNameRef.set(null)"),
            "performShutdown finally обязан сбрасывать tunIfaceNameRef — stale имя ломает новую сессию.",
        )
    }

    @Test
    fun `stopVpn вызывает recordSessionEnd с правильным статусом`() {
        val body = source.substringAfter("fun stopVpn(").substringBefore("suspend fun performShutdown(")
        assertTrue(body.contains("SessionStatsRecorder.Status.FAILED"), "FAILED для priorState=Failed.")
        assertTrue(body.contains("SessionStatsRecorder.Status.DISCONNECTED"), "DISCONNECTED иначе.")
        assertTrue(body.contains("recordSessionEnd(endStatus)"), "recordSessionEnd вызов обязателен.")
    }

    @Test
    fun `stopVpn — идемпотентен через stopping compareAndSet`() {
        val body = source.substringAfter("fun stopVpn(").substringBefore("suspend fun performShutdown(")
        assertTrue(
            body.contains("state.stopping.compareAndSet(false, true)"),
            "stopVpn обязан guard через compareAndSet — идемпотентность.",
        )
    }

    @Test
    fun `OzeroVpnService строит ShutdownCoordinator через by lazy`() {
        assertTrue(
            serviceSource.contains("shutdownCoord: ShutdownCoordinator by lazy {") &&
                serviceSource.contains("ShutdownCoordinator("),
            "OzeroVpnService обязан инжектить shutdownCoord через by lazy.",
        )
    }

    @Test
    fun `OzeroVpnService stopVpn delegates to ShutdownCoordinator`() {
        assertTrue(
            serviceSource.contains("private fun stopVpn() {") && serviceSource.contains("shutdownCoord.stopVpn()"),
            "stopVpn в сервисе должен делегировать в shutdownCoord без inline body.",
        )
        assertTrue(
            !serviceSource.contains("private fun recordSessionEnd"),
            "recordSessionEnd обязан быть удалён — извлечён в ShutdownCoordinator.",
        )
        assertTrue(
            !serviceSource.contains("private suspend fun performShutdown"),
            "performShutdown обязан быть удалён — извлечён в ShutdownCoordinator.",
        )
        assertTrue(
            !serviceSource.contains("PARALLEL_STOP_TIMEOUT_MS"),
            "PARALLEL_STOP_TIMEOUT_MS обязан жить только в ShutdownCoordinator.",
        )
    }

    @Test
    fun `OzeroVpnService onDestroy зовёт shutdownCoord performShutdown с callStopSelf=false`() {
        val body = serviceSource.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
        assertTrue(
            body.contains("shutdownCoord.performShutdown(callStopSelf = false)"),
            "onDestroy обязан звать shutdownCoord.performShutdown(callStopSelf=false). Body:\n$body",
        )
    }

    @Test
    fun `OzeroVpnService больше не имеет @Suppress TooManyFunctions LargeClass`() {
        assertTrue(
            !serviceSource.contains("@Suppress(\"TooManyFunctions\", \"LargeClass\")"),
            "После декомпоза #72 OzeroVpnService обязан НЕ иметь TooManyFunctions/LargeClass suppress — " +
                "класс уменьшен до lifecycle/delegation сервиса.",
        )
    }
}
