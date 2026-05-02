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
    fun `start() обрабатывает все 4 SDK init throw paths`() {
        val startBlock = source
            .substringAfter("override suspend fun start")
            .substringBefore("override suspend fun stop")
        val throwPoints = listOf(
            "newNetworkSpaceManager threw",
            "NetworkSpace resolve failed",
            "newDeviceLocalWithDefaults threw",
            "setTunnelStarted threw",
        )
        throwPoints.forEach { msg ->
            assertTrue(
                startBlock.contains(msg),
                "start() должен ловить throw на этом этапе: '$msg'. Без catch — uncaught exception " +
                    "ломает coroutine + утечка native ресурсов.",
            )
        }
        assertTrue(
            startBlock.split("cleanupOnFailure()").size - 1 >= 3,
            "cleanupOnFailure обязан вызываться на каждом из 3 throw paths после init manager — " +
                "иначе утечка NetworkSpaceManager / DeviceLocal native handles.",
        )
    }

    @Test
    fun `start() возвращает StartResult Failed already running при повторном вызове`() {
        val startBlock = source
            .substringAfter("override suspend fun start")
            .substringBefore("override suspend fun stop")
        assertTrue(
            startBlock.contains("running.get()") && startBlock.contains("already running"),
            "start() обязан guard если running=true — иначе двойная init = double native crash.",
        )
    }

    @Test
    fun `attachTun валидирует fd и device state и ioLoop double-attach`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            attachBlock.contains("tunFd < 0") && attachBlock.contains("invalid fd"),
            "attachTun обязан проверять tunFd < 0 — Sdk.newIoLoop с invalid fd = native crash.",
        )
        assertTrue(
            attachBlock.contains("DeviceLocal not initialised"),
            "attachTun обязан проверять deviceRef.get() — без device.start NPE в native.",
        )
        assertTrue(
            attachBlock.contains("IoLoop already attached"),
            "attachTun обязан guard от double-attach — два IoLoop на один fd = race + leak.",
        )
        assertTrue(
            attachBlock.contains("catch (t: Throwable)") && attachBlock.contains("newIoLoop failed"),
            "attachTun обязан catch Throwable из Sdk.newIoLoop — иначе VPN crash на старте.",
        )
    }

    @Test
    fun `attachTun регистрирует IoLoopDoneCallback который сбрасывает running`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            attachBlock.contains("IoLoopDoneCallback") &&
                attachBlock.contains("running.set(false)"),
            "IoLoopDoneCallback обязан сбрасывать running=false — без этого после tunnel end " +
                "isRunning() врёт = UI показывает connected когда нет.",
        )
    }

    @Test
    fun `stop() очищает все 4 ref-ы через runCatching (idempotent)`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(
            stopBlock.contains("running.set(false)"),
            "stop() обязан сразу running=false — иначе race с attachTun.",
        )
        listOf("ioLoopRef", "deviceRef", "managerRef").forEach { ref ->
            assertTrue(
                stopBlock.contains("$ref.getAndSet(null)"),
                "stop() обязан getAndSet(null) на $ref — иначе stale reference + двойной close.",
            )
        }
        val runCatchingCount = stopBlock.split("runCatching").size - 1
        assertTrue(
            runCatchingCount >= 4,
            "Каждый close() обязан в runCatching — exception в одной cleanup не должен останавливать " +
                "остальные. Найдено runCatching=$runCatchingCount, ожидалось ≥4 (userwireguard удалён).",
        )
    }

    @Test
    fun `cleanupOnFailure закрывает device и manager без throw`() {
        val cleanupBlock = source.substringAfter("private fun cleanupOnFailure")
            .substringBefore("private companion object")
        assertTrue(
            cleanupBlock.contains("deviceRef.getAndSet(null)") &&
                cleanupBlock.contains("managerRef.getAndSet(null)"),
            "cleanupOnFailure обязан getAndSet null обоих refs — иначе second start() видит stale " +
                "ref и не начинает заново.",
        )
        assertTrue(
            cleanupBlock.contains("runCatching"),
            "cleanupOnFailure runCatching close — exception на cleanup в error path не должен " +
                "перекрывать первоначальную причину failure.",
        )
    }

    @Test
    fun `start() использует guest JWT empty placeholder если null`() {
        val startBlock = source
            .substringAfter("override suspend fun start")
            .substringBefore("override suspend fun stop")
        assertTrue(
            startBlock.contains("byJwt.orEmpty()"),
            "byJwt?.orEmpty() обязателен — Sdk.newDeviceLocalWithDefaults не принимает null. " +
                "До auto-acquire guest path передавался null = NPE.",
        )
    }
}
