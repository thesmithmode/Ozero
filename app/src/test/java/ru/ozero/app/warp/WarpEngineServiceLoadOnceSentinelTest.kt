package ru.ozero.app.warp

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarpEngineServiceLoadOnceSentinelTest {

    private val source by lazy {
        val f = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/warp/WarpEngineService.kt",
        )
        assertTrue(f.exists(), "WarpEngineService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `ensureLibraryLoaded не держит double-checked synchronized — System loadLibrary сам идемпотентен`() {
        val body = source.substringAfter("private fun ensureLibraryLoaded()")
            .substringBefore("private companion object")
        assertFalse(
            body.contains("synchronized("),
            "synchronized(this) в ensureLibraryLoaded дублирует JVM internal lock — System.loadLibrary " +
                "по контракту thread-safe + идемпотентен. Доп. лок = шум, риск ABA. Body:\n$body",
        )
        assertFalse(
            body.contains("libraryLoaded"),
            "libraryLoaded флаг лишний — eager-load в OzeroApp.onCreate (engine_warp guard) " +
                "гарантирует загрузку до первого вызова. System.loadLibrary повторно = no-op. Body:\n$body",
        )
    }

    @Test
    fun `ensureLibraryLoaded всё ещё дергает System loadLibrary как defensive fallback`() {
        val body = source.substringAfter("private fun ensureLibraryLoaded()")
            .substringBefore("private companion object")
        assertTrue(
            body.contains("System.loadLibrary(\"am-go\")"),
            "ensureLibraryLoaded удалить нельзя — defensive fallback на случай regression OzeroApp guard. " +
                "Native call безопасен. Body:\n$body",
        )
    }

    @Test
    fun `WarpEngineService foreground использует основной VPN notification id`() {
        assertTrue(
            source.contains("startForegroundSession()") &&
                source.contains("OzeroNotificationFactory(this).enterForeground(this)"),
            "WARP engine process обязан оставаться foreground в фоне, но через основной VPN notification id, " +
                "чтобы не показывать второе уведомление.",
        )
        assertFalse(
            source.contains("Ozero WARP") || source.contains("ozero_warp_engine") || source.contains("7302"),
            "WarpEngineService не должен иметь отдельный title/channel/id для второго WARP notification.",
        )
        assertTrue(
            source.contains("override fun onDestroy()") && source.contains("leaveForeground()"),
            "RemoteAwgRuntime.close вызывает stopService, поэтому detach обязан быть в onDestroy, " +
                "а не только в ACTION_STOP_SESSION ветке.",
        )
        assertTrue(
            source.contains("STOP_FOREGROUND_DETACH"),
            "Stop WARP foreground не должен удалять основной VPN notification с traffic stats.",
        )
        assertTrue(
            source.contains("val foreground =") && source.contains("if (!foreground) stopSelf()"),
            "Если startForeground rejected, service обязан self-stop, иначе Android O+ убьёт его по FGS timeout.",
        )
    }

    @Test
    fun `WARP service releases active runtime for explicit and process teardown`() {
        val stopSession = source.substringAfter("ACTION_STOP_SESSION ->").substringBefore("else ->")
        val onDestroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun startForegroundSession")

        assertTrue(stopSession.contains("shutdownCoordinator.request()"))
        assertTrue(onDestroy.contains("shutdownCoordinator.request()"))
        assertTrue(source.contains("cleanup = ::stopActiveRuntime"))
        assertTrue(source.contains("activeTunHandles.releaseAll()"))
        assertTrue(source.contains("synchronized(runtimeLock)"))
        assertTrue(source.contains(".onSuccess {") && source.contains("proxyStarted = false"))
    }

    @Test
    fun `service owns one runtime lease and cleans both native modes before replacement`() {
        val prepare = source.substringAfter("private fun prepareForNewRuntime()")
            .substringBefore("private fun releaseActiveTunnels")

        assertTrue(prepare.contains("releaseActiveTunnels()"))
        assertTrue(prepare.contains("stopActiveProxy()"))
        assertFalse(prepare.contains("releaseActiveTunnels() && stopActiveProxy()"))
        assertTrue(prepare.contains("awaitRuntimeRestartCooldown()"))
        assertTrue(source.split("prepareForNewRuntime()").size >= 4)
    }

    @Test
    fun `runtime cooldown survives explicit turnOff before replacement`() {
        val turnOff = source.substringAfter("private fun turnOffNative")
            .substringBefore("private fun ensureLibraryLoaded")
        val cooldown = source.substringAfter("private fun awaitRuntimeRestartCooldown()")
            .substringBefore("private fun terminateCurrentProcess")

        assertTrue(turnOff.contains("markRuntimeStopped()"))
        assertTrue(cooldown.contains("lastRuntimeStopElapsedMs"))
        assertTrue(cooldown.contains("RUNTIME_RESTART_COOLDOWN_MS"))
        assertTrue(cooldown.contains("SystemClock.sleep(remaining)"))
    }

    @Test
    fun `client loss schedules bounded cleanup and binder force kill stays process local`() {
        val onUnbind = source.substringAfter("override fun onUnbind")
            .substringBefore("override fun onDestroy")
        val terminate = source.substringAfter("private fun terminateCurrentProcess()")
            .substringBefore("private fun turnOffNative")

        assertTrue(onUnbind.contains("shutdownCoordinator.request()"))
        assertTrue(onUnbind.contains("stopSelf()"))
        assertFalse(onUnbind.contains("stopActiveRuntime()"))
        assertTrue(source.contains("override fun forceTerminate() = terminateCurrentProcess()"))
        assertTrue(terminate.contains("Process.killProcess(Process.myPid())"))
    }
}
