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
    fun `start() обрабатывает 3 SDK init throw paths`() {
        val throwPoints = listOf(
            "newNetworkSpaceManager threw",
            "NetworkSpace resolve failed",
            "newDeviceLocalWithDefaults threw",
        )
        throwPoints.forEach { msg ->
            assertTrue(source.contains(msg), "должен быть catch: '$msg'")
        }
        assertTrue(
            source.split("cleanupOnFailure()").size - 1 >= 3,
            "cleanupOnFailure обязан вызываться на каждом из 3 throw paths",
        )
    }

    @Test
    fun `start() guard already running`() {
        assertTrue(source.contains("running.get()") && source.contains("already running"))
    }

    @Test
    fun `start() требует non-blank byClientJwt`() {
        assertTrue(
            source.contains("byClientJwt.isBlank()") && source.contains("byClientJwt is blank"),
            "byClientJwt blank → Failed без вызова newDeviceLocalWithDefaults — " +
                "иначе SDK создаёт unauthenticated device, hits Go-GC SIGABRT path",
        )
    }

    @Test
    fun `start() и attachTun выполняются на main thread`() {
        assertTrue(
            source.contains("Dispatchers.Main.immediate"),
            "SDK calls (newDeviceLocalWithDefaults, newIoLoop) обязаны на main thread — " +
                "Go runtime + non-locked OSThread = SIGABRT на Nubia/RedMagic",
        )
    }

    @Test
    fun `attachTun валидирует fd и device state и double-attach`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun resolveNetworkSpace")
        assertTrue(attachBlock.contains("tunFd < 0") && attachBlock.contains("invalid fd"))
        assertTrue(attachBlock.contains("DeviceLocal not initialised"))
        assertTrue(attachBlock.contains("IoLoop already attached"))
        assertTrue(attachBlock.contains("catch (t: Throwable)") && attachBlock.contains("newIoLoop failed"))
    }

    @Test
    fun `attachTun вызывает setTunnelStarted true ПОСЛЕ newIoLoop`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun resolveNetworkSpace")
        val ioLoopIdx = attachBlock.indexOf("Sdk.newIoLoop")
        val tunnelStartedIdx = attachBlock.indexOf("setTunnelStarted(true)")
        assertTrue(ioLoopIdx >= 0, "newIoLoop должен быть в attachTun")
        assertTrue(tunnelStartedIdx >= 0, "setTunnelStarted(true) должен быть в attachTun")
        assertTrue(
            tunnelStartedIdx > ioLoopIdx,
            "setTunnelStarted(true) обязан вызываться ПОСЛЕ newIoLoop — иначе SDK forwarders " +
                "стартуют без транспорта, hits nil packet flow path",
        )
    }

    @Test
    fun `start() НЕ вызывает setTunnelStarted true (только attachTun)`() {
        val startBlock = source
            .substringAfter("private fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            !startBlock.contains("setTunnelStarted(true)"),
            "start() НЕ должен ставить tunnelStarted=true — это работа attachTun после newIoLoop",
        )
    }

    @Test
    fun `attachTun регистрирует IoLoopDoneCallback который сбрасывает running`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun resolveNetworkSpace")
        assertTrue(
            attachBlock.contains("IoLoopDoneCallback") &&
                attachBlock.contains("running.set(false)"),
        )
    }

    @Test
    fun `stop() очищает 3 ref-а через runCatching idempotent`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(stopBlock.contains("running.set(false)"))
        listOf("ioLoopRef", "deviceRef", "managerRef").forEach { ref ->
            assertTrue(stopBlock.contains("$ref.getAndSet(null)"), "stop() getAndSet(null) на $ref")
        }
        val runCatchingCount = stopBlock.split("runCatching").size - 1
        assertTrue(runCatchingCount >= 4, "Каждый close()/setTunnelStarted в runCatching, found=$runCatchingCount")
    }

    @Test
    fun `cleanupOnFailure закрывает device и manager без throw`() {
        val cleanupBlock = source.substringAfter("private fun cleanupOnFailure")
            .substringBefore("private companion object")
        assertTrue(
            cleanupBlock.contains("deviceRef.getAndSet(null)") &&
                cleanupBlock.contains("managerRef.getAndSet(null)"),
        )
        assertTrue(cleanupBlock.contains("runCatching"))
    }

    @Test
    fun `resolveNetworkSpace fallback active stored imported`() {
        val helperBlock = source.substringAfter("private fun resolveNetworkSpace")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            helperBlock.contains("activeNetworkSpace?.let") &&
                helperBlock.contains("getNetworkSpace(key)?.let") &&
                helperBlock.contains("importNetworkSpaceFromJson"),
        )
        listOf("using active", "using stored", "imported default").forEach { phrase ->
            assertTrue(helperBlock.contains(phrase))
        }
    }

    @Test
    fun `storage dir — filesDir root (parity с официальным URnetwork)`() {
        assertTrue(
            source.contains("context.filesDir.absolutePath"),
            "Официальное URnetwork приложение использует filesDir.absolutePath (root), не subdir — " +
                "обеспечивает консистентность NetworkSpace storage между сессиями",
        )
    }
}
