package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkRuntimeReleaseSentinelTest {

    private val runtimeSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/UrnetworkRuntime.kt")
        assertTrue(f.exists(), "UrnetworkRuntime.kt не найден: $f")
        f.readText()
    }

    private val bridgeSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `UrnetworkRuntime содержит suspend fun release — освобождает Go-runtime ресурсы`() {
        assertTrue(
            runtimeSource.contains("suspend fun release()"),
            "UrnetworkRuntime обязан иметь suspend fun release() — без неё Go-runtime singleton " +
                "продолжает держать UDP listeners и file handles после выключения движка, " +
                "блокируя URnetwork оригинальное приложение (симметричный конфликт).",
        )
    }

    @Test
    fun `release обнуляет manager и space — иначе следующий ensure возвращает stale ссылку`() {
        val body = runtimeSource.substringAfter("suspend fun release()").substringBefore("}\n}")
        assertTrue(
            body.contains("manager = null"),
            "release обязан обнулить manager — без этого следующий ensure() возвращает stale NetworkSpaceManager, " +
                "конфликт с URnetwork-app не разрешается.",
        )
        assertTrue(
            body.contains("space = null"),
            "release обязан обнулить space — без этого ensure() возвращает stale NetworkSpace.",
        )
    }

    @Test
    fun `release вызывает Sdk_freeMemory — освобождает Go heap`() {
        val body = runtimeSource.substringAfter("suspend fun release()").substringBefore("}\n}")
        assertTrue(
            body.contains("Sdk.freeMemory()"),
            "release обязан вызывать Sdk.freeMemory() — это SDK-mechanism для освобождения Go heap. " +
                "Оригинальный URnetwork app использует эту же функцию в onLowMemory.",
        )
    }

    @Test
    fun `release вызывает setActiveNetworkSpace null — иначе SDK держит активные сессии`() {
        val body = runtimeSource.substringAfter("suspend fun release()").substringBefore("}\n}")
        assertTrue(
            body.contains("setActiveNetworkSpace(null)"),
            "release обязан вызывать manager.setActiveNetworkSpace(null) — без этого NetworkSpace остаётся " +
                "активным в Go-side, держит P2P relay UDP sockets.",
        )
    }

    @Test
    fun `release обёрнут в Dispatchers_Main_immediate — Go SDK требует main thread на teardown`() {
        val body = runtimeSource.substringAfter("suspend fun release()").substringBefore("}\n}")
        assertTrue(
            body.contains("Dispatchers.Main.immediate") || body.contains("Dispatchers.Main"),
            "release обязан выполняться на main thread через withContext(Dispatchers.Main.immediate). " +
                "Go SDK init/teardown на worker thread → SIGSEGV (см. invariant в CLAUDE.md про loadOnce).",
        )
    }

    @Test
    fun `release защищён mutex — race между ensure и release без лока приведёт к null manager в active session`() {
        val body = runtimeSource.substringAfter("suspend fun release()").substringBefore("}\n}")
        assertTrue(
            body.contains("mutex.withLock"),
            "release обязан использовать mutex.withLock — иначе concurrent ensure() может видеть " +
                "пол-разваленный state.",
        )
    }

    @Test
    fun `bridge stop вызывает UrnetworkRuntime release — иначе симметричный конфликт`() {
        val stopBlock = bridgeSource.substringAfter("private suspend fun stopUnderLock()")
            .substringBefore("private fun closeDevice")
        assertTrue(
            stopBlock.contains("UrnetworkRuntime.release()"),
            "stopUnderLock обязан в конце вызывать UrnetworkRuntime.release() — иначе при следующем " +
                "запуске URnetwork-app оригинального приложения оно не сможет bind UDP/получить " +
                "ownership Go-runtime ресурсов. Симметричный баг: оба VPN-приложения борются за " +
                "один и тот же singleton SDK state.",
        )
    }

    @Test
    fun `bridge stop откладывает release до IoLoopDoneCallback при активном IoLoop`() {
        val stopBlock = bridgeSource.substringAfter("private suspend fun stopUnderLock()")
            .substringBefore("private fun closeDevice")
        val callbackBlock = bridgeSource.substringAfter("IoLoopDoneCallback {")
            .substringBefore("val loop = Sdk.newIoLoop")
        assertTrue(
            stopBlock.contains("completed == true") &&
                stopBlock.contains("runtime release deferred until IoLoop done"),
            "stopUnderLock обязан откладывать UrnetworkRuntime.release(), если loop.close() только запросил " +
                "асинхронное завершение IoLoop.",
        )
        assertTrue(
            callbackBlock.contains("releaseRuntimeAfterIoLoopDone()"),
            "IoLoopDoneCallback обязан запускать release только после closeDevice(capturedDevice), когда Go IoLoop " +
                "уже подтвердил завершение.",
        )
        val closeDeviceIndex = callbackBlock.indexOf("closeDevice(capturedDevice)")
        val releaseIndex = callbackBlock.indexOf("releaseRuntimeAfterIoLoopDone()")
        assertTrue(
            closeDeviceIndex < releaseIndex,
            "runtime нельзя освобождать до закрытия DeviceLocal в IoLoopDoneCallback.",
        )
    }

    @Test
    fun `release warns если manager null но space non-null — anomaly не молчит`() {
        val body = runtimeSource.substringAfter("suspend fun release()").substringBefore("}\n}")
        assertTrue(
            body.contains("invariant violated"),
            "release обязан логировать warn если detected mgr==null && space!=null. " +
                "Без warn anomaly молчит и стало невозможно диагностировать корень.",
        )
    }

    @Test
    fun `bridge stop отличает timeout release от success — silent swallow запрещён`() {
        val stopBlock = bridgeSource.substringAfter("private suspend fun stopUnderLock()")
            .substringBefore("private fun closeDevice")
        assertTrue(
            stopBlock.contains("runtime release НЕ подтверждён") ||
                stopBlock.contains("runtime release timed out"),
            "stopUnderLock обязан явно различать success/timeout/throw release и логировать warn. " +
                "Silent withTimeoutOrNull без обработки null = «stop complete» врёт когда Sdk.freeMemory завис.",
        )
    }

    @Test
    fun `bridge stop release runtime обёрнут в timeout — не блокирует stop пайплайн при зависании Sdk`() {
        val stopBlock = bridgeSource.substringAfter("private suspend fun stopUnderLock()")
            .substringBefore("private fun closeDevice")
        val releaseIdx = stopBlock.indexOf("UrnetworkRuntime.release()")
        assertTrue(releaseIdx >= 0, "UrnetworkRuntime.release() не найден в stopUnderLock")
        val context = stopBlock.substring(maxOf(0, releaseIdx - 200), releaseIdx)
        assertTrue(
            context.contains("withTimeoutOrNull") || context.contains("withTimeout"),
            "release вызов обязан быть обёрнут в withTimeoutOrNull — иначе зависший Sdk.freeMemory " +
                "блокирует stop навсегда. Found context: $context",
        )
    }
}
