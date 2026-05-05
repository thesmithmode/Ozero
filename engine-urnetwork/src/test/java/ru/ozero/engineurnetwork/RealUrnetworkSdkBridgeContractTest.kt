package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class RealUrnetworkSdkBridgeContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `start() guard already running`() {
        assertTrue(source.contains("running.get()") && source.contains("already running"))
    }

    @Test
    fun `start() требует non-blank byClientJwt`() {
        assertTrue(source.contains("byClientJwt.isBlank()") && source.contains("byClientJwt is blank"))
    }

    @Test
    fun `start() и attachTun выполняются на Main thread`() {
        assertTrue(
            source.contains("Dispatchers.Main.immediate"),
            "Go runtime + non-locked OSThread = SIGABRT на Nubia/RedMagic",
        )
    }

    @Test
    fun `bridge использует UrnetworkRuntime ensure (один manager на процесс)`() {
        assertTrue(
            source.contains("UrnetworkRuntime.ensure(app)"),
            "Bridge обязан использовать singleton Runtime — два NetworkSpaceManager = SIGABRT",
        )
        assertTrue(
            !source.contains("Sdk.newNetworkSpaceManager"),
            "Bridge НЕ должен создавать свой manager",
        )
    }

    @Test
    fun `Application context not Activity`() {
        assertTrue(source.contains("private val app: Application"))
    }

    @Test
    fun `attachTun валидирует fd и device state и double-attach`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(attachBlock.contains("tunFd < 0") && attachBlock.contains("invalid fd"))
        assertTrue(attachBlock.contains("DeviceLocal not initialised"))
        assertTrue(attachBlock.contains("IoLoop already attached"))
        assertTrue(attachBlock.contains("catch (t: Throwable)") && attachBlock.contains("newIoLoop failed"))
    }

    @Test
    fun `attachTun setTunnelStarted true ПОСЛЕ newIoLoop`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        val ioLoopIdx = attachBlock.indexOf("Sdk.newIoLoop")
        val tunnelStartedIdx = attachBlock.indexOf("setTunnelStarted(true)")
        assertTrue(ioLoopIdx >= 0 && tunnelStartedIdx >= 0)
        assertTrue(tunnelStartedIdx > ioLoopIdx)
    }

    @Test
    fun `start() не вызывает setTunnelStarted true`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(!startBlock.contains("setTunnelStarted(true)"))
    }

    @Test
    fun `attachTun регистрирует IoLoopDoneCallback который сбрасывает running`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(attachBlock.contains("IoLoopDoneCallback"))
        assertTrue(attachBlock.contains("running.set(false)"))
    }

    @Test
    fun `stop() очищает ioLoop и device через runCatching idempotent`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(stopBlock.contains("running.set(false)"))
        assertTrue(stopBlock.contains("ioLoopRef.getAndSet(null)"))
        assertTrue(stopBlock.contains("deviceRef.getAndSet(null)"))
        val runCatchingCount = stopBlock.split("runCatching").size - 1
        assertTrue(runCatchingCount >= 3, "Каждый close()/setTunnelStarted в runCatching, found=$runCatchingCount")
    }

    @Test
    fun `cleanupOnFailure закрывает device без throw`() {
        val cleanupBlock = source.substringAfter("private fun cleanupOnFailure")
            .substringBefore("private companion object")
        assertTrue(cleanupBlock.contains("deviceRef.getAndSet(null)"))
        assertTrue(cleanupBlock.contains("runCatching"))
    }
}
